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
 * Insurance Query Agent — Retrieve KB data → verify eligibility → respond.
 * Handles insurance coverage, eligibility, and preauthorization queries.
 */
@Component
public class InsuranceQueryAgent {

    private static final Logger log = LoggerFactory.getLogger(InsuranceQueryAgent.class);

    private final ChatClient chatClient;
    private final HealthcareRagService ragService;
    private final AuditService auditService;

    public InsuranceQueryAgent(ChatClient chatClient,
                                HealthcareRagService ragService,
                                AuditService auditService) {
        this.chatClient = chatClient;
        this.ragService = ragService;
        this.auditService = auditService;
    }

    /**
     * Execute insurance query workflow.
     */
    public InsuranceResult execute(String query, String insurancePlan, String workflowId) {
        long start = System.currentTimeMillis();
        log.info("[{}] InsuranceQueryAgent processing: {}", workflowId, query.substring(0, Math.min(query.length(), 100)));

        // Step 1: Retrieve insurance-related KB documents
        Map<String, String> filters = Map.of("category", "insurance");
        if (insurancePlan != null && !insurancePlan.isBlank()) {
            filters = Map.of("category", "insurance", "insurance_plan", insurancePlan);
        }

        HealthcareRagService.RagResult ragResult = ragService.retrieve(query, filters);

        auditService.logSimple(workflowId, 1, "INSURANCE_KB_RETRIEVAL", "InsuranceQueryAgent",
                query, "Retrieved " + ragResult.documentCount() + " documents");

        // Step 2: Generate response using LLM with KB context
        String systemPrompt = """
            You are a friendly hospital insurance assistance specialist having a helpful conversation with a patient. Using ONLY the hospital knowledge base context provided,
            answer the patient's insurance-related question. You MUST:
            1. Only provide information found in the KB context
            2. Clearly state coverage details, copays, or eligibility criteria
            3. If the information is not in the KB, say "I don't have specific information about that in our records"
            4. Suggest contacting the insurance department at (555) 123-4570 for detailed inquiries
            5. Never guarantee coverage — always say "based on our records" or "typically"
            6. Include disclaimer about verifying with the insurance provider
            7. Be conversational — explain in plain, simple language that a patient can easily understand
            8. ALWAYS end by asking if the patient has any other insurance questions or needs help with something else
            9. If relevant, proactively mention related coverage information (e.g., if they ask about MRI coverage, also mention any pre-authorization requirements)

            Insurance Knowledge Base Context:
            """ + (ragResult.hasContext() ? ragResult.context() :
            "No specific insurance information found in the knowledge base. Please advise patient to contact insurance department.");

        try {
            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user("Insurance query: " + query + (insurancePlan != null ? " (Plan: " + insurancePlan + ")" : ""))
                    .call()
                    .content();

            auditService.logStep(workflowId, 2, "INSURANCE_RESPONSE_GENERATED", "InsuranceQueryAgent",
                    query, response, null, null,
                    "LOW", false, false, false, System.currentTimeMillis() - start);

            return new InsuranceResult(response, ragResult.citations(), ragResult.hasContext(), workflowId);

        } catch (Exception e) {
            log.error("[{}] InsuranceQueryAgent error: {}", workflowId, e.getMessage());
            return new InsuranceResult(
                "I apologize, but I'm unable to retrieve insurance information at this time. " +
                "Please contact our Insurance Department directly at (555) 123-4570 or visit the insurance desk on the Ground Floor, Building A.",
                List.of(), false, workflowId
            );
        }
    }

    /**
     * Insurance query result.
     */
    public record InsuranceResult(String response, List<String> citations, boolean hasKbContext, String workflowId) {}
}
