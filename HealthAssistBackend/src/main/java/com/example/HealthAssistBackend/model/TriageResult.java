package com.example.HealthAssistBackend.model;

import java.util.List;

/**
 * Triage assessment result with severity classification, department routing, and safety disclaimers.
 */
public record TriageResult(
    String severity,
    RiskTier riskTier,
    String recommendedDepartment,
    String recommendation,
    boolean escalationRequired,
    List<String> symptoms,
    String disclaimer
) {
    public static TriageResult lowRisk(String department, String recommendation, List<String> symptoms) {
        return new TriageResult("LOW", RiskTier.LOW, department, recommendation, false, symptoms,
            "This is a preliminary assessment only. Please consult with a qualified healthcare provider for a proper diagnosis.");
    }

    public static TriageResult mediumRisk(String department, String recommendation, List<String> symptoms) {
        return new TriageResult("MEDIUM", RiskTier.MEDIUM, department, recommendation, false, symptoms,
            "Your symptoms suggest you should see a doctor soon. This is not a diagnosis — please seek professional medical evaluation.");
    }

    public static TriageResult highRisk(String department, String recommendation, List<String> symptoms) {
        return new TriageResult("HIGH", RiskTier.HIGH, department, recommendation, true, symptoms,
            "⚠️ Your symptoms indicate a potentially serious condition. This case is being escalated to a medical professional immediately. If you are experiencing a medical emergency, please call emergency services (911) right away.");
    }

    public static TriageResult critical(List<String> symptoms) {
        return new TriageResult("CRITICAL", RiskTier.CRITICAL, "Emergency Medicine",
            "IMMEDIATE EMERGENCY ATTENTION REQUIRED. Please call 911 or go to the nearest emergency room immediately.",
            true, symptoms,
            "🚨 CRITICAL EMERGENCY: This situation requires immediate medical intervention. Call 911 now. Do not wait.");
    }
}
