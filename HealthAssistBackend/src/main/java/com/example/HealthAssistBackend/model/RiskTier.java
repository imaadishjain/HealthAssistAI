package com.example.HealthAssistBackend.model;

/**
 * Risk tier classification for patient symptoms and queries.
 */
public enum RiskTier {
    LOW("Low risk - routine care appropriate"),
    MEDIUM("Medium risk - prompt attention recommended"),
    HIGH("High risk - urgent care required, escalating to medical staff"),
    CRITICAL("Critical risk - immediate emergency intervention required");

    private final String description;

    RiskTier(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
