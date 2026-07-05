package com.example.HealthAssistBackend.service;

import com.example.HealthAssistBackend.model.AuditLog;
import com.example.HealthAssistBackend.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate a new unique workflow ID.
     */
    public String generateWorkflowId() {
        return "WF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Log an audit entry for a workflow step.
     */
    public AuditLog logStep(String workflowId, int step, String action, String agentName,
                             String input, String output, String toolCalls,
                             String guardrailResults, String riskTier,
                             boolean escalated, boolean piiDetected, boolean moderationFlagged,
                             long durationMs) {
        log.debug("Audit [{}] Step {}: {} by {} (risk: {}, escalated: {})",
                workflowId, step, action, agentName, riskTier, escalated);

        AuditLog auditLog = new AuditLog(workflowId, step, action, agentName);
        auditLog.setInputData(truncate(input, 4000));
        auditLog.setOutputData(truncate(output, 4000));
        auditLog.setToolCalls(toolCalls);
        auditLog.setGuardrailResults(guardrailResults);
        auditLog.setRiskTier(riskTier);
        auditLog.setEscalated(escalated);
        auditLog.setPiiDetected(piiDetected);
        auditLog.setModerationFlagged(moderationFlagged);
        auditLog.setDurationMs(durationMs);
        auditLog.setCreatedAt(LocalDateTime.now());

        return auditLogRepository.save(auditLog);
    }

    /**
     * Simplified audit log method for quick logging.
     */
    public AuditLog logSimple(String workflowId, int step, String action, String agentName,
                               String input, String output) {
        return logStep(workflowId, step, action, agentName, input, output,
                null, null, "LOW", false, false, false, 0);
    }

    /**
     * Get all audit logs for a specific workflow.
     */
    public List<AuditLog> getAuditByWorkflowId(String workflowId) {
        return auditLogRepository.findByWorkflowIdOrderByStepNumberAsc(workflowId);
    }

    /**
     * Get escalated audit entries.
     */
    public List<AuditLog> getEscalatedAudits() {
        return auditLogRepository.findByEscalatedTrue();
    }

    /**
     * Get audits with PII detections.
     */
    public List<AuditLog> getPiiAudits() {
        return auditLogRepository.findByPiiDetectedTrue();
    }

    /**
     * Get audits flagged by moderation.
     */
    public List<AuditLog> getModerationFlaggedAudits() {
        return auditLogRepository.findByModerationFlaggedTrue();
    }

    /**
     * Get all recent audit logs (latest 100).
     */
    public List<AuditLog> getAllRecentAudits() {
        return auditLogRepository.findTop100ByOrderByCreatedAtDesc();
    }

    /**
     * Get all distinct workflow IDs.
     */
    public List<String> getDistinctWorkflowIds() {
        return auditLogRepository.findDistinctWorkflowIds();
    }

    /**
     * Truncate string to max length for DB storage.
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    /**
     * Convert object to JSON string for audit logging.
     */
    public String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj != null ? obj.toString() : "null";
        }
    }
}
