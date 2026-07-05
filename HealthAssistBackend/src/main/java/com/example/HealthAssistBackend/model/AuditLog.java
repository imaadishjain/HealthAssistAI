package com.example.HealthAssistBackend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false, length = 100)
    private String workflowId;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(nullable = false)
    private String action;

    @Column(name = "agent_name", length = 100)
    private String agentName;

    @Column(name = "input_data", columnDefinition = "TEXT")
    private String inputData;

    @Column(name = "output_data", columnDefinition = "TEXT")
    private String outputData;

    @Column(name = "tool_calls", columnDefinition = "TEXT")
    private String toolCalls;

    @Column(name = "guardrail_results", columnDefinition = "TEXT")
    private String guardrailResults;

    @Column(name = "risk_tier", length = 20)
    private String riskTier;

    @Column(nullable = false)
    private Boolean escalated = false;

    @Column(name = "pii_detected", nullable = false)
    private Boolean piiDetected = false;

    @Column(name = "moderation_flagged", nullable = false)
    private Boolean moderationFlagged = false;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public AuditLog() {}

    public AuditLog(String workflowId, int stepNumber, String action, String agentName) {
        this.workflowId = workflowId;
        this.stepNumber = stepNumber;
        this.action = action;
        this.agentName = agentName;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }

    public Integer getStepNumber() { return stepNumber; }
    public void setStepNumber(Integer stepNumber) { this.stepNumber = stepNumber; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public String getInputData() { return inputData; }
    public void setInputData(String inputData) { this.inputData = inputData; }

    public String getOutputData() { return outputData; }
    public void setOutputData(String outputData) { this.outputData = outputData; }

    public String getToolCalls() { return toolCalls; }
    public void setToolCalls(String toolCalls) { this.toolCalls = toolCalls; }

    public String getGuardrailResults() { return guardrailResults; }
    public void setGuardrailResults(String guardrailResults) { this.guardrailResults = guardrailResults; }

    public String getRiskTier() { return riskTier; }
    public void setRiskTier(String riskTier) { this.riskTier = riskTier; }

    public Boolean getEscalated() { return escalated; }
    public void setEscalated(Boolean escalated) { this.escalated = escalated; }

    public Boolean getPiiDetected() { return piiDetected; }
    public void setPiiDetected(Boolean piiDetected) { this.piiDetected = piiDetected; }

    public Boolean getModerationFlagged() { return moderationFlagged; }
    public void setModerationFlagged(Boolean moderationFlagged) { this.moderationFlagged = moderationFlagged; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
