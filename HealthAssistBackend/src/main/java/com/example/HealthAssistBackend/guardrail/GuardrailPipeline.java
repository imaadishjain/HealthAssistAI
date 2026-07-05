package com.example.HealthAssistBackend.guardrail;

import com.example.HealthAssistBackend.model.RiskTier;
import com.example.HealthAssistBackend.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates all guardrail services: moderation, PII redaction, risk classification.
 * Applies a full healthcare-grade safety pipeline to inputs and outputs.
 */
@Service
public class GuardrailPipeline {

    private static final Logger log = LoggerFactory.getLogger(GuardrailPipeline.class);

    private final ModerationService moderationService;
    private final PiiRedactionService piiRedactionService;
    private final RiskClassifierService riskClassifierService;
    private final AuditService auditService;

    public GuardrailPipeline(ModerationService moderationService,
                              PiiRedactionService piiRedactionService,
                              RiskClassifierService riskClassifierService,
                              AuditService auditService) {
        this.moderationService = moderationService;
        this.piiRedactionService = piiRedactionService;
        this.riskClassifierService = riskClassifierService;
        this.auditService = auditService;
    }

    /**
     * Process input through the full guardrail pipeline.
     * Returns a GuardrailResult with the processed (safe) input and all classification details.
     */
    public GuardrailResult processInput(String input, String workflowId) {
        long start = System.currentTimeMillis();

        // Step 1: Moderation — check for unsafe/off-topic content
        ModerationService.ModerationResult moderation = moderationService.moderateInput(input);
        if (!moderation.allowed()) {
            log.warn("Input BLOCKED by moderation: {}", moderation.issues());
            auditService.logStep(workflowId, 0, "INPUT_MODERATION_BLOCKED", "GuardrailPipeline",
                    input, "BLOCKED: " + moderation.issues(), null,
                    auditService.toJson(moderation), "BLOCKED",
                    false, false, true, System.currentTimeMillis() - start);

            return new GuardrailResult(
                null, false, true, false, RiskTier.LOW, false,
                "I'm unable to process this request. " + String.join("; ", moderation.issues()) +
                ". If you're experiencing a medical emergency, please call 911 immediately.",
                moderation, null, null
            );
        }

        // Step 2: PII Redaction — remove personal identifiers
        PiiRedactionService.RedactionResult redaction = piiRedactionService.redact(input);
        String safeInput = redaction.redactedText();

        // Step 3: Risk Classification — assess symptom severity
        RiskClassifierService.RiskClassification risk = riskClassifierService.classify(safeInput);

        // Step 4: Log guardrail results
        boolean escalated = risk.escalationRequired();
        auditService.logStep(workflowId, 0, "GUARDRAIL_PIPELINE", "GuardrailPipeline",
                "[REDACTED]", "Risk: " + risk.riskTier() + ", PII: " + redaction.piiDetected(),
                null, buildGuardrailSummary(moderation, redaction, risk),
                risk.riskTier().name(), escalated,
                redaction.piiDetected(), !moderation.issues().isEmpty(),
                System.currentTimeMillis() - start);

        return new GuardrailResult(
            safeInput, true, false,
            redaction.piiDetected(), risk.riskTier(), escalated,
            null, moderation, redaction, risk
        );
    }

    /**
     * Process model output through safety checks.
     */
    public String processOutput(String output, String workflowId) {
        // Moderate output
        ModerationService.ModerationResult moderation = moderationService.moderateOutput(output);

        // Redact any PII in output
        PiiRedactionService.RedactionResult redaction = piiRedactionService.redact(output);
        String safeOutput = redaction.redactedText();

        // Add disclaimer if moderation has warnings
        if (!moderation.issues().isEmpty()) {
            safeOutput += "\n\n⚠️ **Disclaimer**: " + String.join(". ", moderation.issues()) +
                ". Please consult a qualified healthcare professional for medical advice, diagnosis, or treatment.";
        }

        return safeOutput;
    }

    /**
     * Redact PII from text without running full moderation pipeline.
     * Useful for sanitising conversation history before passing to LLM context.
     */
    public String redactText(String text) {
        if (text == null || text.isBlank()) return text;
        return piiRedactionService.redact(text).redactedText();
    }

    /**
     * Expose the risk classifier for department-inquiry checks in the orchestrator.
     */
    public RiskClassifierService getRiskClassifierService() {
        return riskClassifierService;
    }

    /**
     * Build a JSON summary of guardrail results for audit logging.
     */
    private String buildGuardrailSummary(ModerationService.ModerationResult moderation,
                                          PiiRedactionService.RedactionResult redaction,
                                          RiskClassifierService.RiskClassification risk) {
        return String.format(
            "{\"moderation\":{\"status\":\"%s\",\"issues\":%s},\"pii\":{\"detected\":%s,\"types\":%s},\"risk\":{\"tier\":\"%s\",\"escalation\":%s}}",
            moderation.status(), auditService.toJson(moderation.issues()),
            redaction.piiDetected(), auditService.toJson(redaction.detectedTypes()),
            risk.riskTier(), risk.escalationRequired()
        );
    }

    /**
     * Complete guardrail pipeline result.
     */
    public record GuardrailResult(
        String safeInput,
        boolean allowed,
        boolean moderationBlocked,
        boolean piiDetected,
        RiskTier riskTier,
        boolean escalationRequired,
        String blockMessage,
        ModerationService.ModerationResult moderationResult,
        PiiRedactionService.RedactionResult redactionResult,
        RiskClassifierService.RiskClassification riskClassification
    ) {}
}
