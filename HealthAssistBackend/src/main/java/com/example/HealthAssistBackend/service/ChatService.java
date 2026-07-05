package com.example.HealthAssistBackend.service;

import com.example.HealthAssistBackend.agent.HealthAgentOrchestrator;
import com.example.HealthAssistBackend.guardrail.GuardrailPipeline;
import com.example.HealthAssistBackend.model.ChatRequest;
import com.example.HealthAssistBackend.model.ChatResponse;
import com.example.HealthAssistBackend.rag.HealthcareRagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Chat service providing sync, async (streaming), and agentic chat capabilities.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatClient chatClient;
    private final HealthAgentOrchestrator orchestrator;
    private final GuardrailPipeline guardrailPipeline;
    private final HealthcareRagService ragService;
    private final AuditService auditService;

    private static final String HEALTHCARE_SYSTEM_PROMPT = """
        You are HealthAssist AI, a smart, empathetic, and conversational healthcare assistant for City General Hospital.
        You don't just answer questions — you ENGAGE with users in a natural, helpful dialogue.

        CONVERSATION RULES:
        1. ALWAYS greet the user warmly if they greet you (e.g., "Hi! 👋 How can I help you today?")
        2. NEVER provide medical diagnoses, prescriptions, or treatment plans
        3. ALWAYS include disclaimers when discussing symptoms or medical conditions
        4. For HIGH/CRITICAL risk symptoms, IMMEDIATELY advise calling 911 or visiting the ER
        5. ONLY use information from the hospital knowledge base when answering factual questions
        6. If you don't know something, say so honestly and suggest contacting appropriate staff
        7. Be warm, empathetic, clear, and professional in all responses
        8. Protect patient privacy — never ask for or display full names, SSNs, or medical record numbers
        9. When using tools, confirm important details before taking action
        10. ALWAYS end your response with a follow-up question or helpful suggestion to continue the conversation
        11. If the user's request is vague, ask a clarifying question instead of guessing

        INTERACTION STYLE:
        - Ask follow-up questions to understand the user's needs better
        - Offer proactive suggestions (e.g., "Would you also like to check doctor availability?")
        - When answering, acknowledge what the user said first, then provide the answer
        - Use phrases like "Is there anything else I can help with?" or "Would you like to..."
        - Make the user feel heard and supported, not processed

        YOUR CAPABILITIES:
        - Answer questions about hospital departments, visiting hours, locations, and procedures
        - Perform preliminary symptom triage (with disclaimers) and recommend departments
        - Check doctor availability and help schedule appointments
        - Look up and display a patient's existing/scheduled appointments when they ask
        - Help report facility/equipment issues by creating tickets
        - Look up and display existing tickets when a user asks about their reported issues
        - Answer insurance coverage and eligibility questions
        - Provide discharge instructions and follow-up guidance

        SAFETY GUIDELINES:
        - If a patient describes chest pain, difficulty breathing, stroke symptoms, severe bleeding,
          or loss of consciousness → IMMEDIATELY advise calling 911
        - Never recommend specific medications or dosages
        - Always end symptom discussions with: "This is for informational purposes only. Please consult
          a healthcare professional for proper diagnosis and treatment."

        TONE: Warm, conversational, empathetic, clear. Use simple language. Avoid medical jargon when possible.
        """;

    public ChatService(ChatClient chatClient,
                        HealthAgentOrchestrator orchestrator,
                        GuardrailPipeline guardrailPipeline,
                        HealthcareRagService ragService,
                        AuditService auditService) {
        this.chatClient = chatClient;
        this.orchestrator = orchestrator;
        this.guardrailPipeline = guardrailPipeline;
        this.ragService = ragService;
        this.auditService = auditService;
    }

    /**
     * Synchronous chat with healthcare-safe RAG pipeline.
     */
    public ChatResponse chatSync(ChatRequest request) {
        log.info("Sync chat request: {}", request.message().substring(0, Math.min(request.message().length(), 100)));
        String workflowId = auditService.generateWorkflowId();

        // Guardrail check
        GuardrailPipeline.GuardrailResult guardrail = guardrailPipeline.processInput(request.message(), workflowId);
        if (!guardrail.allowed()) {
            return ChatResponse.simple(guardrail.blockMessage());
        }

        // RAG retrieval
        HealthcareRagService.RagResult ragResult = ragService.simpleRetrieve(guardrail.safeInput());

        String contextPrompt = HEALTHCARE_SYSTEM_PROMPT;
        if (ragResult.hasContext()) {
            contextPrompt += "\n\nHOSPITAL KNOWLEDGE BASE CONTEXT:\n" + ragResult.context();
        }

        // Append conversation history for multi-turn context (PII-redacted)
        contextPrompt += guardrailPipeline.redactText(request.formattedHistory());

        try {
            String response = chatClient.prompt()
                .system(contextPrompt)
                .user(guardrail.safeInput())
                .toolNames("checkDoctorAvailability", "createAppointment", "createIncidentTicket",
                          "triageAssessment", "medicalDepartmentRouter",
                          "lookupAppointments", "lookupTickets")
                .call()
                .content();

            String safeOutput = guardrailPipeline.processOutput(response, workflowId);

            if (guardrail.escalationRequired()) {
                return ChatResponse.escalated(safeOutput, guardrail.riskTier().name(), workflowId);
            }

            // Add contextual quick replies for continued interaction
            List<ChatResponse.QuickReply> quickReplies = buildContextualQuickReplies(guardrail.safeInput());

            return new ChatResponse(safeOutput, ragResult.citations(), "LOW", false, workflowId, null,
                "This information is for guidance only. Please consult a healthcare professional for medical advice.",
                null, null, quickReplies);

        } catch (Exception e) {
            log.error("Chat sync error: {}", e.getMessage(), e);
            return ChatResponse.simple("I apologize, but I'm experiencing technical difficulties. " +
                "For urgent medical concerns, please call our front desk at (555) 123-4567 or dial 911 for emergencies.");
        }
    }

    /**
     * Asynchronous streaming chat for real-time responses.
     */
    public Flux<String> chatStream(String message) {
        log.info("Stream chat request: {}", message.substring(0, Math.min(message.length(), 100)));
        String workflowId = auditService.generateWorkflowId();

        // Guardrail check
        GuardrailPipeline.GuardrailResult guardrail = guardrailPipeline.processInput(message, workflowId);
        if (!guardrail.allowed()) {
            return Flux.just(guardrail.blockMessage());
        }

        // RAG retrieval
        HealthcareRagService.RagResult ragResult = ragService.simpleRetrieve(guardrail.safeInput());

        String contextPrompt = HEALTHCARE_SYSTEM_PROMPT;
        if (ragResult.hasContext()) {
            contextPrompt += "\n\nHOSPITAL KNOWLEDGE BASE CONTEXT:\n" + ragResult.context();
        }

        try {
            // Stream tokens directly to the client for real-time experience.
            // Input guardrails already applied above; output guardrails run
            // asynchronously after the stream completes for audit purposes.
            StringBuilder fullResponse = new StringBuilder();

            return chatClient.prompt()
                .system(contextPrompt)
                .user(guardrail.safeInput())
                .stream()
                .content()
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> {
                    // Post-stream: apply output guardrails for audit/logging
                    try {
                        guardrailPipeline.processOutput(fullResponse.toString(), workflowId);
                    } catch (Exception ex) {
                        log.warn("Post-stream output guardrail error: {}", ex.getMessage());
                    }
                });
        } catch (Exception e) {
            log.error("Chat stream error: {}", e.getMessage(), e);
            return Flux.just("I apologize, but I'm experiencing technical difficulties. Please try again.");
        }
    }

    /**
     * Agentic chat with full orchestrator pipeline.
     */
    public ChatResponse agentChat(ChatRequest request) {
        log.info("Agent chat request: {}", request.message().substring(0, Math.min(request.message().length(), 100)));
        return orchestrator.process(request.message(), request.department(), request.insurancePlan(),
                request.facility(), request.formattedHistory());
    }

    /**
     * Build contextual quick-reply suggestions based on the user's query.
     */
    private List<ChatResponse.QuickReply> buildContextualQuickReplies(String input) {
        String lower = input.toLowerCase();

        // Symptom-related
        if (lower.matches(".*(pain|ache|fever|cough|nausea|dizzy|breath|symptom|sick|hurt|bleed|swell|itch|rash).*")) {
            return List.of(
                new ChatResponse.QuickReply("📅 Book Appointment", "I want to schedule an appointment"),
                new ChatResponse.QuickReply("👨‍⚕️ Find Doctors", "Show me available doctors"),
                new ChatResponse.QuickReply("💰 Insurance Coverage", "What does my insurance cover for this?")
            );
        }

        // Insurance-related
        if (lower.matches(".*(insurance|coverage|copay|deductible|premium|plan|claim).*")) {
            return List.of(
                new ChatResponse.QuickReply("💰 Another Insurance Question", "I have another insurance question"),
                new ChatResponse.QuickReply("📅 Book Appointment", "I want to schedule an appointment"),
                new ChatResponse.QuickReply("👨‍⚕️ Find Doctors", "Show me available doctors")
            );
        }

        // Appointment-related
        if (lower.matches(".*(appointment|schedule|book|reschedule|visit).*")) {
            return List.of(
                new ChatResponse.QuickReply("📋 My Appointments", "Show my scheduled appointments"),
                new ChatResponse.QuickReply("👨‍⚕️ Find Doctors", "Show me available doctors"),
                new ChatResponse.QuickReply("💰 Insurance Help", "What does my insurance cover?")
            );
        }

        // Doctor-related
        if (lower.matches(".*(doctor|specialist|available|department).*")) {
            return List.of(
                new ChatResponse.QuickReply("📅 Book Appointment", "I want to schedule an appointment"),
                new ChatResponse.QuickReply("🏥 Hospital Info", "Tell me about the hospital"),
                new ChatResponse.QuickReply("💰 Insurance Help", "What does my insurance cover?")
            );
        }

        // Default follow-ups
        return List.of(
            new ChatResponse.QuickReply("🩺 Check Symptoms", "I want to report my symptoms"),
            new ChatResponse.QuickReply("📅 Book Appointment", "I want to schedule an appointment"),
            new ChatResponse.QuickReply("👨‍⚕️ Find Doctors", "Show me available doctors"),
            new ChatResponse.QuickReply("🏥 Hospital Info", "Tell me about the hospital")
        );
    }
}
