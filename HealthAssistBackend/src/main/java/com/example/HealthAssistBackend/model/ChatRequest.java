package com.example.HealthAssistBackend.model;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Chat request DTO for healthcare assistant interactions.
 */
public record ChatRequest(
    @NotBlank(message = "Message is required")
    String message,
    String sessionId,
    String department,
    String facility,
    String insurancePlan,
    List<ChatHistoryEntry> chatHistory
) {
    /**
     * A single turn in the conversation history.
     */
    public record ChatHistoryEntry(String role, String content) {}

    public ChatRequest(String message) {
        this(message, null, null, null, null, null);
    }

    /**
     * Build a formatted conversation history string for LLM context.
     * Returns empty string if no history is available.
     */
    public String formattedHistory() {
        if (chatHistory == null || chatHistory.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("\n\nCONVERSATION HISTORY (most recent messages):\n");
        for (ChatHistoryEntry entry : chatHistory) {
            String label = "user".equals(entry.role()) ? "Patient" : "HealthAssist AI";
            sb.append(label).append(": ").append(entry.content()).append("\n");
        }
        sb.append("--- End of history ---\n");
        return sb.toString();
    }
}
