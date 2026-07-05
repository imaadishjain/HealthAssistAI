package com.example.HealthAssistBackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for ingesting healthcare knowledge base documents into the vector store.
 * Reads Markdown SOPs and text KB files, creates embeddings, and stores them.
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final VectorStore vectorStore;
    private final ResourceLoader resourceLoader;

    @Value("${healthassist.vector-store.path:./data/vector-store.json}")
    private String vectorStorePath;

    public DocumentIngestionService(VectorStore vectorStore, ResourceLoader resourceLoader) {
        this.vectorStore = vectorStore;
        this.resourceLoader = resourceLoader;
    }

    /**
     * Ingest all healthcare documents.
     * Call manually via the /documents/ingest/all API endpoint.
     */
    public void ingestDocumentsOnStartup() {
        File vectorStoreFile = new File(vectorStorePath);
        if (vectorStoreFile.exists() && vectorStoreFile.length() > 100) {
            log.info("Vector store already exists at {}. Skipping ingestion.", vectorStorePath);
            return;
        }

        log.info("Starting healthcare document ingestion...");
        long start = System.currentTimeMillis();

        try {
            List<Document> allDocuments = new ArrayList<>();

            // Ingest Markdown knowledge base documents
            allDocuments.addAll(ingestMarkdownDocuments());

            if (!allDocuments.isEmpty()) {
                // Split documents into smaller chunks
                TokenTextSplitter splitter = new TokenTextSplitter(500, 100, 5, 10000, true);
                List<Document> chunks = splitter.apply(allDocuments);

                log.info("Adding {} document chunks to vector store", chunks.size());
                vectorStore.add(chunks);

                // Save vector store to disk
                if (vectorStore instanceof SimpleVectorStore simpleStore) {
                    vectorStoreFile.getParentFile().mkdirs();
                    simpleStore.save(vectorStoreFile);
                    log.info("Vector store saved to {}", vectorStorePath);
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            log.info("Document ingestion complete. {} documents processed in {}ms", allDocuments.size(), elapsed);

        } catch (Exception e) {
            log.error("Error during document ingestion: {}", e.getMessage(), e);
        }
    }

    /**
     * Ingest Markdown SOP/KB documents from the resources directory.
     */
    private List<Document> ingestMarkdownDocuments() {
        List<Document> documents = new ArrayList<>();

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:*.md");

            for (Resource resource : resources) {
                try {
                    String filename = resource.getFilename();
                    log.info("Ingesting Markdown: {}", filename);
                    String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

                    // Split by top-level and second-level markdown headers (multiline mode)
                    String[] sections = content.split("(?m)(?=^#{1,2}\\s)", -1);
                    Map<String, Object> baseMeta = extractMetadataFromFilename(filename);

                    for (String section : sections) {
                        if (section.trim().isEmpty()) continue;

                        Map<String, Object> metadata = new HashMap<>(baseMeta);

                        // Extract section title if present
                        String firstLine = section.lines().findFirst().orElse("");
                        if (firstLine.matches("^#{1,2}\\s.*")) {
                            metadata.put("section", firstLine.replaceAll("^#{1,2}\\s*", "").trim());
                        }

                        documents.add(new Document(section.trim(), metadata));
                    }

                    log.info("Ingested {} sections from {}", sections.length, filename);
                } catch (Exception e) {
                    log.warn("Failed to ingest Markdown {}: {}", resource.getFilename(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Error scanning for Markdown resources: {}", e.getMessage());
        }

        return documents;
    }

    /**
     * Extract metadata from document filename (e.g., Triage_Guidelines_and_Emergency_Routing.md).
     */
    private Map<String, Object> extractMetadataFromFilename(String filename) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", filename);
        metadata.put("type", "SOP");

        if (filename == null) return metadata;

        String lower = filename.toLowerCase();
        if (lower.contains("triage") || lower.contains("emergency_routing")) {
            metadata.put("category", "triage");
            metadata.put("department", "emergency");
        } else if (lower.contains("appointment") || lower.contains("referral")) {
            metadata.put("category", "appointment");
        } else if (lower.contains("insurance") || lower.contains("eligibility")) {
            metadata.put("category", "insurance");
        } else if (lower.contains("facility") || lower.contains("equipment")) {
            metadata.put("category", "facility");
        } else if (lower.contains("navigation") || lower.contains("visiting")) {
            metadata.put("category", "navigation");
        } else if (lower.contains("department") || lower.contains("directory")) {
            metadata.put("category", "department");
            metadata.put("type", "knowledge-base");
        } else if (lower.contains("privacy") || lower.contains("phi") || lower.contains("data_handling")) {
            metadata.put("category", "privacy");
            metadata.put("type", "policy");
        } else if (lower.contains("rag") || lower.contains("query_transformation")) {
            metadata.put("category", "rag-policy");
            metadata.put("type", "policy");
        } else if (lower.contains("rai") || lower.contains("safety") || lower.contains("guardrail") || lower.contains("moderation")) {
            metadata.put("category", "rai-policy");
            metadata.put("type", "policy");
        } else if (lower.contains("tool_schema") || lower.contains("agent_workflow") || lower.contains("api_contract")) {
            metadata.put("category", "tool-schema");
            metadata.put("type", "policy");
        } else if (lower.contains("discharge")) {
            metadata.put("category", "discharge");
        } else if (lower.contains("emergency") || lower.contains("escalation")) {
            metadata.put("category", "emergency");
            metadata.put("department", "emergency");
        }

        return metadata;
    }

    // ───────────────────────────────────────────────────────────
    //  Public methods exposed via DocumentController / Swagger
    // ───────────────────────────────────────────────────────────

    /**
     * Ingest one or more uploaded Markdown/text files into the vector store.
     */
    public Map<String, Object> ingestUploadedFiles(List<MultipartFile> files) {
        long start = System.currentTimeMillis();
        List<String> ingested = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        int totalChunks = 0;

        for (MultipartFile file : files) {
            String originalName = file.getOriginalFilename();
            try {
                // Treat all uploads as plain text (Markdown / text)
                String text = new String(file.getBytes(), StandardCharsets.UTF_8);
                List<Document> docs = List.of(new Document(text,
                        Map.of("source", originalName != null ? originalName : "upload")));

                // Add metadata
                Map<String, Object> meta = extractMetadataFromFilename(originalName);
                meta.put("uploadedFile", true);
                for (Document doc : docs) {
                    doc.getMetadata().putAll(meta);
                }

                // Chunk, embed, store
                TokenTextSplitter splitter = new TokenTextSplitter(500, 100, 5, 10000, true);
                List<Document> chunks = splitter.apply(docs);
                vectorStore.add(chunks);
                totalChunks += chunks.size();
                ingested.add(originalName);

                log.info("Ingested uploaded file {} → {} chunks", originalName, chunks.size());
            } catch (Exception e) {
                log.warn("Failed to ingest uploaded file {}: {}", originalName, e.getMessage());
                failed.add(originalName + " (" + e.getMessage() + ")");
            }
        }

        // Persist SimpleVectorStore
        saveVectorStore();

        long elapsed = System.currentTimeMillis() - start;
        Map<String, Object> result = new HashMap<>();
        result.put("ingested", ingested);
        result.put("failed", failed);
        result.put("totalChunks", totalChunks);
        result.put("elapsedMs", elapsed);
        return result;
    }

    /**
     * Force a full re-ingestion of all bundled resources (deletes existing store first).
     */
    public Map<String, Object> forceReingest() {
        long start = System.currentTimeMillis();

        // Delete existing store file so startup logic runs fresh
        File vectorStoreFile = new File(vectorStorePath);
        if (vectorStoreFile.exists()) {
            vectorStoreFile.delete();
            log.info("Deleted existing vector store file for re-ingestion");
        }

        // Run the same logic as startup
        List<Document> allDocuments = new ArrayList<>();
        allDocuments.addAll(ingestMarkdownDocuments());

        int totalChunks = 0;
        if (!allDocuments.isEmpty()) {
            TokenTextSplitter splitter = new TokenTextSplitter(500, 100, 5, 10000, true);
            List<Document> chunks = splitter.apply(allDocuments);
            vectorStore.add(chunks);
            totalChunks = chunks.size();
            saveVectorStore();
        }

        long elapsed = System.currentTimeMillis() - start;
        Map<String, Object> result = new HashMap<>();
        result.put("documentsProcessed", allDocuments.size());
        result.put("totalChunks", totalChunks);
        result.put("elapsedMs", elapsed);
        return result;
    }

    /**
     * Return status information about the vector store on disk.
     */
    public Map<String, Object> getVectorStoreStatus() {
        File vectorStoreFile = new File(vectorStorePath);
        Map<String, Object> status = new HashMap<>();
        status.put("path", vectorStorePath);
        status.put("exists", vectorStoreFile.exists());
        status.put("sizeBytes", vectorStoreFile.exists() ? vectorStoreFile.length() : 0);
        status.put("lastModified", vectorStoreFile.exists()
                ? java.time.Instant.ofEpochMilli(vectorStoreFile.lastModified()).toString() : null);
        return status;
    }

    /**
     * Persist the SimpleVectorStore to disk if applicable.
     */
    private void saveVectorStore() {
        if (vectorStore instanceof SimpleVectorStore simpleStore) {
            File vectorStoreFile = new File(vectorStorePath);
            vectorStoreFile.getParentFile().mkdirs();
            simpleStore.save(vectorStoreFile);
            log.info("Vector store saved to {}", vectorStorePath);
        }
    }

    // SOP number → classpath filename mapping (Markdown files)
    private static final Map<String, String> SOP_FILES = Map.ofEntries(
        Map.entry("01", "classpath:Triage_Guidelines_and_Emergency_Routing.md"),
        Map.entry("02", "classpath:SOP_Appointment_and_Referral_Processes.md"),
        Map.entry("03", "classpath:Insurance_Coverage_and_Eligibility_Rules.md"),
        Map.entry("04", "classpath:KB_Department_Directory_and_Services.md"),
        Map.entry("05", "classpath:SOP_Hospital_Navigation_and_Visiting_Hours.md"),
        Map.entry("06", "classpath:Facility_and_Equipment_Troubleshooting_SOP.md"),
        Map.entry("07", "classpath:Privacy_PHI_and_Data_Handling_Standard.md"),
        Map.entry("08", "classpath:RAG_System_and_Query_Transformation_Policy.md"),
        Map.entry("09", "classpath:RAI_Safety_Guardrails_and_Moderation_Policy.md"),
        Map.entry("10", "classpath:Tool_Schemas_Agent_Workflows_and_API_Contracts.md")
    );

    /**
     * Ingest a single SOP Markdown file by its number (01–10).
     */
    public Map<String, Object> ingestSingleSop(String sopNumber) {
        long start = System.currentTimeMillis();
        String padded = sopNumber.length() == 1 ? "0" + sopNumber : sopNumber;
        String resourcePath = SOP_FILES.get(padded);

        if (resourcePath == null) {
            return Map.of("error", "No SOP found for number: " + sopNumber,
                          "validNumbers", "01–10");
        }

        try {
            Resource resource = resourceLoader.getResource(resourcePath);
            if (!resource.exists()) {
                return Map.of("error", "SOP file not found on classpath: " + resourcePath);
            }

            String filename = resource.getFilename();
            log.info("Ingesting SOP {}: {}", padded, filename);

            // Read markdown content
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String[] sections = content.split("(?m)(?=^#{1,2}\\s)", -1);
            Map<String, Object> baseMeta = extractMetadataFromFilename(filename);

            List<Document> docs = new ArrayList<>();
            for (String section : sections) {
                if (section.trim().isEmpty()) continue;
                Map<String, Object> metadata = new HashMap<>(baseMeta);
                String firstLine = section.lines().findFirst().orElse("");
                if (firstLine.matches("^#{1,2}\\s.*")) {
                    metadata.put("section", firstLine.replaceAll("^#{1,2}\\s*", "").trim());
                }
                docs.add(new Document(section.trim(), metadata));
            }

            TokenTextSplitter splitter = new TokenTextSplitter(500, 100, 5, 10000, true);
            List<Document> chunks = splitter.apply(docs);
            vectorStore.add(chunks);
            saveVectorStore();

            long elapsed = System.currentTimeMillis() - start;
            Map<String, Object> result = new HashMap<>();
            result.put("sopNumber", padded);
            result.put("filename", filename);
            result.put("sectionsExtracted", docs.size());
            result.put("chunksCreated", chunks.size());
            result.put("elapsedMs", elapsed);
            return result;

        } catch (Exception e) {
            log.error("Failed to ingest SOP {}: {}", padded, e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("error", "Ingestion failed for SOP " + padded + ": " + e.getMessage());
            return result;
        }
    }

}
