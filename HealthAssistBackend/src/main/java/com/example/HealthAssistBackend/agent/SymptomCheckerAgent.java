package com.example.HealthAssistBackend.agent;

import com.example.HealthAssistBackend.guardrail.GuardrailPipeline;
import com.example.HealthAssistBackend.guardrail.RiskClassifierService;
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
 * Symptom Checker Agent — Assess symptoms → recommend department → offer appointment.
 * Implements prompt chaining pattern: triage → route → offer.
 */
@Component
public class SymptomCheckerAgent {

    private static final Logger log = LoggerFactory.getLogger(SymptomCheckerAgent.class);

    private final ChatClient chatClient;
    private final HealthcareRagService ragService;
    private final RiskClassifierService riskClassifier;
    private final AuditService auditService;

    public SymptomCheckerAgent(ChatClient chatClient,
                                HealthcareRagService ragService,
                                RiskClassifierService riskClassifier,
                                AuditService auditService) {
        this.chatClient = chatClient;
        this.ragService = ragService;
        this.riskClassifier = riskClassifier;
        this.auditService = auditService;
    }

    /**
     * Execute symptom checker workflow.
     */
    public AgentResult execute(String symptoms, String workflowId) {
        long start = System.currentTimeMillis();
        log.info("[{}] SymptomCheckerAgent processing: {}", workflowId, symptoms.substring(0, Math.min(symptoms.length(), 100)));

        // Step 1: Risk classification
        RiskClassifierService.RiskClassification risk = riskClassifier.classify(symptoms);
        auditService.logSimple(workflowId, 1, "SYMPTOM_RISK_CLASSIFICATION", "SymptomCheckerAgent",
                symptoms, "Risk: " + risk.riskTier());

        // Step 2: RAG retrieval for triage guidelines
        HealthcareRagService.RagResult ragResult = ragService.retrieve(symptoms,
                Map.of("category", "triage"));

        auditService.logSimple(workflowId, 2, "RAG_TRIAGE_RETRIEVAL", "SymptomCheckerAgent",
                symptoms, "Retrieved " + ragResult.documentCount() + " documents");

        // Step 3: Generate assessment using LLM with RAG context
        String systemPrompt = """
            You are a medical triage assistant having a caring conversation with a patient. Based on the patient's symptoms and the hospital triage guidelines provided,
            generate a structured assessment. You MUST:
            1. Acknowledge the patient's symptoms with genuine empathy (e.g., "I'm sorry to hear you're experiencing...")
            2. Provide a preliminary severity assessment (LOW/MEDIUM/HIGH/CRITICAL)
            3. **ALWAYS clearly recommend the most appropriate department** (e.g., "Based on your symptoms, I recommend visiting the **Cardiology** department.")
            4. List the recommended department prominently using bold formatting
            5. If the patient is asking about which department to visit, make the department recommendation the PRIMARY focus of the response
            6. Suggest whether they should schedule an appointment or seek immediate care
            7. Include a clear disclaimer that this is NOT a medical diagnosis
            8. If risk is HIGH or CRITICAL, advise seeking prompt medical attention but still provide the department recommendation
            9. Ask a follow-up question to better understand their condition (e.g., "How long have you been experiencing this?" or "Have you taken any medication?")
            10. ALWAYS end by proactively offering to book an appointment: "Would you like me to book an appointment with a [department] specialist right now?"
            
            IMPORTANT: When a patient asks which department to go to, your response MUST include:
            - The specific department name in bold (e.g., **Cardiology**, **Neurology**)
            - A brief explanation of why this department is recommended
            - An offer to check available doctors in that department
            - An offer to schedule an appointment immediately
            
            Be conversational and supportive — the patient should feel heard and guided, not lectured at.

            Hospital Triage Context:
            """ + (ragResult.hasContext() ? ragResult.context() : "No specific triage guidelines found. Use general medical knowledge with extra caution.");

        try {
            String assessment = chatClient.prompt()
                    .system(systemPrompt)
                    .user("Patient symptoms: " + symptoms)
                    .toolNames("triageAssessment", "medicalDepartmentRouter", "checkDoctorAvailability")
                    .call()
                    .content();

            auditService.logStep(workflowId, 3, "SYMPTOM_ASSESSMENT_GENERATED", "SymptomCheckerAgent",
                    symptoms, assessment, "triageAssessment,medicalDepartmentRouter,checkDoctorAvailability",
                    null, risk.riskTier().name(), risk.escalationRequired(), false, false,
                    System.currentTimeMillis() - start);

            return new AgentResult(assessment, risk.riskTier(), risk.escalationRequired(),
                    ragResult.citations(), workflowId);

        } catch (Exception e) {
            log.error("[{}] SymptomCheckerAgent error: {}", workflowId, e.getMessage());
            return new AgentResult(
                "I apologize, but I'm unable to complete the symptom assessment at this time. " +
                "If you're experiencing severe symptoms, please call 911 or visit your nearest emergency room immediately. " +
                "For non-urgent symptoms, please contact our front desk at (555) 123-4567.",
                risk.riskTier(), risk.escalationRequired(), List.of(), workflowId
            );
        }
    }

    /**
     * Agent execution result.
     */
    public record AgentResult(String response, RiskTier riskTier, boolean escalated,
                               List<String> citations, String workflowId) {}
}
