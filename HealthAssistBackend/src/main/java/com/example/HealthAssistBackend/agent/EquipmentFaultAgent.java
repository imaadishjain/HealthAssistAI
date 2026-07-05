package com.example.HealthAssistBackend.agent;

import com.example.HealthAssistBackend.model.RiskTier;
import com.example.HealthAssistBackend.rag.HealthcareRagService;
import com.example.HealthAssistBackend.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Equipment Fault Agent — Diagnose basic equipment issues → create facility ticket.
 * Handles facility/equipment malfunction reports.
 */
@Component
public class EquipmentFaultAgent {

    private static final Logger log = LoggerFactory.getLogger(EquipmentFaultAgent.class);

    private final ChatClient chatClient;
    private final HealthcareRagService ragService;
    private final AuditService auditService;

    public EquipmentFaultAgent(ChatClient chatClient,
                                HealthcareRagService ragService,
                                AuditService auditService) {
        this.chatClient = chatClient;
        this.ragService = ragService;
        this.auditService = auditService;
    }

    /**
     * Execute equipment fault workflow.
     */
    public EquipmentResult execute(String issueDescription, String workflowId) {
        long start = System.currentTimeMillis();
        log.info("[{}] EquipmentFaultAgent processing: {}", workflowId, issueDescription.substring(0, Math.min(issueDescription.length(), 100)));

        // Step 1: Retrieve facility/equipment SOPs
        HealthcareRagService.RagResult ragResult = ragService.retrieve(issueDescription,
                Map.of("category", "facility"));

        auditService.logSimple(workflowId, 1, "EQUIPMENT_KB_RETRIEVAL", "EquipmentFaultAgent",
                issueDescription, "Retrieved " + ragResult.documentCount() + " documents");

        // Step 2: Generate assessment and create ticket via LLM with tool calling
        String systemPrompt = """
            You are a helpful hospital facility management assistant. A staff member has reported an equipment or facility issue.
            You MUST:
            1. Acknowledge the reported issue with empathy and urgency appropriate to the severity
            2. Assess the severity (LOW/MEDIUM/HIGH/CRITICAL)
            3. Use the createIncidentTicket tool to log the issue
            4. Provide basic troubleshooting steps if applicable (from KB context)
            5. Inform the reporter about expected response times based on priority
            6. If the issue affects patient safety, classify as HIGH priority
            7. ALWAYS ask if there are any additional details they want to add or if they have other issues to report
            8. Be conversational and reassuring — let them know the issue is being handled

            Priority Guidelines:
            - CRITICAL: Directly affects patient care (e.g., ventilator, defibrillator failure)
            - HIGH: Major equipment failure affecting department operations
            - MEDIUM: Equipment degraded but workaround available
            - LOW: Minor issues, cosmetic, non-urgent

            Facility Knowledge Base Context:
            """ + (ragResult.hasContext() ? ragResult.context() : "No specific facility guidelines found.");

        try {
            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user("Equipment/Facility Issue Report: " + issueDescription)
                    .toolNames("createIncidentTicket")
                    .call()
                    .content();

            auditService.logStep(workflowId, 2, "EQUIPMENT_ASSESSMENT_COMPLETE", "EquipmentFaultAgent",
                    issueDescription, response, "createIncidentTicket", null,
                    "LOW", false, false, false, System.currentTimeMillis() - start);

            return new EquipmentResult(response, ragResult.citations(), workflowId);

        } catch (Exception e) {
            log.error("[{}] EquipmentFaultAgent error: {}", workflowId, e.getMessage());
            return new EquipmentResult(
                "I've noted your equipment issue report. Unfortunately, I couldn't create an automated ticket at this time. " +
                "Please contact the Facilities Department directly at (555) 123-4580 or email facilities@hospital.com. " +
                "For critical patient-safety equipment failures, please call the emergency line immediately.",
                List.of(), workflowId
            );
        }
    }

    /**
     * Equipment fault agent result.
     */
    public record EquipmentResult(String response, List<String> citations, String workflowId) {}
}
