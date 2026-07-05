package com.example.HealthAssistBackend.controller;

import com.example.HealthAssistBackend.service.DocumentIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/documents")
@CrossOrigin(origins = "http://localhost:4200")
@Tag(name = "Documents", description = "Markdown / document ingestion into the vector store")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);
    private final DocumentIngestionService ingestionService;

    public DocumentController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    // ── Individual SOP endpoints (click Execute → auto-ingests that Markdown file) ──

    @Operation(summary = "SOP 01 — Triage Guidelines & Emergency Routing")
    @PostMapping("/ingest/sop01")
    public ResponseEntity<Map<String, Object>> ingestSop01() {
        return ingest("01");
    }

    @Operation(summary = "SOP 02 — Appointment & Referral Processes")
    @PostMapping("/ingest/sop02")
    public ResponseEntity<Map<String, Object>> ingestSop02() {
        return ingest("02");
    }

    @Operation(summary = "SOP 03 — Insurance Coverage & Eligibility Rules")
    @PostMapping("/ingest/sop03")
    public ResponseEntity<Map<String, Object>> ingestSop03() {
        return ingest("03");
    }

    @Operation(summary = "SOP 04 — Department Directory & Services")
    @PostMapping("/ingest/sop04")
    public ResponseEntity<Map<String, Object>> ingestSop04() {
        return ingest("04");
    }

    @Operation(summary = "SOP 05 — Hospital Navigation & Visiting Hours")
    @PostMapping("/ingest/sop05")
    public ResponseEntity<Map<String, Object>> ingestSop05() {
        return ingest("05");
    }

    @Operation(summary = "SOP 06 — Facility & Equipment Troubleshooting")
    @PostMapping("/ingest/sop06")
    public ResponseEntity<Map<String, Object>> ingestSop06() {
        return ingest("06");
    }

    @Operation(summary = "SOP 07 — Privacy, PHI & Data Handling Standard")
    @PostMapping("/ingest/sop07")
    public ResponseEntity<Map<String, Object>> ingestSop07() {
        return ingest("07");
    }

    @Operation(summary = "SOP 08 — RAG System & Query Transformation Policy")
    @PostMapping("/ingest/sop08")
    public ResponseEntity<Map<String, Object>> ingestSop08() {
        return ingest("08");
    }

    @Operation(summary = "SOP 09 — RAI Safety Guardrails & Moderation Policy")
    @PostMapping("/ingest/sop09")
    public ResponseEntity<Map<String, Object>> ingestSop09() {
        return ingest("09");
    }

    @Operation(summary = "SOP 10 — Tool Schemas, Agent Workflows & API Contracts")
    @PostMapping("/ingest/sop10")
    public ResponseEntity<Map<String, Object>> ingestSop10() {
        return ingest("10");
    }

    // ── Bulk & KB endpoints ──

    @Operation(summary = "Ingest all 10 Markdown knowledge base documents")
    @PostMapping("/ingest/all")
    public ResponseEntity<Map<String, Object>> ingestAll() {
        try {
            return ResponseEntity.ok(ingestionService.forceReingest());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Vector store status")
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(ingestionService.getVectorStoreStatus());
    }

    // ── Helper ──

    private ResponseEntity<Map<String, Object>> ingest(String sopNumber) {
        log.info("POST /documents/ingest/sop{}", sopNumber);
        try {
            Map<String, Object> result = ingestionService.ingestSingleSop(sopNumber);
            if (result.containsKey("error")) {
                return ResponseEntity.status(404).body(result);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
