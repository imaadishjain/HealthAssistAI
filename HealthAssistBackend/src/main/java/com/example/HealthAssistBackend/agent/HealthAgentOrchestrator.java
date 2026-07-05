package com.example.HealthAssistBackend.agent;

import com.example.HealthAssistBackend.guardrail.GuardrailPipeline;
import com.example.HealthAssistBackend.guardrail.RiskClassifierService;
import com.example.HealthAssistBackend.model.Appointment;
import com.example.HealthAssistBackend.model.ChatResponse;
import com.example.HealthAssistBackend.model.ChatResponse.FormField;
import com.example.HealthAssistBackend.model.Doctor;
import com.example.HealthAssistBackend.model.RiskTier;
import com.example.HealthAssistBackend.model.Ticket;
import com.example.HealthAssistBackend.rag.HealthcareRagService;
import com.example.HealthAssistBackend.service.AppointmentService;
import com.example.HealthAssistBackend.service.AuditService;
import com.example.HealthAssistBackend.service.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Health Agent Orchestrator — Routes queries to the appropriate agent based on intent detection.
 * Implements the Orchestrator-Worker pattern with routing, chaining, and evaluation.
 */
@Component
public class HealthAgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(HealthAgentOrchestrator.class);

    private final ChatClient chatClient;
    private final GuardrailPipeline guardrailPipeline;
    private final SymptomCheckerAgent symptomCheckerAgent;
    private final EmergencyRoutingAgent emergencyRoutingAgent;
    private final InsuranceQueryAgent insuranceQueryAgent;
    private final EquipmentFaultAgent equipmentFaultAgent;
    private final HealthcareRagService ragService;
    private final AuditService auditService;
    private final AppointmentService appointmentService;
    private final TicketService ticketService;

    public HealthAgentOrchestrator(ChatClient chatClient,
                                    GuardrailPipeline guardrailPipeline,
                                    SymptomCheckerAgent symptomCheckerAgent,
                                    EmergencyRoutingAgent emergencyRoutingAgent,
                                    InsuranceQueryAgent insuranceQueryAgent,
                                    EquipmentFaultAgent equipmentFaultAgent,
                                    HealthcareRagService ragService,
                                    AuditService auditService,
                                    AppointmentService appointmentService,
                                    TicketService ticketService) {
        this.chatClient = chatClient;
        this.guardrailPipeline = guardrailPipeline;
        this.symptomCheckerAgent = symptomCheckerAgent;
        this.emergencyRoutingAgent = emergencyRoutingAgent;
        this.insuranceQueryAgent = insuranceQueryAgent;
        this.equipmentFaultAgent = equipmentFaultAgent;
        this.ragService = ragService;
        this.auditService = auditService;
        this.appointmentService = appointmentService;
        this.ticketService = ticketService;
    }

    /**
     * Process a healthcare query through the full orchestrator pipeline.
     */
    public ChatResponse process(String userMessage, String department, String insurancePlan, String facility,
                                String conversationHistory) {
        String workflowId = auditService.generateWorkflowId();
        long start = System.currentTimeMillis();
        log.info("[{}] Orchestrator processing query", workflowId);

        // ── Handle form submissions (structured data from frontend forms) ──
        if (userMessage.startsWith("[FORM_SUBMIT:")) {
            return handleFormSubmission(userMessage, department, workflowId);
        }

        // Step 1: Guardrail pipeline — moderation, PII redaction, risk classification
        GuardrailPipeline.GuardrailResult guardrail = guardrailPipeline.processInput(userMessage, workflowId);

        if (!guardrail.allowed()) {
            return ChatResponse.simple(guardrail.blockMessage());
        }

        String safeInput = guardrail.safeInput();
        RiskTier riskTier = guardrail.riskTier();

        // Redact PII from conversation history so the LLM never sees raw phone numbers, etc.
        String safeHistory = guardrailPipeline.redactText(conversationHistory);

        // Step 2: Check for emergency — escalate immediately if HIGH/CRITICAL
        // BUT if the user is asking about which department to visit, route to symptom checker instead
        if (guardrail.escalationRequired()) {
            // Check if this is a department-inquiry (e.g., "chest pain which department should I go?")
            RiskClassifierService riskService = guardrailPipeline.getRiskClassifierService();
            if (riskService != null && riskService.isDepartmentInquiry(safeInput)) {
                log.info("[{}] Department inquiry detected with serious symptoms — routing to SymptomChecker instead of EmergencyAgent", workflowId);
                auditService.logSimple(workflowId, 1, "DEPARTMENT_INQUIRY_REROUTE", "Orchestrator",
                        safeInput, "Serious symptoms in department inquiry — routed to SymptomChecker");
                return handleDepartmentRecommendation(safeInput, workflowId);
            }
            EmergencyRoutingAgent.EmergencyResult emergency = emergencyRoutingAgent.execute(safeInput, workflowId);
            String safeOutput = guardrailPipeline.processOutput(emergency.response(), workflowId);
            return ChatResponse.escalated(safeOutput, emergency.riskTier().name(), workflowId);
        }

        // Step 3: Route to appropriate agent based on intent (history-aware)
        QueryIntent intent = classifyIntent(safeInput, safeHistory);
        auditService.logSimple(workflowId, 1, "INTENT_CLASSIFIED", "Orchestrator",
                safeInput, "Intent: " + intent.name());

        ChatResponse response = switch (intent) {
            case GREETING -> handleGreeting(safeInput, workflowId);
            case FOLLOW_UP -> handleFollowUp(safeInput, workflowId, safeHistory);
            case SYMPTOM_TRIAGE -> promptSymptomForm(workflowId);
            case INSURANCE_QUERY -> promptInsuranceForm(workflowId);
            case EQUIPMENT_FACILITY -> promptEquipmentForm(workflowId);
            case APPOINTMENT_SCHEDULING -> promptAppointmentForm(workflowId);
            case APPOINTMENT_LOOKUP -> handleAppointmentLookup(safeInput, workflowId);
            case APPOINTMENT_CANCEL -> handleAppointmentCancelFlow(safeInput, workflowId);
            case TICKET_LOOKUP -> handleTicketLookup(safeInput, workflowId);
            case DOCTOR_AVAILABILITY -> handleDoctorAvailability(safeInput, department, workflowId);
            case INFORMATIONAL_QA -> handleInformationalQuery(safeInput, department, facility, workflowId, safeHistory);
        };

        long elapsed = System.currentTimeMillis() - start;
        auditService.logStep(workflowId, 99, "ORCHESTRATOR_COMPLETE", "Orchestrator",
                "[REDACTED]", "Completed in " + elapsed + "ms", null, null,
                riskTier.name(), false, guardrail.piiDetected(), false, elapsed);

        return response;
    }

    /* ══════════════════════  CONVERSATIONAL HANDLERS  ══════════════════════ */

    /**
     * Handle department recommendation queries where the user has serious symptoms
     * but is specifically asking which department to visit.
     * Uses SymptomCheckerAgent for assessment but adds department-focused framing
     * and proactively offers appointment booking.
     */
    private ChatResponse handleDepartmentRecommendation(String input, String workflowId) {
        // Use SymptomCheckerAgent for assessment with department focus
        SymptomCheckerAgent.AgentResult result = symptomCheckerAgent.execute(input, workflowId);
        String assessment = result.response();

        // Determine the recommended department from the symptoms
        String department = detectDepartmentFromSymptoms(input);

        // Build a department-focused response with appointment offer
        StringBuilder response = new StringBuilder();
        response.append(assessment);

        // Ensure the response includes department recommendation and appointment offer
        if (!assessment.toLowerCase().contains("appointment") || !assessment.toLowerCase().contains(department.toLowerCase())) {
            response.append("\n\n📋 **Recommended Department:** **").append(department).append("**\n");
            response.append("\nWould you like me to **book an appointment** with a ").append(department).append(" specialist? ");
            response.append("I can check available doctors and help you schedule right away.");
        }

        String safeOutput = guardrailPipeline.processOutput(response.toString(), workflowId);

        return new ChatResponse(safeOutput, result.citations(), result.riskTier().name(), false, workflowId, department,
            "⚠️ This is not a medical diagnosis. If your symptoms are severe or worsening, please call 911 or visit the ER immediately.",
            null, null,
            List.of(
                new ChatResponse.QuickReply("📅 Book " + department + " Appointment", "I want to schedule an appointment with " + department),
                new ChatResponse.QuickReply("👨\u200d⚕️ View " + department + " Doctors", "Show me available doctors in " + department),
                new ChatResponse.QuickReply("🚑 It's an Emergency", "I have a medical emergency"),
                new ChatResponse.QuickReply("🩺 More Symptoms", "I have additional symptoms to report")
            ));
    }

    /**
     * Detect the most appropriate department from the symptom description.
     */
    private String detectDepartmentFromSymptoms(String input) {
        if (input == null) return "General Medicine";
        String lower = input.toLowerCase();
        if (lower.contains("chest") || lower.contains("heart") || lower.contains("cardiac") || lower.contains("palpitation"))
            return "Cardiology";
        if (lower.contains("head") || lower.contains("neuro") || lower.contains("seizure") || lower.contains("migraine") || lower.contains("stroke"))
            return "Neurology";
        if (lower.contains("bone") || lower.contains("joint") || lower.contains("fracture") || lower.contains("sprain"))
            return "Orthopedics";
        if (lower.contains("breath") || lower.contains("lung") || lower.contains("cough") || lower.contains("asthma"))
            return "Pulmonology";
        if (lower.contains("stomach") || lower.contains("digest") || lower.contains("abdomen") || lower.contains("gastric"))
            return "Gastroenterology";
        if (lower.contains("skin") || lower.contains("rash") || lower.contains("itch"))
            return "Dermatology";
        if (lower.contains("mental") || lower.contains("anxiety") || lower.contains("depression"))
            return "Psychiatry";
        if (lower.contains("eye") || lower.contains("vision"))
            return "Ophthalmology";
        if (lower.contains("ear") || lower.contains("nose") || lower.contains("throat") || lower.contains("sinus"))
            return "ENT";
        if (lower.contains("pregnan") || lower.contains("gynec"))
            return "Gynecology";
        return "General Medicine";
    }

    /**
     * Handle greetings, thanks, and farewells with a warm, conversational response.
     */
    private ChatResponse handleGreeting(String input, String workflowId) {
        String lower = input.toLowerCase().trim();
        String greeting;

        if (lower.matches(".*(thank|thanks|thx).*")) {
            greeting = "You're very welcome! 😊 I'm glad I could help. Is there anything else I can assist you with today?";
        } else if (lower.matches(".*(bye|goodbye|see you|take care).*")) {
            greeting = "Goodbye! 👋 Take care and stay healthy. Remember, I'm available 24/7 whenever you need assistance. " +
                       "If you have a medical emergency, please call **911** immediately.";
        } else if (lower.matches("how\\s+are\\s+you.*")) {
            greeting = "I'm doing great, thank you for asking! 😊 I'm here and ready to help you with anything you need. " +
                       "How can I assist you today?";
        } else {
            greeting = "Hello! 👋 Welcome to **HealthAssist AI** at City General Hospital. I'm your virtual healthcare assistant, " +
                       "and I'm here to help you with a wide range of services.\n\n" +
                       "Here's what I can do for you:\n" +
                       "- 🩺 **Symptom Assessment** — Describe your symptoms for triage guidance\n" +
                       "- 👨‍⚕️ **Find Doctors** — Browse available specialists by department\n" +
                       "- 📅 **Appointments** — Book, view, or cancel appointments\n" +
                       "- 💰 **Insurance Help** — Check coverage and benefits\n" +
                       "- 🔧 **Report Issues** — Log facility or equipment problems\n" +
                       "- 🎫 **Track Tickets** — Check the status of reported issues\n" +
                       "- 🏥 **Hospital Info** — Visiting hours, departments, and more\n\n" +
                       "What would you like help with today?";
        }

        auditService.logSimple(workflowId, 1, "GREETING", "Orchestrator", input, "Conversational greeting");

        return new ChatResponse(greeting, List.of(), "LOW", false, workflowId, null, null, null, null,
            List.of(
                new ChatResponse.QuickReply("🩺 Check Symptoms", "I want to report my symptoms"),
                new ChatResponse.QuickReply("📅 Book Appointment", "I want to schedule an appointment"),
                new ChatResponse.QuickReply("👨‍⚕️ Find Doctors", "Show me available doctors"),
                new ChatResponse.QuickReply("💰 Insurance Help", "I have an insurance question"),
                new ChatResponse.QuickReply("🏥 Hospital Info", "Tell me about visiting hours")
            ));
    }

    /**
     * Handle vague or unclear queries — use conversation history for context-aware clarification.
     */
    private ChatResponse handleFollowUp(String input, String workflowId, String conversationHistory) {
        // If we have conversation history, use LLM to generate a context-aware response
        if (conversationHistory != null && !conversationHistory.isBlank()) {
            try {
                String response = chatClient.prompt()
                    .system("""
                        You are HealthAssist AI at City General Hospital. The user sent a vague or follow-up message.
                        Use the CONVERSATION HISTORY to understand what they're referring to and respond helpfully.
                        If you can determine what they need from context, answer directly.
                        If still unclear, ask a specific clarifying question referencing the conversation so far.
                        Be warm, empathetic, and conversational. Keep responses concise.
                        NEVER provide medical diagnoses or prescriptions.
                        IMPORTANT: If the user asks you to show or repeat any personal information such as
                        phone numbers, emails, or IDs, you MUST display them in masked format only
                        (e.g., ******3267). NEVER show full personal information.
                        """ + conversationHistory)
                    .user(input)
                    .call()
                    .content();

                // Apply output guardrails (PII redaction + moderation) on LLM response
                String safeOutput = guardrailPipeline.processOutput(response, workflowId);

                auditService.logSimple(workflowId, 1, "FOLLOW_UP_CONTEXT_AWARE", "Orchestrator", input, "Context-aware follow-up");

                return new ChatResponse(safeOutput, List.of(), "LOW", false, workflowId, null,
                    "This information is for guidance only. Please consult a healthcare professional for medical advice.",
                    null, null,
                    List.of(
                        new ChatResponse.QuickReply("🩺 Check Symptoms", "I want to report my symptoms"),
                        new ChatResponse.QuickReply("📅 Book Appointment", "I want to schedule an appointment"),
                        new ChatResponse.QuickReply("👨‍⚕️ Find Doctors", "Show me available doctors"),
                        new ChatResponse.QuickReply("🏥 Hospital Info", "Tell me about the hospital")
                    ));
            } catch (Exception e) {
                log.warn("[{}] Context-aware follow-up failed, falling back to static: {}", workflowId, e.getMessage());
            }
        }

        // Fallback: static responses for when there's no conversation history
        String lower = input.toLowerCase().trim();
        String response;

        if (lower.matches(".*(not sure|don't know|confused|unsure).*")) {
            response = "No worries at all! 😊 Let me help you figure out what you need. Could you tell me a bit more about what's going on?\n\n" +
                       "For example:\n" +
                       "- Are you **feeling unwell** or have any symptoms?\n" +
                       "- Do you need to **see a doctor** or book an appointment?\n" +
                       "- Do you have a question about **insurance** or billing?\n" +
                       "- Do you need **hospital information** like visiting hours or directions?\n\n" +
                       "Just let me know and I'll guide you through it!";
        } else if (lower.matches(".*(what can you do|what do you do|what.*services|help me|capabilities|features).*")) {
            response = "Great question! Here's everything I can help you with:\n\n" +
                       "🩺 **Symptom Assessment** — Tell me your symptoms and I'll help assess them and recommend the right department\n" +
                       "👨‍⚕️ **Find Doctors** — Search for available doctors by specialty or department\n" +
                       "📅 **Manage Appointments** — Book new appointments, view existing ones, or cancel\n" +
                       "💰 **Insurance Queries** — Check coverage, copays, and plan benefits\n" +
                       "🔧 **Report Issues** — Report broken equipment or facility problems\n" +
                       "🎫 **Track Tickets** — Check the status of issues you've reported\n" +
                       "🏥 **Hospital Information** — Get details about visiting hours, departments, discharge process, and more\n\n" +
                       "What would you like to explore? Just click one of the options below or type your question!";
        } else {
            response = "I'd love to help! 😊 Could you provide a bit more detail about what you need? Here are some things I can assist with:\n\n" +
                       "- **\"I have a headache and fever\"** → I'll assess your symptoms\n" +
                       "- **\"Book an appointment with a cardiologist\"** → I'll help you schedule\n" +
                       "- **\"What does Standard plan cover?\"** → I'll check your insurance\n" +
                       "- **\"What are the visiting hours?\"** → I'll find the information\n\n" +
                       "Try telling me more specifically what you're looking for!";
        }

        auditService.logSimple(workflowId, 1, "FOLLOW_UP_CLARIFICATION", "Orchestrator", input, "Asking for clarification");

        return new ChatResponse(response, List.of(), "LOW", false, workflowId, null, null, null, null,
            List.of(
                new ChatResponse.QuickReply("🩺 Check Symptoms", "I want to report my symptoms"),
                new ChatResponse.QuickReply("📅 Book Appointment", "I want to schedule an appointment"),
                new ChatResponse.QuickReply("👨‍⚕️ Find Doctors", "Show me available doctors"),
                new ChatResponse.QuickReply("💰 Insurance Help", "I have an insurance question"),
                new ChatResponse.QuickReply("🔧 Report Issue", "I want to report a facility issue"),
                new ChatResponse.QuickReply("🏥 Hospital Info", "Tell me about the hospital")
            ));
    }

    /* ══════════════════════  FORM DEFINITIONS  ══════════════════════ */

    private ChatResponse promptAppointmentForm(String workflowId) {
        List<FormField> fields = List.of(
            new FormField("patientName",  "Patient Full Name",  "text",     true,  "e.g. John Doe"),
            new FormField("department",   "Department",         "select",   true,  "Select department",
                List.of("Cardiology","Neurology","Orthopedics","Pulmonology","Gastroenterology",
                        "Pediatrics","Emergency Medicine","Dermatology","Psychiatry","Oncology",
                        "Radiology","Gynecology","ENT","General Medicine")),
            new FormField("doctorName",   "Preferred Doctor",   "text",     false, "e.g. Dr. Smith (optional)"),
            new FormField("date",         "Preferred Date",     "date",     true,  ""),
            new FormField("time",         "Preferred Time",     "time",     true,  ""),
            new FormField("reason",       "Reason for Visit",   "textarea", true,  "Brief description of your concern"),
            new FormField("phone",        "Contact Phone",      "tel",      true,  "e.g. (555) 000-0000"),
            new FormField("email",        "Email Address",      "email",    false, "e.g. john@example.com"),
            new FormField("notes",        "Additional Notes",   "textarea", false, "Any other information (allergies, special needs, etc.)")
        );
        return new ChatResponse(
            "📅 I'd be happy to help you schedule an appointment! Please fill out the form below with your details.",
            List.of(), "LOW", false, workflowId, null, null,
            "APPOINTMENT_FORM", fields,
            List.of(new ChatResponse.QuickReply("👨\u200d⚕️ Find Doctors First", "Find available doctors"),
                    new ChatResponse.QuickReply("❌ Cancel", "Cancel, I don't need an appointment")));
    }

    private ChatResponse promptSymptomForm(String workflowId) {
        List<FormField> fields = List.of(
            new FormField("symptoms",     "Describe Your Symptoms",       "textarea", true,  "e.g. headache, fever, chest tightness"),
            new FormField("duration",     "How Long Have You Had These?", "select",   true,  "Select duration",
                List.of("Less than 1 hour","1–6 hours","6–24 hours","1–3 days","3–7 days","More than a week")),
            new FormField("severity",     "Severity (1–10)",              "select",   true,  "Rate your pain/discomfort",
                List.of("1 - Minimal","2","3","4","5 - Moderate","6","7","8","9","10 - Worst possible")),
            new FormField("location",     "Body Location",               "text",     true,  "e.g. chest, head, lower back"),
            new FormField("age",          "Patient Age",                  "number",   true,  "e.g. 35"),
            new FormField("medications",  "Current Medications",          "textarea", false, "List any medications you're taking"),
            new FormField("allergies",    "Known Allergies",              "textarea", false, "List any allergies"),
            new FormField("conditions",   "Pre-existing Conditions",     "textarea", false, "e.g. diabetes, hypertension")
        );
        return new ChatResponse(
            "🩺 I'll help assess your symptoms. Please provide the details below so I can give you the best guidance.",
            List.of(), "LOW", false, workflowId, null, null,
            "SYMPTOM_FORM", fields,
            List.of(new ChatResponse.QuickReply("🚑 It's an Emergency", "I have a medical emergency"),
                    new ChatResponse.QuickReply("❌ Cancel", "Cancel, I'm fine")));
    }

    private ChatResponse promptEquipmentForm(String workflowId) {
        List<FormField> fields = List.of(
            new FormField("equipmentName", "Equipment / Facility Name",   "text",     true,  "e.g. MRI Machine, Room 204 AC"),
            new FormField("location",      "Location",                    "text",     true,  "e.g. Building A, Floor 3, Room 305"),
            new FormField("issueType",     "Issue Type",                  "select",   true,  "Select type",
                List.of("Broken/Not Working","Malfunction","Maintenance Required","Safety Hazard","Cleaning Required","Other")),
            new FormField("description",   "Describe the Issue",          "textarea", true,  "Detailed description of the problem"),
            new FormField("urgency",       "Urgency Level",              "select",   true,  "How urgent is this?",
                List.of("Low - Can wait","Medium - Needs attention soon","High - Affecting patient care","Critical - Safety risk")),
            new FormField("reporterName",  "Your Name",                   "text",     true,  "e.g. Nurse Jane"),
            new FormField("contactPhone",  "Contact Phone",               "tel",      false, "e.g. (555) 000-0000")
        );
        return new ChatResponse(
            "🔧 I'll help you report this facility/equipment issue. Please fill out the details below.",
            List.of(), "LOW", false, workflowId, null, null,
            "EQUIPMENT_FORM", fields,
            List.of(new ChatResponse.QuickReply("❌ Cancel", "Cancel, never mind")));
    }

    private ChatResponse promptInsuranceForm(String workflowId) {
        List<FormField> fields = List.of(
            new FormField("planType",      "Insurance Plan",             "select",   true,  "Select your plan",
                List.of("Premium","Standard","Basic","Medicare","Medicaid","Other")),
            new FormField("question",      "Your Question",              "textarea", true,  "e.g. Is cardiac MRI covered under my plan?"),
            new FormField("memberId",      "Member / Policy ID",        "text",     false, "e.g. INS-12345 (optional)"),
            new FormField("procedure",     "Procedure / Service",        "text",     false, "e.g. MRI, Blood Test, Surgery"),
            new FormField("patientName",   "Patient Name",               "text",     false, "e.g. John Doe")
        );
        return new ChatResponse(
            "💰 I can help with your insurance question. Please provide the details below.",
            List.of(), "LOW", false, workflowId, null, null,
            "INSURANCE_FORM", fields,
            List.of(new ChatResponse.QuickReply("❌ Cancel", "Cancel, I don't need insurance info")));
    }

    /* ══════════════════════  FORM SUBMISSION HANDLERS  ══════════════════════ */

    private ChatResponse handleFormSubmission(String rawMessage, String department, String workflowId) {
        // Format: [FORM_SUBMIT:FORM_TYPE] key1=value1 | key2=value2 | ...
        try {
            String formType = rawMessage.substring(rawMessage.indexOf(':') + 1, rawMessage.indexOf(']'));
            String data = rawMessage.substring(rawMessage.indexOf(']') + 1).trim();
            log.info("[{}] Form submission: type={}", workflowId, formType);

            return switch (formType) {
                case "APPOINTMENT_FORM" -> handleAppointmentQuery(data, department, workflowId);
                case "CANCEL_FORM"      -> handleAppointmentCancelSubmission(data, workflowId);
                case "SYMPTOM_FORM"     -> handleSymptomTriage(data, workflowId);
                case "EQUIPMENT_FORM"   -> handleEquipmentFault(data, workflowId);
                case "INSURANCE_FORM"   -> handleInsuranceSubmission(data, workflowId);
                default                 -> handleInformationalQuery(data, department, null, workflowId, "");
            };
        } catch (Exception e) {
            log.error("[{}] Form parse error: {}", workflowId, e.getMessage());
            return ChatResponse.simple("Sorry, I had trouble processing your form. Please try again.");
        }
    }

    private ChatResponse handleInsuranceSubmission(String data, String workflowId) {
        // Extract plan from form data for the insurance agent
        String plan = null;
        for (String pair : data.split("\\|")) {
            String trimmed = pair.trim();
            if (trimmed.startsWith("planType=")) {
                plan = trimmed.substring("planType=".length()).trim();
            }
        }
        return handleInsuranceQuery(data, plan, workflowId);
    }

    /**
     * Classify the intent of the user's query using LLM-based routing.
     * Uses conversation history when available for context-aware classification.
     */
    private QueryIntent classifyIntent(String query, String conversationHistory) {
        // ── Fast rule-based pre-check for unambiguous lookup patterns ──
        // This runs BEFORE the LLM to avoid misclassification of clear lookup requests
        QueryIntent quickMatch = classifyLookupQuick(query);
        if (quickMatch != null) {
            log.info("Intent resolved by quick rule-match: {}", quickMatch);
            return quickMatch;
        }

        try {
            String systemPrompt = """
                    Classify the following healthcare query into exactly ONE category.
                    Respond with ONLY the category name, nothing else.
                    Categories:
                    - GREETING: Greetings, salutations, pleasantries, thanks, farewell. Example: "hi", "hello", "good morning", "thanks", "bye", "how are you"
                    - FOLLOW_UP: Vague, unclear, or ambiguous requests that need clarification, including generic cancel/stop/dismiss messages that don't specify WHAT to cancel. Example: "help", "I need something", "what can you do", "I'm not sure", "tell me more", "cancel it", "stop", "never mind", "forget it", "no thanks", "skip"
                    - SYMPTOM_TRIAGE: Patient describing or reporting their OWN specific symptoms, pain, health complaints, feeling unwell with details. Example: "I have a headache", "my chest hurts", "I feel dizzy"
                    - INFORMATIONAL_QA: General knowledge questions about diseases, conditions, symptoms of a disease, hospital info, visiting hours, department locations, discharge, navigation. Example: "What are the symptoms of chickenpox?", "Tell me about diabetes", "What causes flu?", "symptoms of malaria"
                    - INSURANCE_QUERY: Questions about insurance coverage, eligibility, costs, copays, preauthorization
                    - EQUIPMENT_FACILITY: Reports about broken equipment, facility issues, room problems, maintenance
                    - APPOINTMENT_SCHEDULING: Requests to schedule, reschedule, book, or create NEW appointments
                    - APPOINTMENT_LOOKUP: Viewing, checking, listing, or fetching EXISTING appointments that are already scheduled. Example: "show my appointments", "list scheduled appointments", "check my bookings"
                    - APPOINTMENT_CANCEL: Requests to cancel, withdraw, or revoke an existing appointment — MUST explicitly mention "appointment", "booking", or "visit". Example: "cancel my appointment", "cancel appointment #5", "I want to cancel my booking". NOTE: Vague statements like "cancel it", "cancel", "stop" without mentioning an appointment should be FOLLOW_UP, not APPOINTMENT_CANCEL.
                    - TICKET_LOOKUP: Viewing, checking, listing, or fetching EXISTING tickets, reported issues, or complaints. Example: "show my tickets", "check reported issues", "list open tickets"
                    - DOCTOR_AVAILABILITY: Finding available doctors, checking which doctors are available, looking for doctors by department or specialty
                    - INFORMATIONAL_QA: General hospital info, visiting hours, department locations, discharge, navigation

                    IMPORTANT RULES:
                    1. Use the conversation history (if provided) to understand ambiguous or short messages.
                       For example, if the patient previously asked about doctors and now says "yes" or "sure", classify based on what would logically follow.
                    2. Short, vague messages like "cancel it", "stop", "never mind", "forget it", "no" should be classified as FOLLOW_UP
                       unless the conversation history CLEARLY shows the user was in the middle of a specific action (e.g., booking an appointment).
                    3. APPOINTMENT_CANCEL requires the user to explicitly mention "appointment", "booking", or "visit". Do NOT classify generic
                       cancel/stop messages as APPOINTMENT_CANCEL.
                    4. When in doubt between an action intent and FOLLOW_UP, prefer FOLLOW_UP — it's better to ask for clarification than to take the wrong action.
                    """;

            // Append conversation history for context-aware classification
            if (conversationHistory != null && !conversationHistory.isBlank()) {
                systemPrompt += conversationHistory;
            }

            String classification = chatClient.prompt()
                .system(systemPrompt)
                .user(query)
                .call()
                .content();

            if (classification != null) {
                String trimmed = classification.trim().toUpperCase().replace(" ", "_");
                try {
                    return QueryIntent.valueOf(trimmed);
                } catch (IllegalArgumentException e) {
                    // Try partial match
                    for (QueryIntent intent : QueryIntent.values()) {
                        if (trimmed.contains(intent.name())) return intent;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Intent classification failed, using rule-based fallback: {}", e.getMessage());
        }

        // Rule-based fallback
        return classifyIntentRuleBased(query);
    }

    /**
     * Quick rule-based pre-check specifically for appointment/ticket LOOKUP patterns.
     * Returns null if no confident match — falls through to LLM classification.
     */
    private QueryIntent classifyLookupQuick(String query) {
        String lower = query.toLowerCase().trim();

        // Greetings — fast path for common greetings
        if (lower.matches("(hi|hello|hey|good\\s*(morning|afternoon|evening|day))\\s*!?$")
                || lower.matches("(thanks|thank you|bye|goodbye|see you)\\s*!?$")
                || lower.matches("how\\s+are\\s+you\\??$"))
            return QueryIntent.GREETING;

        // Vague queries — short or unclear
        if (lower.matches("(help|help me|what can you do|menu|options|i need help)\\s*\\??$")
                || lower.length() <= 3)
            return QueryIntent.FOLLOW_UP;

        // Vague cancel/stop/nevermind WITHOUT a specific entity → clarify, not act
        // e.g. "cancel it", "stop", "never mind", "forget it"
        if (lower.matches("(cancel|cancel it|stop|stop it|never\\s*mind|forget it|nvm|nah|no thanks|skip|skip it|leave it|drop it)\\s*\\.?!?$")) {
            return QueryIntent.FOLLOW_UP;
        }

        // Appointment CANCEL: "cancel my appointment", "cancel appointment #5", "I want to cancel an appointment"
        if (lower.matches(".*(cancel|withdraw|revoke|drop).*\\b(appointment|booking|visit)s?\\b.*")
                || lower.matches(".*\\b(appointment|booking|visit)s?\\b.*(cancel|withdraw|revoke|drop).*")) {
            return QueryIntent.APPOINTMENT_CANCEL;
        }

        // Appointment lookup: "show my appointments", "view scheduled appointments", "check my bookings", "list appointments"
        if (lower.matches(".*(show|view|list|check|fetch|see|get|display|what are).*(my|all|existing|scheduled|upcoming|current|booked).*\\b(appointment|booking|visit)s?\\b.*")
                || lower.matches(".*\\b(my|all|existing|scheduled|upcoming|current|booked)\\b.*(appointment|booking|visit)s?\\b.*")
                || lower.matches(".*\\b(appointment|booking|visit)s?\\b.*(status|detail|list|history).*")) {
            // Exclude explicit creation verbs — "schedule an appointment", "book a new appointment"
            if (!lower.matches(".*(schedule|book|create|make|set up|arrange)\\s+(a |an |new |my )?(appointment|booking|visit).*")) {
                return QueryIntent.APPOINTMENT_LOOKUP;
            }
        }

        // Ticket lookup: "show my tickets", "view reported issues", "check ticket status", "list open tickets"
        if (lower.matches(".*(show|view|list|check|fetch|see|get|display|what are).*(my|all|existing|open|reported|current).*\\b(ticket|issue|complaint|report)s?\\b.*")
                || lower.matches(".*\\b(my|all|existing|open|reported|current)\\b.*(ticket|issue|complaint|report)s?\\b.*")
                || lower.matches(".*\\b(ticket|issue|complaint)s?\\b.*(status|detail|list|history).*")) {
            // Exclude explicit creation verbs — "report a new issue", "create a ticket"
            if (!lower.matches(".*(report|create|submit|file|raise|log)\\s+(a |an |new )?(ticket|issue|complaint|problem).*")) {
                return QueryIntent.TICKET_LOOKUP;
            }
        }

        return null; // no confident match — let LLM decide
    }

    /**
     * Rule-based intent classification fallback.
     */
    private QueryIntent classifyIntentRuleBased(String query) {
        String lower = query.toLowerCase().trim();

        // Greetings / pleasantries / thanks / farewell
        if (lower.matches("(hi|hello|hey|good\\s*(morning|afternoon|evening|day)|howdy|greetings|yo|sup)\\b.*")
                || lower.matches(".*(thank|thanks|thx|bye|goodbye|see you|take care|cheers).*")
                || lower.matches("how\\s+are\\s+you.*"))
            return QueryIntent.GREETING;

        // Vague / unclear requests needing clarification
        if (lower.matches("(help|help me|i need help|what can you do|what do you do|i('m| am) not sure|tell me more|options|menu)")
                || lower.matches("(i need|i want|can you|please)\\s*$")
                || lower.matches(".*what\\s+(all|services|things)\\s+(can|do)\\s+you.*")
                || lower.length() <= 4)
            return QueryIntent.FOLLOW_UP;

        // Informational questions ABOUT symptoms/diseases — NOT the user reporting their own symptoms
        // e.g. "what are the symptoms of chickenpox", "symptoms of malaria", "tell me about diabetes"
        if (lower.matches(".*(what|which|tell|list|describe|explain|causes?|signs?)\\b.*(symptom|disease|condition|illness|infection).*")
                || lower.matches(".*(symptom|sign|cause)s?\\s+(of|for|in|associated|related)\\s+.*")
                || lower.matches(".*(what\\s+is|what\\s+are|tell me about|information about|info on|know about)\\s+.*"))
            return QueryIntent.INFORMATIONAL_QA;

        if (lower.matches(".*(pain|ache|fever|cough|nausea|dizzy|breath|symptom|sick|hurt|bleed|swell|itch|rash).*"))
            return QueryIntent.SYMPTOM_TRIAGE;
        if (lower.matches(".*(insurance|coverage|copay|deductible|eligib|preauth|claim|premium|plan).*"))
            return QueryIntent.INSURANCE_QUERY;
        if (lower.matches(".*(broken|malfunction|not working|equipment|machine|device|repair|maintenance|facility|room issue).*"))
            return QueryIntent.EQUIPMENT_FACILITY;
        if (lower.matches(".*(find doctor|available doctor|doctor available|which doctor|list doctor|show doctor|doctor list|doctors in|doctor.*department|department.*doctor).*"))
            return QueryIntent.DOCTOR_AVAILABILITY;

        // Lookup intents BEFORE scheduling — "show my appointments" vs "book an appointment"
        if (lower.matches(".*(show|view|list|check|fetch|see|get|display|my|existing|scheduled|upcoming).*(appointment|booking|visit).*")
                || lower.matches(".*(appointment|booking|visit).*(show|view|list|check|fetch|see|get|display|status|detail).*"))
            return QueryIntent.APPOINTMENT_LOOKUP;
        if (lower.matches(".*(show|view|list|check|fetch|see|get|display|my|existing|open|reported).*(ticket|issue|complaint|report).*")
                || lower.matches(".*(ticket|issue|complaint|report).*(show|view|list|check|fetch|see|get|display|status|detail).*"))
            return QueryIntent.TICKET_LOOKUP;

        // Vague cancel/stop/nevermind without specific entity → clarify
        if (lower.matches("(cancel|cancel it|stop|stop it|never\\s*mind|forget it|nvm|nah|no thanks|skip|skip it|leave it|drop it)\\s*\\.?!?$"))
            return QueryIntent.FOLLOW_UP;

        if (lower.matches(".*(cancel|withdraw|revoke|drop).*(appointment|booking|visit).*")
                || lower.matches(".*(appointment|booking|visit).*(cancel|withdraw|revoke|drop).*"))
            return QueryIntent.APPOINTMENT_CANCEL;

        if (lower.matches(".*(appointment|schedule|book|reschedule|follow.?up).*"))
            return QueryIntent.APPOINTMENT_SCHEDULING;

        return QueryIntent.INFORMATIONAL_QA;
    }

    /**
     * Handle symptom/triage queries.
     * Detects the recommended department from the symptom data and provides
     * a department-specific "Schedule Appointment" quick reply.
     */
    private ChatResponse handleSymptomTriage(String input, String workflowId) {
        SymptomCheckerAgent.AgentResult result = symptomCheckerAgent.execute(input, workflowId);
        String safeOutput = guardrailPipeline.processOutput(result.response(), workflowId);

        // Detect the recommended department from the symptom description
        String department = detectDepartmentFromSymptoms(input);

        if (result.escalated()) {
            // Escalated: show emergency options AND a department-specific scheduling option
            return new ChatResponse(safeOutput, result.citations(), result.riskTier().name(), true, workflowId, department,
                "⚠️ HIGH RISK DETECTED: This case has been escalated to a medical professional for immediate review.",
                null, null,
                List.of(
                    new ChatResponse.QuickReply("🚑 Call 911", "Call 911 emergency"),
                    new ChatResponse.QuickReply("🏥 Go to ER", "Where is the emergency room?"),
                    new ChatResponse.QuickReply("📅 Schedule " + department + " Appointment",
                        "I want to schedule an appointment with " + department)
                ));
        }
        // Non-escalated: provide department-specific quick replies
        return new ChatResponse(safeOutput, result.citations(), "LOW", false, workflowId, department,
            "This information is for guidance only. Please consult a healthcare professional for medical advice.",
            null, null,
            List.of(
                new ChatResponse.QuickReply("📅 Schedule " + department + " Appointment",
                    "I want to schedule an appointment with " + department),
                new ChatResponse.QuickReply("👨‍⚕️ View " + department + " Doctors",
                    "Show me available doctors in " + department),
                new ChatResponse.QuickReply("🩺 More Symptoms", "I have additional symptoms to report"),
                new ChatResponse.QuickReply("💰 Insurance Coverage", "What does my insurance cover for this?")
            ));
    }

    /**
     * Handle insurance queries.
     */
    private ChatResponse handleInsuranceQuery(String input, String insurancePlan, String workflowId) {
        InsuranceQueryAgent.InsuranceResult result = insuranceQueryAgent.execute(input, insurancePlan, workflowId);
        String safeOutput = guardrailPipeline.processOutput(result.response(), workflowId);
        return new ChatResponse(safeOutput, result.citations(), "LOW", false, workflowId, null,
            "This information is for guidance only. Please consult a healthcare professional for medical advice.",
            null, null,
            List.of(
                new ChatResponse.QuickReply("💰 Another Insurance Question", "I have another insurance question"),
                new ChatResponse.QuickReply("📅 Book Appointment", "I want to schedule an appointment"),
                new ChatResponse.QuickReply("👨‍⚕️ Find Doctors", "Show me available doctors"),
                new ChatResponse.QuickReply("🏥 Hospital Info", "Tell me about the hospital")
            ));
    }

    /**
     * Handle equipment/facility fault reports.
     */
    private ChatResponse handleEquipmentFault(String input, String workflowId) {
        EquipmentFaultAgent.EquipmentResult result = equipmentFaultAgent.execute(input, workflowId);
        String safeOutput = guardrailPipeline.processOutput(result.response(), workflowId);
        return new ChatResponse(safeOutput, result.citations(), "LOW", false, workflowId, null,
            "Your report has been logged. For urgent safety issues, please contact facilities directly.",
            null, null,
            List.of(
                new ChatResponse.QuickReply("🔧 Report Another Issue", "I want to report another facility issue"),
                new ChatResponse.QuickReply("🎫 View My Tickets", "Show my reported tickets"),
                new ChatResponse.QuickReply("🏥 Hospital Info", "Tell me about the hospital")
            ));
    }

    /**
     * Handle appointment scheduling — directly books using parsed form data.
     */
    private ChatResponse handleAppointmentQuery(String data, String department, String workflowId) {
        auditService.logSimple(workflowId, 1, "APPOINTMENT_BOOKING", "Orchestrator", "[FORM_DATA]", "Creating appointment from form");

        try {
            Map<String, String> form = parseFormData(data);

            String patientName = form.getOrDefault("patientName", "Unknown Patient");
            String dept        = form.getOrDefault("department", department != null ? department : "General Medicine");
            String date         = form.getOrDefault("date", "");
            String time         = form.getOrDefault("time", "09:00");
            String reason       = form.getOrDefault("reason", "General consultation");
            String phone        = form.getOrDefault("phone", "");
            String email        = form.getOrDefault("email", "");

            // Combine date + time into LocalDateTime
            LocalDateTime dateTime;
            try {
                dateTime = LocalDateTime.parse(date + "T" + time);
            } catch (Exception e) {
                dateTime = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
            }

            // Create appointment directly through the service (no LLM round-trip)
            Appointment apt = appointmentService.createAppointment(
                patientName, email, phone, null, dept, dateTime, reason);

            String formattedDate = apt.getAppointmentDateTime()
                .format(DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a"));

            String confirmation = String.format("""
                ✅ **Appointment Successfully Scheduled!**

                📋 **Appointment Details:**
                - **Appointment ID:** #%d
                - **Patient:** %s
                - **Department:** %s
                - **Doctor:** %s
                - **Date & Time:** %s
                - **Reason:** %s

                📌 **Reminders:**
                - Please arrive **15 minutes early**
                - Bring your **insurance card** and **photo ID**
                - Contact us at **(555) 123-4567** if you need to reschedule or cancel

                Is there anything else I can help you with?""",
                apt.getId(), patientName, dept, apt.getDoctorName(), formattedDate, reason);

            String safeOutput = guardrailPipeline.processOutput(confirmation, workflowId);
            return new ChatResponse(safeOutput, List.of(), "LOW", false, workflowId, null,
                "Appointment information is subject to availability. Please confirm with the front desk.",
                null, null,
                List.of(new ChatResponse.QuickReply("📅 Schedule Another", "I want to schedule another appointment"),
                        new ChatResponse.QuickReply("👨\u200d⚕️ View Doctors", "Find available doctors"),
                        new ChatResponse.QuickReply("💰 Insurance Info", "What does my insurance cover?")));

        } catch (Exception e) {
            log.error("[{}] Appointment booking error: {}", workflowId, e.getMessage(), e);
            return ChatResponse.simple(
                "I apologize, but I couldn't create your appointment. Please contact our scheduling desk at **(555) 123-4567**.");
        }
    }

    /**
     * Parse "key=value | key=value | ..." form data into a Map.
     */
    private Map<String, String> parseFormData(String data) {
        Map<String, String> result = new HashMap<>();
        for (String pair : data.split("\\|")) {
            String trimmed = pair.trim();
            int eq = trimmed.indexOf('=');
            if (eq > 0) {
                result.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
            }
        }
        return result;
    }

    /**
     * Handle doctor availability queries — directly queries the DB.
     */
    private ChatResponse handleDoctorAvailability(String input, String department, String workflowId) {
        auditService.logSimple(workflowId, 1, "DOCTOR_AVAILABILITY", "Orchestrator", input, "Querying available doctors");

        try {
            List<Doctor> doctors;
            String lower = input.toLowerCase();

            // Try to detect department from the query — first by explicit department name
            String detectedDept = department;
            if (detectedDept == null || detectedDept.isBlank()) {
                String[] deptNames = {"Cardiology","Neurology","Orthopedics","Pulmonology","Gastroenterology",
                    "Pediatrics","Emergency Medicine","Dermatology","Psychiatry","Oncology",
                    "Radiology","Gynecology","ENT","General Medicine"};
                for (String d : deptNames) {
                    if (lower.contains(d.toLowerCase())) { detectedDept = d; break; }
                }
            }

            // If no explicit department name found, try mapping symptom keywords to a department
            if (detectedDept == null || detectedDept.isBlank()) {
                detectedDept = detectDepartmentFromSymptoms(input);
                if ("General Medicine".equals(detectedDept)) {
                    // detectDepartmentFromSymptoms returns "General Medicine" as a fallback;
                    // only use it if there are actual medical keywords in the query
                    boolean hasMedicalKeywords = lower.matches(
                        ".*(pain|ache|fever|cough|breath|skin|rash|eye|ear|nose|throat|head|chest|heart|stomach|bone|joint|mental|anxiety|pregnan|cancer|tumor).*");
                    if (!hasMedicalKeywords) {
                        detectedDept = null; // no symptom keywords found, don't force a department
                    }
                }
            }

            if (detectedDept != null && !detectedDept.isBlank()) {
                doctors = appointmentService.getDoctorsByDepartment(detectedDept);
            } else {
                doctors = appointmentService.getAllDoctors().stream()
                    .filter(d -> d.getAvailable() != null && d.getAvailable())
                    .toList();
            }

            if (doctors.isEmpty()) {
                String msg = detectedDept != null
                    ? "No available doctors found in **" + detectedDept + "** right now. Please contact our front desk at **(555) 123-4567** for assistance."
                    : "No available doctors found at the moment. Please contact our front desk at **(555) 123-4567**.";
                return new ChatResponse(msg, List.of(), "LOW", false, workflowId, null, null, null, null,
                    List.of(new ChatResponse.QuickReply("📅 Schedule Appointment", "I want to schedule an appointment"),
                            new ChatResponse.QuickReply("🏥 Hospital Info", "Tell me about the hospital")));
            }

            StringBuilder sb = new StringBuilder();
            sb.append("👨\u200d⚕️ **Available Doctors");
            if (detectedDept != null && !detectedDept.isBlank()) sb.append(" — ").append(detectedDept);
            sb.append("**\n\n");

            for (int i = 0; i < doctors.size(); i++) {
                Doctor d = doctors.get(i);
                String displayName = d.getName().startsWith("Dr.") ? d.getName() : "Dr. " + d.getName();
                sb.append(String.format("**%d. %s**\n", i + 1, displayName));
                sb.append(String.format("   - **Department:** %s\n", d.getDepartment()));
                if (d.getSpecialty() != null && !d.getSpecialty().isBlank())
                    sb.append(String.format("   - **Specialty:** %s\n", d.getSpecialty()));
                if (d.getLocation() != null && !d.getLocation().isBlank())
                    sb.append(String.format("   - **Location:** %s\n", d.getLocation()));
                if (d.getNextAvailableSlot() != null)
                    sb.append(String.format("   - **Next Available:** %s\n",
                        d.getNextAvailableSlot().format(DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a"))));
                if (d.getConsultationFee() != null)
                    sb.append(String.format("   - **Consultation Fee:** $%s\n", d.getConsultationFee()));
                sb.append("\n");
            }

            sb.append("Would you like to **schedule an appointment** with any of these doctors?");

            String safeOutput = guardrailPipeline.processOutput(sb.toString(), workflowId);
            return new ChatResponse(safeOutput, List.of(), "LOW", false, workflowId, null,
                "Doctor availability is subject to change. Please confirm with the front desk.",
                null, null,
                List.of(new ChatResponse.QuickReply("📅 Schedule Appointment", "I want to schedule an appointment"),
                        new ChatResponse.QuickReply("🏥 Hospital Info", "Tell me about the hospital"),
                        new ChatResponse.QuickReply("💰 Insurance Query", "What does my insurance cover?")));

        } catch (Exception e) {
            log.error("[{}] Doctor availability error: {}", workflowId, e.getMessage(), e);
            return ChatResponse.simple(
                "I apologize, I couldn't retrieve doctor information. Please contact our front desk at **(555) 123-4567**.");
        }
    }

    /**
     * Handle appointment lookup — fetches existing appointments and displays them.
     */
    private ChatResponse handleAppointmentLookup(String input, String workflowId) {
        auditService.logSimple(workflowId, 1, "APPOINTMENT_LOOKUP", "Orchestrator", input, "Fetching appointments");

        try {
            // 1. Try to extract an appointment ID first (e.g., "fetch appointment #1")
            Long appointmentId = extractAppointmentIdFromQuery(input);
            // Also check for bare "#<number>" or "id <number>" patterns
            if (appointmentId == null) {
                java.util.regex.Matcher idMatcher = java.util.regex.Pattern.compile(
                    "(?:id|#)\\s*#?(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE
                ).matcher(input);
                if (idMatcher.find()) {
                    try { appointmentId = Long.parseLong(idMatcher.group(1)); } catch (NumberFormatException ignored) {}
                }
            }

            List<Appointment> appointments;

            if (appointmentId != null) {
                // Fetch specific appointment by ID
                appointments = appointmentService.getAppointmentById(appointmentId)
                    .map(List::of).orElse(List.of());
            } else {
                // 2. Try department extraction
                String department = extractDepartmentFromQuery(input);
                // 3. Try date extraction
                java.time.LocalDate dateFilter = extractDateFromQuery(input);
                // 4. Try patient name extraction
                String patientName = extractNameFromQuery(input);
                // 5. Try status extraction
                String statusFilter = extractStatusFromQuery(input);

                if (department != null) {
                    appointments = appointmentService.getAppointmentsByDepartment(department);
                } else if (dateFilter != null) {
                    java.time.LocalDateTime dayStart = dateFilter.atStartOfDay();
                    java.time.LocalDateTime dayEnd = dateFilter.atTime(23, 59, 59);
                    appointments = appointmentService.getAppointmentsByDateRange(dayStart, dayEnd);
                } else if (patientName != null && !patientName.isBlank()) {
                    appointments = appointmentService.getAppointmentsByPatient(patientName);
                } else if (statusFilter != null) {
                    appointments = appointmentService.getAllAppointments().stream()
                        .filter(a -> a.getStatus().equalsIgnoreCase(statusFilter))
                        .toList();
                } else {
                    appointments = appointmentService.getAllAppointments();
                }

                // Apply additional cross-filters
                if (department != null && statusFilter != null) {
                    final String sf = statusFilter;
                    appointments = appointments.stream()
                        .filter(a -> a.getStatus().equalsIgnoreCase(sf)).toList();
                }
                if (dateFilter != null && department != null) {
                    final java.time.LocalDate df = dateFilter;
                    appointments = appointments.stream()
                        .filter(a -> a.getAppointmentDateTime() != null
                            && a.getAppointmentDateTime().toLocalDate().equals(df)).toList();
                }
            }

            // Build a label describing the filter used
            String filterLabel = "All Appointments";
            if (appointmentId != null) {
                filterLabel = "Appointment #" + appointmentId;
            } else {
                String dept = extractDepartmentFromQuery(input);
                java.time.LocalDate dateF = extractDateFromQuery(input);
                String pName = extractNameFromQuery(input);
                if (dept != null) filterLabel = dept + " Department Appointments";
                else if (dateF != null) filterLabel = "Appointments on " + dateF.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"));
                else if (pName != null && !pName.isBlank()) filterLabel = "Appointments for " + pName;
            }

            if (appointments.isEmpty()) {
                String msg = "📅 No " + filterLabel.toLowerCase() + " found. Would you like to schedule a new appointment?";
                return new ChatResponse(msg, List.of(), "LOW", false, workflowId, null, null, null, null,
                    List.of(new ChatResponse.QuickReply("📅 Schedule Appointment", "I want to schedule an appointment"),
                            new ChatResponse.QuickReply("👨‍⚕️ Find Doctors", "Find available doctors")));
            }

            StringBuilder sb = new StringBuilder();
            sb.append("📅 **").append(filterLabel);
            sb.append("** (").append(appointments.size()).append(" found)\n\n");

            for (int i = 0; i < appointments.size(); i++) {
                Appointment a = appointments.get(i);
                String statusIcon = switch (a.getStatus().toUpperCase()) {
                    case "SCHEDULED" -> "🟢";
                    case "COMPLETED" -> "✅";
                    case "CANCELLED" -> "🔴";
                    default -> "⚪";
                };
                sb.append(String.format("**%d. Appointment #%d** %s %s\n", i + 1, a.getId(), statusIcon, a.getStatus()));
                sb.append(String.format("   - **Patient:** %s\n", a.getPatientName()));
                sb.append(String.format("   - **Doctor:** %s\n", a.getDoctorName() != null ? a.getDoctorName() : "To be assigned"));
                sb.append(String.format("   - **Department:** %s\n", a.getDepartment()));
                if (a.getAppointmentDateTime() != null)
                    sb.append(String.format("   - **Date & Time:** %s\n",
                        a.getAppointmentDateTime().format(DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a"))));
                if (a.getReason() != null && !a.getReason().isBlank())
                    sb.append(String.format("   - **Reason:** %s\n", a.getReason()));
                sb.append("\n");
            }

            sb.append("Would you like to **schedule a new appointment**, **cancel an appointment**, or need any other help?");

            String safeOutput = guardrailPipeline.processOutput(sb.toString(), workflowId);
            return new ChatResponse(safeOutput, List.of(), "LOW", false, workflowId, null,
                "Appointment information is fetched from our records. Please verify with the front desk for real-time updates.",
                null, null,
                List.of(new ChatResponse.QuickReply("❌ Cancel Appointment", "I want to cancel an appointment"),
                        new ChatResponse.QuickReply("📅 Schedule Appointment", "I want to schedule an appointment"),
                        new ChatResponse.QuickReply("👨\u200d⚕️ Find Doctors", "Find available doctors")));

        } catch (Exception e) {
            log.error("[{}] Appointment lookup error: {}", workflowId, e.getMessage(), e);
            return ChatResponse.simple(
                "I apologize, I couldn't retrieve appointment information. Please contact our scheduling desk at **(555) 123-4567**.");
        }
    }

    /**
     * Handle appointment cancel flow — shows scheduled appointments with cancel options.
     * If the user specifies an appointment ID directly (e.g., "cancel appointment #5"),
     * it cancels immediately. Otherwise, it prompts a form to pick one.
     */
    private ChatResponse handleAppointmentCancelFlow(String input, String workflowId) {
        auditService.logSimple(workflowId, 1, "APPOINTMENT_CANCEL", "Orchestrator", input, "Cancel appointment flow");

        try {
            // Check if the user already specified an appointment ID
            Long appointmentId = extractAppointmentIdFromQuery(input);

            if (appointmentId != null) {
                // Direct cancellation
                return performAppointmentCancel(appointmentId, workflowId);
            }

            // No specific ID — fetch all SCHEDULED appointments and prompt user to pick one
            List<Appointment> appointments = appointmentService.getAllAppointments().stream()
                .filter(a -> "SCHEDULED".equalsIgnoreCase(a.getStatus()))
                .toList();

            if (appointments.isEmpty()) {
                return new ChatResponse(
                    "📅 There are no **scheduled** appointments to cancel. All appointments are either completed or already cancelled.",
                    List.of(), "LOW", false, workflowId, null, null, null, null,
                    List.of(new ChatResponse.QuickReply("📅 Schedule Appointment", "I want to schedule an appointment"),
                            new ChatResponse.QuickReply("📋 View Appointments", "Show my appointments")));
            }

            // Build options list for the cancel form
            List<String> options = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            sb.append("❌ **Cancel an Appointment**\n\n");
            sb.append("Here are your **scheduled** appointments. Please select the one you want to cancel:\n\n");

            for (Appointment a : appointments) {
                String label = String.format("#%d — %s with Dr. %s (%s)",
                    a.getId(), a.getPatientName(),
                    a.getDoctorName() != null ? a.getDoctorName() : "TBD",
                    a.getAppointmentDateTime() != null
                        ? a.getAppointmentDateTime().format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"))
                        : "Date TBD");
                options.add(label);
                sb.append("• ").append(label).append("\n");
            }
            sb.append("\nSelect an appointment below and confirm cancellation.");

            String safeOutput = guardrailPipeline.processOutput(sb.toString(), workflowId);

            List<ChatResponse.FormField> fields = List.of(
                new ChatResponse.FormField("appointmentSelect", "Select Appointment to Cancel", "select", true,
                    "Choose an appointment...", options),
                new ChatResponse.FormField("confirmCancel", "Type 'YES' to confirm cancellation", "text", true,
                    "YES", null)
            );

            return new ChatResponse(safeOutput, List.of(), "LOW", false, workflowId, null,
                "Cancellation is permanent. Please confirm carefully.",
                "CANCEL_FORM", fields, List.of());

        } catch (Exception e) {
            log.error("[{}] Appointment cancel flow error: {}", workflowId, e.getMessage(), e);
            return ChatResponse.simple(
                "I apologize, I couldn't process the cancellation request. Please contact our scheduling desk at **(555) 123-4567**.");
        }
    }

    /**
     * Handle the CANCEL_FORM submission — extract appointment ID and cancel it.
     */
    private ChatResponse handleAppointmentCancelSubmission(String data, String workflowId) {
        auditService.logSimple(workflowId, 1, "APPOINTMENT_CANCEL_SUBMIT", "Orchestrator", data, "Processing cancellation");

        try {
            Map<String, String> formData = parseFormData(data);
            String selected = formData.getOrDefault("appointmentSelect", "");
            String confirm = formData.getOrDefault("confirmCancel", "").trim();

            if (!"YES".equalsIgnoreCase(confirm)) {
                return new ChatResponse(
                    "⚠️ Cancellation **not confirmed**. You must type **YES** to confirm. Your appointment has not been changed.",
                    List.of(), "LOW", false, workflowId, null, null, null, null,
                    List.of(new ChatResponse.QuickReply("❌ Try Again", "I want to cancel an appointment"),
                            new ChatResponse.QuickReply("📋 View Appointments", "Show my appointments")));
            }

            // Extract ID from the selected option string like "#5 — John Doe with Dr. Smith ..."
            Long appointmentId = null;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("#(\\d+)").matcher(selected);
            if (m.find()) {
                appointmentId = Long.parseLong(m.group(1));
            }

            if (appointmentId == null) {
                return ChatResponse.simple("⚠️ Could not determine which appointment to cancel. Please try again.");
            }

            return performAppointmentCancel(appointmentId, workflowId);

        } catch (Exception e) {
            log.error("[{}] Appointment cancel submission error: {}", workflowId, e.getMessage(), e);
            return ChatResponse.simple(
                "I apologize, I couldn't process the cancellation. Please contact our scheduling desk at **(555) 123-4567**.");
        }
    }

    /**
     * Actually cancel an appointment by ID and return a confirmation response.
     */
    private ChatResponse performAppointmentCancel(Long appointmentId, String workflowId) {
        try {
            var optCancelled = appointmentService.cancelAppointment(appointmentId);

            if (optCancelled.isEmpty()) {
                return ChatResponse.simple(
                    "⚠️ Appointment **#" + appointmentId + "** was not found. Please check the ID and try again.");
            }

            Appointment cancelled = optCancelled.get();
            String msg = String.format(
                "✅ **Appointment #%d has been cancelled successfully!**\n\n" +
                "• **Patient:** %s\n" +
                "• **Doctor:** %s\n" +
                "• **Department:** %s\n" +
                "• **Status:** 🔴 CANCELLED\n\n" +
                "If this was done in error, please contact our scheduling desk at **(555) 123-4567** to reschedule.",
                cancelled.getId(),
                cancelled.getPatientName(),
                cancelled.getDoctorName() != null ? cancelled.getDoctorName() : "N/A",
                cancelled.getDepartment());

            String safeOutput = guardrailPipeline.processOutput(msg, workflowId);
            return new ChatResponse(safeOutput, List.of(), "LOW", false, workflowId, null,
                "This cancellation has been recorded in the system.",
                null, null,
                List.of(new ChatResponse.QuickReply("📅 Schedule New", "I want to schedule an appointment"),
                        new ChatResponse.QuickReply("📋 View Appointments", "Show my appointments"),
                        new ChatResponse.QuickReply("👨\u200d⚕️ Find Doctors", "Find available doctors")));

        } catch (Exception e) {
            log.error("[{}] Failed to cancel appointment #{}: {}", workflowId, appointmentId, e.getMessage(), e);
            return ChatResponse.simple(
                "⚠️ Could not cancel appointment **#" + appointmentId + "**. It may not exist or is already cancelled. " +
                "Please check your appointments or contact the front desk.");
        }
    }

    /**
     * Extract an appointment ID from a user query like "cancel appointment #5" or "cancel appointment 5".
     */
    private Long extractAppointmentIdFromQuery(String query) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "(?:appointment|booking)\\s*#?(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE
        ).matcher(query);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Extract a ticket ID from a user query like "fetch ticket #2" or "ticket id 2".
     */
    private Long extractTicketIdFromQuery(String query) {
        // Match "ticket #2", "ticket 2", "ticket id 2", "#2" with ticket context
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "(?:ticket|issue)\\s*#?(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE
        ).matcher(query);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        // Also check for bare "id #<number>" or "#<number>" patterns
        java.util.regex.Matcher idMatcher = java.util.regex.Pattern.compile(
            "(?:id|#)\\s*#?(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE
        ).matcher(query);
        if (idMatcher.find()) {
            try {
                return Long.parseLong(idMatcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Handle ticket lookup — fetches existing tickets and displays them.
     */
    private ChatResponse handleTicketLookup(String input, String workflowId) {
        auditService.logSimple(workflowId, 1, "TICKET_LOOKUP", "Orchestrator", input, "Fetching tickets");

        try {
            // 1. Try to extract a ticket ID first (e.g., "fetch ticket #1", "ticket id 2")
            Long ticketId = extractTicketIdFromQuery(input);
            List<Ticket> tickets;

            if (ticketId != null) {
                // Fetch specific ticket by ID
                tickets = ticketService.getTicketById(ticketId)
                    .map(List::of).orElse(List.of());
            } else {
                // 2. Try category, priority, reporter, status extraction
                String categoryFilter = extractCategoryFromQuery(input);
                String priorityFilter = extractPriorityFromQuery(input);
                String reporterName = extractNameFromQuery(input);
                String statusFilter = extractStatusFromQuery(input);

                if (categoryFilter != null) {
                    tickets = ticketService.getTicketsByCategory(categoryFilter);
                } else if (priorityFilter != null) {
                    tickets = ticketService.getTicketsByPriority(priorityFilter);
                } else if (reporterName != null && !reporterName.isBlank()) {
                    tickets = ticketService.getAllTickets().stream()
                        .filter(t -> t.getReportedBy() != null && t.getReportedBy().toLowerCase().contains(reporterName.toLowerCase()))
                        .toList();
                } else if (statusFilter != null) {
                    tickets = ticketService.getTicketsByStatus(statusFilter);
                } else {
                    tickets = ticketService.getAllTickets();
                }

                // Apply additional cross-filters
                if (statusFilter != null && categoryFilter != null) {
                    final String sf = statusFilter;
                    tickets = tickets.stream().filter(t -> t.getStatus().equalsIgnoreCase(sf)).toList();
                }
                if (priorityFilter != null && categoryFilter != null) {
                    final String pf = priorityFilter;
                    tickets = tickets.stream().filter(t -> t.getPriority() != null && t.getPriority().equalsIgnoreCase(pf)).toList();
                }
            }

            // Build a label describing the filter used
            String ticketFilterLabel = "All Tickets";
            if (ticketId != null) {
                ticketFilterLabel = "Ticket #" + ticketId;
            } else {
                String cat = extractCategoryFromQuery(input);
                String pri = extractPriorityFromQuery(input);
                String rep = extractNameFromQuery(input);
                String sts = extractStatusFromQuery(input);
                if (cat != null) ticketFilterLabel = cat + " Tickets";
                else if (pri != null) ticketFilterLabel = pri + " Priority Tickets";
                else if (rep != null) ticketFilterLabel = "Tickets reported by " + rep;
                else if (sts != null) ticketFilterLabel = sts + " Tickets";
            }

            if (tickets.isEmpty()) {
                String msg = "🎫 No " + ticketFilterLabel.toLowerCase() + " found. Would you like to report a new issue?";
                return new ChatResponse(msg, List.of(), "LOW", false, workflowId, null, null, null, null,
                    List.of(new ChatResponse.QuickReply("🔧 Report Issue", "I want to report a facility issue"),
                            new ChatResponse.QuickReply("🏥 Hospital Info", "Tell me about the hospital")));
            }

            StringBuilder sb = new StringBuilder();
            sb.append("🎫 **").append(ticketFilterLabel);
            sb.append("** (").append(tickets.size()).append(" found)\n\n");

            for (int i = 0; i < tickets.size(); i++) {
                Ticket t = tickets.get(i);
                String statusIcon = switch (t.getStatus().toUpperCase()) {
                    case "OPEN" -> "🟡";
                    case "IN_PROGRESS" -> "🔵";
                    case "RESOLVED" -> "✅";
                    case "CLOSED" -> "⚪";
                    default -> "⚪";
                };
                String priorityIcon = switch ((t.getPriority() != null ? t.getPriority() : "MEDIUM").toUpperCase()) {
                    case "CRITICAL" -> "🔴";
                    case "HIGH" -> "🟠";
                    case "MEDIUM" -> "🟡";
                    case "LOW" -> "🟢";
                    default -> "⚪";
                };
                sb.append(String.format("**%d. Ticket #%d** %s %s\n", i + 1, t.getId(), statusIcon, t.getStatus()));
                sb.append(String.format("   - **Title:** %s\n", t.getTitle()));
                sb.append(String.format("   - **Category:** %s\n", t.getCategory()));
                sb.append(String.format("   - **Priority:** %s %s\n", priorityIcon, t.getPriority()));
                if (t.getLocation() != null && !t.getLocation().isBlank())
                    sb.append(String.format("   - **Location:** %s\n", t.getLocation()));
                if (t.getReportedBy() != null && !t.getReportedBy().isBlank())
                    sb.append(String.format("   - **Reported By:** %s\n", t.getReportedBy()));
                if (t.getCreatedAt() != null)
                    sb.append(String.format("   - **Created:** %s\n",
                        t.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a"))));
                if (t.getDescription() != null && !t.getDescription().isBlank())
                    sb.append(String.format("   - **Description:** %s\n", t.getDescription()));
                sb.append("\n");
            }

            sb.append("Would you like to **report a new issue** or need any other help?");

            String safeOutput = guardrailPipeline.processOutput(sb.toString(), workflowId);
            return new ChatResponse(safeOutput, List.of(), "LOW", false, workflowId, null,
                "Ticket information is fetched from our records. Status may change — contact facilities for real-time updates.",
                null, null,
                List.of(new ChatResponse.QuickReply("🔧 Report Issue", "I want to report a facility issue"),
                        new ChatResponse.QuickReply("📅 Schedule Appointment", "I want to schedule an appointment"),
                        new ChatResponse.QuickReply("🏥 Hospital Info", "Tell me about the hospital")));

        } catch (Exception e) {
            log.error("[{}] Ticket lookup error: {}", workflowId, e.getMessage(), e);
            return ChatResponse.simple(
                "I apologize, I couldn't retrieve ticket information. Please contact our facilities desk for assistance.");
        }
    }

    /**
     * Try to extract a person's name from a query like "show appointments for John Doe".
     * Returns null if no name can be found.
     */
    private String extractNameFromQuery(String query) {
        String lower = query.toLowerCase();
        // Patterns: "for John Doe", "by Jane", "of Dr. Smith", "patient John"
        String[] patterns = {"for ", "by ", "of ", "patient ", "name "};
        for (String prefix : patterns) {
            int idx = lower.indexOf(prefix);
            if (idx >= 0) {
                String after = query.substring(idx + prefix.length()).trim();
                // Take up to 3 words as the name, stopping at common stop-words
                String[] words = after.split("\\s+");
                StringBuilder name = new StringBuilder();
                for (String word : words) {
                    String w = word.toLowerCase();
                    if (w.matches("(with|in|at|on|from|status|department|and|the|please|thanks|today|tomorrow)"))
                        break;
                    if (name.length() > 0) name.append(' ');
                    name.append(word.replaceAll("[^a-zA-Z.\\-']", ""));
                    if (name.toString().split("\\s+").length >= 3) break;
                }
                String result = name.toString().trim();
                if (!result.isEmpty() && result.length() > 1) return result;
            }
        }
        return null;
    }

    /**
     * Try to extract a status filter from a query like "show open tickets" or "completed appointments".
     */
    private String extractStatusFromQuery(String query) {
        String lower = query.toLowerCase();
        if (lower.contains("open")) return "OPEN";
        if (lower.contains("in progress") || lower.contains("in_progress")) return "IN_PROGRESS";
        if (lower.contains("resolved")) return "RESOLVED";
        if (lower.contains("closed")) return "CLOSED";
        if (lower.contains("scheduled")) return "SCHEDULED";
        if (lower.contains("completed")) return "COMPLETED";
        if (lower.contains("cancelled") || lower.contains("canceled")) return "CANCELLED";
        return null;
    }

    /**
     * Extract a department name from the query, e.g. "show cardiology appointments".
     */
    private String extractDepartmentFromQuery(String query) {
        String lower = query.toLowerCase();
        String[][] departments = {
            {"cardiology", "Cardiology"}, {"oncology", "Oncology"}, {"neurology", "Neurology"},
            {"orthopedics", "Orthopedics"}, {"pediatrics", "Pediatrics"}, {"dermatology", "Dermatology"},
            {"radiology", "Radiology"}, {"emergency", "Emergency Medicine"}, {"psychiatry", "Psychiatry"},
            {"gastroenterology", "Gastroenterology"}, {"urology", "Urology"}, {"pulmonology", "Pulmonology"},
            {"endocrinology", "Endocrinology"}, {"ophthalmology", "Ophthalmology"},
            {"general surgery", "General Surgery"}, {"internal medicine", "Internal Medicine"},
            {"obstetrics", "Obstetrics"}, {"gynecology", "Gynecology"}, {"nephrology", "Nephrology"}
        };
        for (String[] dept : departments) {
            if (lower.contains(dept[0])) return dept[1];
        }
        // Use word-boundary regex for short keywords like "ent" to avoid false matches
        // (e.g. "appointments" contains "ent" as a substring)
        if (java.util.regex.Pattern.compile("\\bent\\b").matcher(lower).find()) return "ENT";
        return null;
    }

    /**
     * Extract a date from the query, supporting formats like "2026-03-05", "march 5", "today", "tomorrow".
     */
    private java.time.LocalDate extractDateFromQuery(String query) {
        String lower = query.toLowerCase();
        // "today" / "tomorrow"
        if (lower.contains("today")) return java.time.LocalDate.now();
        if (lower.contains("tomorrow")) return java.time.LocalDate.now().plusDays(1);
        if (lower.contains("yesterday")) return java.time.LocalDate.now().minusDays(1);

        // ISO format: 2026-03-05
        java.util.regex.Matcher isoMatcher = java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(query);
        if (isoMatcher.find()) {
            try { return java.time.LocalDate.parse(isoMatcher.group(1)); } catch (Exception ignored) {}
        }

        // Formats: "March 5, 2026", "march 5 2026", "Mar 5", "5 March 2026"
        String[] monthNames = {"january","february","march","april","may","june","july","august","september","october","november","december"};
        String[] monthAbbr = {"jan","feb","mar","apr","may","jun","jul","aug","sep","oct","nov","dec"};

        // "March 5, 2026" or "March 5 2026" or "March 5"
        java.util.regex.Matcher mdyMatcher = java.util.regex.Pattern.compile(
            "(?:january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|oct|nov|dec)\\.?\\s+(\\d{1,2})(?:[,\\s]+(\\d{4}))?",
            java.util.regex.Pattern.CASE_INSENSITIVE
        ).matcher(query);
        if (mdyMatcher.find()) {
            try {
                String monthStr = mdyMatcher.group(0).split("\\s")[0].replaceAll("\\.", "").toLowerCase();
                int month = -1;
                for (int i = 0; i < monthNames.length; i++) {
                    if (monthNames[i].startsWith(monthStr) || monthAbbr[i].equals(monthStr)) { month = i + 1; break; }
                }
                if (month > 0) {
                    int day = Integer.parseInt(mdyMatcher.group(1));
                    int year = mdyMatcher.group(2) != null ? Integer.parseInt(mdyMatcher.group(2)) : java.time.LocalDate.now().getYear();
                    return java.time.LocalDate.of(year, month, day);
                }
            } catch (Exception ignored) {}
        }

        // "dd/mm/yyyy" or "mm/dd/yyyy"
        java.util.regex.Matcher slashMatcher = java.util.regex.Pattern.compile("(\\d{1,2})/(\\d{1,2})/(\\d{4})").matcher(query);
        if (slashMatcher.find()) {
            try {
                int a = Integer.parseInt(slashMatcher.group(1));
                int b = Integer.parseInt(slashMatcher.group(2));
                int y = Integer.parseInt(slashMatcher.group(3));
                // Assume MM/DD/YYYY
                return java.time.LocalDate.of(y, a, b);
            } catch (Exception ignored) {}
        }

        return null;
    }

    /**
     * Extract a ticket category from the query, e.g. "show equipment malfunction tickets".
     */
    private String extractCategoryFromQuery(String query) {
        String lower = query.toLowerCase();
        if (lower.contains("equipment malfunction") || lower.contains("equipment")) return "Equipment Malfunction";
        if (lower.contains("room issue") || lower.contains("room")) return "Room Issue";
        if (lower.contains("facility") || lower.contains("infrastructure")) return "Facility";
        if (lower.contains("safety")) return "Safety";
        if (lower.contains("hvac") || lower.contains("heating") || lower.contains("cooling")) return "HVAC";
        if (lower.contains("plumbing") || lower.contains("water")) return "Plumbing";
        if (lower.contains("electrical") || lower.contains("power")) return "Electrical";
        if (lower.contains("cleaning") || lower.contains("sanitation")) return "Cleaning";
        return null;
    }

    /**
     * Extract a priority level from the query, e.g. "show high priority tickets".
     */
    private String extractPriorityFromQuery(String query) {
        String lower = query.toLowerCase();
        if (lower.contains("critical")) return "CRITICAL";
        if (lower.contains("high")) return "HIGH";
        if (lower.contains("medium")) return "MEDIUM";
        if (lower.contains("low priority") || lower.matches(".*\\blow\\b.*")) return "LOW";
        return null;
    }

    /**
     * Handle general informational Q&A queries using RAG.
     */
    private ChatResponse handleInformationalQuery(String input, String department, String facility,
                                                   String workflowId, String conversationHistory) {
        auditService.logSimple(workflowId, 1, "INFORMATIONAL_QA", "Orchestrator", input, "RAG retrieval");

        // Build dynamic filters
        java.util.HashMap<String, String> filters = new java.util.HashMap<>();
        if (department != null && !department.isBlank()) filters.put("department", department);
        if (facility != null && !facility.isBlank()) filters.put("facility", facility);

        HealthcareRagService.RagResult ragResult = ragService.retrieve(input, filters.isEmpty() ? null : filters);

        if (!ragResult.hasContext()) {
            return ChatResponse.simple(
                "I don't have specific information about that in our hospital knowledge base. " +
                "Please contact our front desk at (555) 123-4567 or visit the information counter in the main lobby for assistance.");
        }

        try {
            String systemPrompt = """
                    You are a friendly, conversational hospital information assistant. Answer the patient's question using ONLY
                    the provided hospital knowledge base context. You MUST:
                    1. Provide accurate, helpful information from the KB
                    2. Be warm, empathetic, and conversational in tone — greet the user naturally
                    3. If information is not in the KB, say so honestly and suggest who to contact
                    4. Include relevant department contacts or locations when applicable
                    5. Add a brief disclaimer when appropriate
                    6. Use **bold** for important details like department names, phone numbers, times, locations, and key instructions
                    7. ALWAYS end your response by asking if the user needs anything else or suggesting a related follow-up
                    8. Be proactive — if the user asks about visiting hours, also mention parking or directions if available
                    9. Use the CONVERSATION HISTORY (if provided) to maintain continuity and avoid repeating information already shared

                    Hospital Knowledge Base Context:
                    """ + ragResult.context();

            // Append conversation history for continuity
            if (conversationHistory != null && !conversationHistory.isBlank()) {
                systemPrompt += conversationHistory;
            }

            String response = chatClient.prompt()
                .system(systemPrompt)
                .user(input)
                .call()
                .content();

            String safeOutput = guardrailPipeline.processOutput(response, workflowId);
            return new ChatResponse(safeOutput, ragResult.citations(), "LOW", false, workflowId, null,
                "This information is for guidance only. Please consult a healthcare professional for medical advice.",
                null, null,
                List.of(
                    new ChatResponse.QuickReply("❓ Ask Another Question", "I have another question"),
                    new ChatResponse.QuickReply("📅 Book Appointment", "I want to schedule an appointment"),
                    new ChatResponse.QuickReply("👨‍⚕️ Find Doctors", "Show me available doctors"),
                    new ChatResponse.QuickReply("💰 Insurance Help", "I have an insurance question")
                ));

        } catch (Exception e) {
            log.error("[{}] Informational query error: {}", workflowId, e.getMessage());
            return ChatResponse.simple("I apologize, I couldn't process your question. Please contact our front desk for assistance.");
        }
    }

    /**
     * Query intent classification.
     */
    private enum QueryIntent {
        GREETING,
        FOLLOW_UP,
        SYMPTOM_TRIAGE,
        INSURANCE_QUERY,
        EQUIPMENT_FACILITY,
        APPOINTMENT_SCHEDULING,
        APPOINTMENT_LOOKUP,
        APPOINTMENT_CANCEL,
        TICKET_LOOKUP,
        DOCTOR_AVAILABILITY,
        INFORMATIONAL_QA
    }
}
