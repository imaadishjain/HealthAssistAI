package com.example.HealthAssistBackend.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Healthcare RAG service — retrieval-augmented generation with domain-specific filters,
 * query transformation, and post-retrieval processing.
 */
@Service
public class HealthcareRagService {

    private static final Logger log = LoggerFactory.getLogger(HealthcareRagService.class);

    private final VectorStore vectorStore;
    private final QueryTransformationService queryTransformer;
    private final PostRetrievalProcessor postProcessor;

    @Value("${healthassist.rag.similarity-threshold:0.7}")
    private double similarityThreshold;

    @Value("${healthassist.rag.top-k:5}")
    private int topK;

    @Value("${healthassist.rag.allow-empty-context:false}")
    private boolean allowEmptyContext;

    public HealthcareRagService(VectorStore vectorStore,
                                 QueryTransformationService queryTransformer,
                                 PostRetrievalProcessor postProcessor) {
        this.vectorStore = vectorStore;
        this.queryTransformer = queryTransformer;
        this.postProcessor = postProcessor;
    }

    /**
     * Retrieve relevant documents for a healthcare query with full pipeline:
     * transform → search → filter → post-process.
     */
    public RagResult retrieve(String query, Map<String, String> filters) {
        log.info("RAG retrieval for query: {}", query.substring(0, Math.min(query.length(), 100)));

        // Step 1: Transform the query for better retrieval
        String transformedQuery = queryTransformer.transformQuery(query);
        log.debug("Transformed query: {}", transformedQuery);

        // Step 2: Build search request with filters
        SearchRequest searchRequest = SearchRequest.builder()
                .query(transformedQuery)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();

        // Step 3: Search vector store (wrap in mutable ArrayList for later expansion)
        List<Document> results = new java.util.ArrayList<>(vectorStore.similaritySearch(searchRequest));
        log.debug("Vector search returned {} results", results.size());

        // Step 4: Apply dynamic metadata filters (post-retrieval filtering)
        if (filters != null && !filters.isEmpty()) {
            results = new ArrayList<>(applyMetadataFilters(results, filters));
            log.debug("After metadata filtering: {} results", results.size());
        }

        // Step 5: Multi-query expansion for broader coverage
        if (results.size() < 2) {
            List<String> expandedQueries = queryTransformer.expandQuery(query);
            for (String expanded : expandedQueries) {
                if (expanded.equals(query)) continue;
                SearchRequest expandedRequest = SearchRequest.builder()
                        .query(expanded)
                        .topK(3)
                        .similarityThreshold(similarityThreshold)
                        .build();
                List<Document> additionalResults = vectorStore.similaritySearch(expandedRequest);
                results.addAll(additionalResults);
            }
            log.debug("After expansion: {} total results", results.size());
        }

        // Step 6: Post-retrieval processing (deduplicate, rerank, compress)
        List<Document> processed = postProcessor.process(results, query);

        // Step 7: Extract citations
        List<String> citations = postProcessor.extractCitations(processed);

        // Step 8: Build context string
        String context = buildContext(processed);

        // Check if we have valid context
        boolean hasContext = !processed.isEmpty() && !context.isBlank();
        if (!hasContext && !allowEmptyContext) {
            log.warn("No relevant context found for query and allowEmptyContext=false");
        }

        return new RagResult(context, citations, hasContext, processed.size(), transformedQuery);
    }

    /**
     * Simple retrieval without transformation (for direct KB queries).
     */
    public RagResult simpleRetrieve(String query) {
        return retrieve(query, null);
    }

    /**
     * Apply dynamic metadata filters on retrieved documents.
     */
    private List<Document> applyMetadataFilters(List<Document> documents, Map<String, String> filters) {
        return documents.stream()
            .filter(doc -> {
                Map<String, Object> metadata = doc.getMetadata();
                for (Map.Entry<String, String> filter : filters.entrySet()) {
                    Object value = metadata.get(filter.getKey());
                    if (value != null && !value.toString().equalsIgnoreCase(filter.getValue())) {
                        return false;
                    }
                }
                return true;
            })
            .toList();
    }

    /**
     * Build a context string from retrieved documents for prompt augmentation.
     */
    private String buildContext(List<Document> documents) {
        if (documents.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("=== HOSPITAL KNOWLEDGE BASE CONTEXT ===\n\n");
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            String source = doc.getMetadata().getOrDefault("source", "Unknown").toString();
            sb.append(String.format("[Source %d: %s]\n%s\n\n", i + 1, source, doc.getText()));
        }
        sb.append("=== END OF CONTEXT ===");
        return sb.toString();
    }

    /**
     * Result of RAG retrieval.
     */
    public record RagResult(
        String context,
        List<String> citations,
        boolean hasContext,
        int documentCount,
        String transformedQuery
    ) {}
}
