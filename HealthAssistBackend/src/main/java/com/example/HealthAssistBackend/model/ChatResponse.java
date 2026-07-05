package com.example.HealthAssistBackend.model;

import java.util.List;

/**
 * Chat response DTO with citations, risk assessment, audit metadata, optional form request, and quick-reply buttons.
 */
public record ChatResponse(
    String response,
    List<String> citations,
    String riskTier,
    boolean escalated,
    String workflowId,
    String routedTo,
    String disclaimer,
    String formType,
    List<FormField> formFields,
    List<QuickReply> quickReplies
) {
    /**
     * A clickable quick-reply button shown below the message.
     */
    public record QuickReply(
        String label,    // Display text on the button
        String payload   // Message sent when clicked
    ) {}

    /**
     * Represents a single field in a dynamic form shown to the user.
     */
    public record FormField(
        String name,
        String label,
        String type,       // text, date, time, select, textarea, email, tel, number
        boolean required,
        String placeholder,
        List<String> options  // for select/dropdown fields
    ) {
        public FormField(String name, String label, String type, boolean required, String placeholder) {
            this(name, label, type, required, placeholder, null);
        }
    }

    /* ── Factory methods ── */

    public static ChatResponse simple(String response) {
        return new ChatResponse(response, List.of(), "LOW", false, null, null,
            "This information is for guidance only. Please consult a healthcare professional for medical advice.",
            null, null, null);
    }

    public static ChatResponse withCitations(String response, List<String> citations, String workflowId) {
        return new ChatResponse(response, citations, "LOW", false, workflowId, null,
            "This information is for guidance only. Please consult a healthcare professional for medical advice.",
            null, null, null);
    }

    public static ChatResponse escalated(String response, String riskTier, String workflowId) {
        return new ChatResponse(response, List.of(), riskTier, true, workflowId, "Human Nurse/Doctor",
            "⚠️ HIGH RISK DETECTED: This case has been escalated to a medical professional for immediate review.",
            null, null,
            List.of(new QuickReply("🚑 Call 911", "Call 911 emergency"),
                    new QuickReply("🏥 Go to ER", "Where is the emergency room?")));
    }

    /**
     * Return a response that asks the frontend to render a data-collection form.
     */
    public static ChatResponse withForm(String prompt, String formType, List<FormField> fields, String workflowId) {
        return new ChatResponse(prompt, List.of(), "LOW", false, workflowId, null,
            null, formType, fields, null);
    }

    /**
     * Return a response with quick-reply action buttons.
     */
    public static ChatResponse withQuickReplies(String response, String workflowId, String disclaimer, List<QuickReply> quickReplies) {
        return new ChatResponse(response, List.of(), "LOW", false, workflowId, null,
            disclaimer, null, null, quickReplies);
    }
}
