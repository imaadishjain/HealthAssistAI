package com.example.HealthAssistBackend.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Query transformation service for healthcare queries.
 * Supports rewrite, compression, translation, and multi-query expansion.
 */
@Service
public class QueryTransformationService {

    private static final Logger log = LoggerFactory.getLogger(QueryTransformationService.class);

    private final ChatClient chatClient;

    public QueryTransformationService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Rewrite vague medical queries into specific clinical terms for better retrieval.
     */
    public String rewriteQuery(String query) {
        log.debug("Rewriting query: {}", query);
        try {
            String rewritten = chatClient.prompt()
                .system("You are a medical query rewriter. Rewrite the following patient query into precise medical terminology suitable for searching a hospital knowledge base. Keep it concise (1-2 sentences). Only output the rewritten query, nothing else.")
                .user(query)
                .call()
                .content();
            log.debug("Rewritten query: {}", rewritten);
            return rewritten != null ? rewritten : query;
        } catch (Exception e) {
            log.warn("Query rewrite failed, using original: {}", e.getMessage());
            return query;
        }
    }

    /**
     * Compress verbose patient descriptions into concise retrieval queries.
     */
    public String compressQuery(String query) {
        if (query.length() < 100) return query;

        log.debug("Compressing query: {}", query.substring(0, Math.min(query.length(), 50)));
        try {
            String compressed = chatClient.prompt()
                .system("Extract the key medical symptoms, conditions, and questions from the following patient description. Output only the essential query terms in a single sentence.")
                .user(query)
                .call()
                .content();
            return compressed != null ? compressed : query;
        } catch (Exception e) {
            log.warn("Query compression failed, using original: {}", e.getMessage());
            return query;
        }
    }

    /**
     * Expand a symptom query into multiple sub-queries for better retrieval coverage.
     */
    public List<String> expandQuery(String query) {
        log.debug("Expanding query: {}", query);
        List<String> queries = new ArrayList<>();
        queries.add(query); // Always include original

        try {
            String expanded = chatClient.prompt()
                .system("Generate 3 alternative medical search queries for the following patient question. Each should use different medical terminology or synonyms. Output one query per line, no numbering or bullets.")
                .user(query)
                .call()
                .content();

            if (expanded != null) {
                String[] lines = expanded.split("\\n");
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.equals(query)) {
                        queries.add(trimmed);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Query expansion failed: {}", e.getMessage());
        }

        log.debug("Expanded to {} queries", queries.size());
        return queries;
    }

    /**
     * Translate non-English patient queries to English.
     */
    public String translateToEnglish(String query) {
        log.debug("Attempting translation for query: {}", query.substring(0, Math.min(query.length(), 50)));
        try {
            String translated = chatClient.prompt()
                .system("If the following text is not in English, translate it to English. If it is already in English, return it unchanged. Only output the translation, nothing else.")
                .user(query)
                .call()
                .content();
            return translated != null ? translated : query;
        } catch (Exception e) {
            log.warn("Translation failed, using original: {}", e.getMessage());
            return query;
        }
    }

    /**
     * Full transformation pipeline: translate → compress → rewrite.
     */
    public String transformQuery(String query) {
        String result = translateToEnglish(query);
        result = compressQuery(result);
        result = rewriteQuery(result);
        return result;
    }
}
