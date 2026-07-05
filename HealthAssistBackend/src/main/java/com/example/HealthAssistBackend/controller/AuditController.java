package com.example.HealthAssistBackend.controller;

import com.example.HealthAssistBackend.model.AuditLog;
import com.example.HealthAssistBackend.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Audit controller for reviewing workflow decisions and provenance.
 */
@RestController
@CrossOrigin(origins = "http://localhost:4200")
@Tag(name = "Audit", description = "Workflow audit trail & provenance")
public class AuditController {

    private static final Logger log = LoggerFactory.getLogger(AuditController.class);

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Get all recent audit logs (latest 100).
     * GET /audit/all
     */
    @GetMapping("/audit/all")
    @Operation(summary = "Get all recent audit logs")
    public ResponseEntity<List<AuditLog>> getAllRecentAudits() {
        return ResponseEntity.ok(auditService.getAllRecentAudits());
    }

    /**
     * Get all distinct workflow IDs for selection.
     * GET /audit/workflows
     */
    @GetMapping("/audit/workflows")
    @Operation(summary = "Get distinct workflow IDs")
    public ResponseEntity<List<String>> getDistinctWorkflowIds() {
        return ResponseEntity.ok(auditService.getDistinctWorkflowIds());
    }

    /**
     * Get audit trail for a specific workflow.
     * GET /audit/{workflowId}
     */
    @GetMapping("/audit/{workflowId}")
    public ResponseEntity<List<AuditLog>> getAuditByWorkflow(@PathVariable String workflowId) {
        log.info("GET /audit/{}", workflowId);
        List<AuditLog> logs = auditService.getAuditByWorkflowId(workflowId);
        if (logs.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(logs);
    }

    /**
     * Get all escalated audit entries.
     * GET /audit/escalated
     */
    @GetMapping("/audit/escalated")
    public ResponseEntity<List<AuditLog>> getEscalatedAudits() {
        return ResponseEntity.ok(auditService.getEscalatedAudits());
    }

    /**
     * Get all PII-detected audit entries.
     * GET /audit/pii
     */
    @GetMapping("/audit/pii")
    public ResponseEntity<List<AuditLog>> getPiiAudits() {
        return ResponseEntity.ok(auditService.getPiiAudits());
    }

    /**
     * Get all moderation-flagged audit entries.
     * GET /audit/moderation
     */
    @GetMapping("/audit/moderation")
    public ResponseEntity<List<AuditLog>> getModerationFlaggedAudits() {
        return ResponseEntity.ok(auditService.getModerationFlaggedAudits());
    }
}
