package com.example.HealthAssistBackend.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Post-retrieval document processing: compression, reranking, deduplication.
 */
@Service
public class PostRetrievalProcessor {

    private static final Logger log = LoggerFactory.getLogger(PostRetrievalProcessor.class);

    /**
     * Process retrieved documents: deduplicate, rerank, and compress.
     */
    public List<Document> process(List<Document> documents, String query) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        log.debug("Processing {} retrieved documents", documents.size());

        // Step 1: Deduplicate based on content similarity
        List<Document> deduped = deduplicate(documents);
        log.debug("After deduplication: {} documents", deduped.size());

        // Step 2: Rerank by relevance to the query
        List<Document> reranked = rerank(deduped, query);

        // Step 3: Compress lengthy documents
        List<Document> compressed = compress(reranked);

        log.debug("Final processed documents: {}", compressed.size());
        return compressed;
    }

    /**
     * Remove duplicate or near-duplicate documents based on content hash.
     */
    private List<Document> deduplicate(List<Document> documents) {
        Set<String> seen = new HashSet<>();
        List<Document> unique = new ArrayList<>();

        for (Document doc : documents) {
            // Create a simple hash based on first 200 chars of content
            String key = doc.getText() != null ?
                doc.getText().substring(0, Math.min(doc.getText().length(), 200)).trim().toLowerCase() : "";

            if (seen.add(key)) {
                unique.add(doc);
            }
        }

        return unique;
    }

    /**
     * Rerank documents based on keyword relevance to the query.
     * Uses simple keyword overlap scoring — in production, use a cross-encoder model.
     */
    private List<Document> rerank(List<Document> documents, String query) {
        if (query == null || query.isBlank()) return documents;

        Set<String> queryTerms = Arrays.stream(query.toLowerCase().split("\\s+"))
                .filter(t -> t.length() > 2)
                .collect(Collectors.toSet());

        return documents.stream()
                .sorted((a, b) -> {
                    int scoreA = calculateRelevanceScore(a.getText(), queryTerms);
                    int scoreB = calculateRelevanceScore(b.getText(), queryTerms);
                    return Integer.compare(scoreB, scoreA); // Descending
                })
                .toList();
    }

    /**
     * Calculate relevance score based on keyword overlap.
     */
    private int calculateRelevanceScore(String content, Set<String> queryTerms) {
        if (content == null) return 0;
        String lower = content.toLowerCase();
        int score = 0;
        for (String term : queryTerms) {
            if (lower.contains(term)) score++;
        }
        return score;
    }

    /**
     * Compress documents by truncating to a maximum length.
     */
    private List<Document> compress(List<Document> documents) {
        int maxContentLength = 2000;
        return documents.stream()
                .map(doc -> {
                    if (doc.getText() != null && doc.getText().length() > maxContentLength) {
                        String compressed = doc.getText().substring(0, maxContentLength) + "...";
                        return new Document(compressed, doc.getMetadata());
                    }
                    return doc;
                })
                .toList();
    }

    /**
     * Extract citations from processed documents.
     */
    public List<String> extractCitations(List<Document> documents) {
        return documents.stream()
                .map(doc -> {
                    Map<String, Object> metadata = doc.getMetadata();
                    String source = metadata.getOrDefault("source", "Hospital Knowledge Base").toString();
                    String section = metadata.getOrDefault("section", "").toString();
                    return section.isEmpty() ? source : source + " — " + section;
                })
                .distinct()
                .toList();
    }
}
