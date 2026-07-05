package com.example.HealthAssistBackend.agent;

import com.example.HealthAssistBackend.guardrail.RiskClassifierService;
import com.example.HealthAssistBackend.model.RiskTier;
import com.example.HealthAssistBackend.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Emergency Routing Agent — Classify HIGH RISK → escalate to human nurse/doctor.
 * Handles critical and high-risk situations with immediate escalation.
 */
@Component
public class EmergencyRoutingAgent {

    private static final Logger log = LoggerFactory.getLogger(EmergencyRoutingAgent.class);

    private final ChatClient chatClient;
    private final RiskClassifierService riskClassifier;
    private final AuditService auditService;

    public EmergencyRoutingAgent(ChatClient chatClient,
                                  RiskClassifierService riskClassifier,
                                  AuditService auditService) {
        this.chatClient = chatClient;
        this.riskClassifier = riskClassifier;
        this.auditService = auditService;
    }

    /**
     * Execute emergency routing workflow.
     */
    public EmergencyResult execute(String input, String workflowId) {
        long start = System.currentTimeMillis();
        log.warn("[{}] EmergencyRoutingAgent activated for: {}", workflowId, input.substring(0, Math.min(input.length(), 100)));

        RiskClassifierService.RiskClassification risk = riskClassifier.classify(input);

        String response;
        if (risk.riskTier() == RiskTier.CRITICAL) {
            response = generateCriticalResponse(input);
            auditService.logStep(workflowId, 1, "CRITICAL_EMERGENCY_ESCALATION", "EmergencyRoutingAgent",
                    "[REDACTED-EMERGENCY]", response, null, null,
                    "CRITICAL", true, false, false, System.currentTimeMillis() - start);
        } else if (risk.riskTier() == RiskTier.HIGH) {
            response = generateHighRiskResponse(input);
            auditService.logStep(workflowId, 1, "HIGH_RISK_ESCALATION", "EmergencyRoutingAgent",
                    "[REDACTED-HIGH-RISK]", response, null, null,
                    "HIGH", true, false, false, System.currentTimeMillis() - start);
        } else {
            response = "Your symptoms have been assessed. While not classified as an emergency, " +
                "please monitor your condition and seek medical attention if symptoms worsen. " +
                "Would you like to schedule an appointment with a specialist?";
            auditService.logSimple(workflowId, 1, "NON_EMERGENCY_ASSESSMENT", "EmergencyRoutingAgent",
                    input, response);
        }

        return new EmergencyResult(response, risk.riskTier(), risk.escalationRequired(), workflowId);
    }

    private String generateCriticalResponse(String input) {
        try {
            return chatClient.prompt()
                .system("""
                    You are an emergency medical assistant. The patient has described CRITICAL symptoms.
                    Your response MUST:
                    1. Start with "🚨 EMERGENCY ALERT" in bold
                    2. Instruct them to call 911 IMMEDIATELY
                    3. Provide basic first-aid instructions while waiting for help
                    4. State that a human medical professional has been notified
                    5. Be calm, clear, and reassuring
                    6. Do NOT attempt diagnosis
                    Keep the response concise and action-oriented.
                    """)
                .user("Patient emergency: " + input)
                .call()
                .content();
        } catch (Exception e) {
            return "🚨 **EMERGENCY ALERT**: Based on your symptoms, please call 911 or go to your nearest emergency room IMMEDIATELY. " +
                "A medical professional has been notified of your situation. " +
                "While waiting for help: Stay calm, do not exert yourself, and keep your airway clear. " +
                "If someone is with you, ask them to stay by your side.";
        }
    }

    private String generateHighRiskResponse(String input) {
        try {
            return chatClient.prompt()
                .system("""
                    You are a medical triage assistant handling a HIGH-RISK case.
                    Your response MUST:
                    1. Start with "⚠️ HIGH RISK ALERT"
                    2. Strongly recommend seeking immediate medical attention
                    3. Mention this case is being escalated to a nurse/doctor for review
                    4. Provide the ER contact information: (555) 123-4567
                    5. Include clear disclaimer about not being a medical diagnosis
                    Keep the response empathetic and urgent.
                    """)
                .user("Patient symptoms: " + input)
                .call()
                .content();
        } catch (Exception e) {
            return "⚠️ **HIGH RISK ALERT**: Your symptoms suggest you need prompt medical attention. " +
                "This case has been escalated to a nurse/doctor for immediate review. " +
                "Please proceed to the nearest emergency room or call our ER at (555) 123-4567. " +
                "Note: This is not a medical diagnosis. A healthcare professional will contact you shortly.";
        }
    }

    /**
     * Emergency routing result.
     */
    public record EmergencyResult(String response, RiskTier riskTier, boolean escalated, String workflowId) {}
}
