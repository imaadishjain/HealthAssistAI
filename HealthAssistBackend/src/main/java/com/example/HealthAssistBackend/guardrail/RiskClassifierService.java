package com.example.HealthAssistBackend.guardrail;

import com.example.HealthAssistBackend.model.RiskTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Risk tier classifier for patient symptoms and queries.
 * Classifies queries into LOW, MEDIUM, HIGH, or CRITICAL risk tiers.
 */
@Service
public class RiskClassifierService {

    private static final Logger log = LoggerFactory.getLogger(RiskClassifierService.class);

    // Patterns indicating the user is asking for department guidance (not an active emergency)
    private static final List<Pattern> DEPARTMENT_INQUIRY_PATTERNS = List.of(
        Pattern.compile("(?i)(which|what)\\s+(department|dept|specialist|doctor|section|unit)"),
        Pattern.compile("(?i)suggest.*\\b(department|dept|specialist|doctor)\\b"),
        Pattern.compile("(?i)(where|whom)\\s+(should|do|can)\\s+i\\s+(go|report|visit|consult|see)"),
        Pattern.compile("(?i)\\b(report\\s+to|go\\s+to|visit|consult)\\s+(which|what)"),
        Pattern.compile("(?i)\\b(recommend|advise).*\\b(department|dept|specialist|doctor)\\b"),
        Pattern.compile("(?i)\\b(department|dept)\\s+(for|to|should)\\b"),
        Pattern.compile("(?i)\\bwhich\\s+(doctor|specialist)\\s+(should|can|do)\\b"),
        Pattern.compile("(?i)\\b(book|schedule|appointment).*\\b(department|which)\\b")
    );

    // Critical symptoms requiring immediate emergency attention
    private static final List<Pattern> CRITICAL_PATTERNS = List.of(
        Pattern.compile("(?i)(chest\\s+pain|heart\\s+attack|cardiac\\s+arrest)"),
        Pattern.compile("(?i)(can'?t\\s+breathe|difficulty\\s+breathing|severe\\s+breathing)"),
        Pattern.compile("(?i)(stroke|sudden\\s+numbness|face\\s+drooping|slurred\\s+speech)"),
        Pattern.compile("(?i)(severe\\s+bleeding|uncontrolled\\s+bleeding|hemorrhage)"),
        Pattern.compile("(?i)(unconscious|loss\\s+of\\s+consciousness|unresponsive)"),
        Pattern.compile("(?i)(anaphyla|severe\\s+allergic)"),
        Pattern.compile("(?i)(seizure|convulsion)"),
        Pattern.compile("(?i)(overdose|poison)")
    );

    // High risk symptoms needing urgent attention
    private static final List<Pattern> HIGH_PATTERNS = List.of(
        Pattern.compile("(?i)(sharp\\s+pain|severe\\s+pain|intense\\s+pain)"),
        Pattern.compile("(?i)(shortness\\s+of\\s+breath|breathing\\s+difficulty)"),
        Pattern.compile("(?i)(high\\s+fever|fever\\s+over\\s+10[2-5])"),
        Pattern.compile("(?i)(head\\s+injury|concussion|hit\\s+my\\s+head)"),
        Pattern.compile("(?i)(blood\\s+in\\s+(urine|stool|vomit))"),
        Pattern.compile("(?i)(suicid|self[- ]?harm|want\\s+to\\s+die)"),
        Pattern.compile("(?i)(broken\\s+bone|fracture)"),
        Pattern.compile("(?i)(severe\\s+headache|worst\\s+headache)")
    );

    // Medium risk symptoms
    private static final List<Pattern> MEDIUM_PATTERNS = List.of(
        Pattern.compile("(?i)(persistent\\s+cough|cough\\s+for\\s+weeks)"),
        Pattern.compile("(?i)(moderate\\s+pain|ongoing\\s+pain|chronic\\s+pain)"),
        Pattern.compile("(?i)(dizziness|lightheaded|vertigo)"),
        Pattern.compile("(?i)(rash|skin\\s+irritation|swelling)"),
        Pattern.compile("(?i)(nausea|vomiting|diarrhea)"),
        Pattern.compile("(?i)(fever|temperature)"),
        Pattern.compile("(?i)(anxiety|depression|mental\\s+health)"),
        Pattern.compile("(?i)(infection|infected)")
    );

    /**
     * Classify the risk tier of a user query/symptom description.
     */
    public RiskClassification classify(String text) {
        if (text == null || text.isBlank()) {
            return new RiskClassification(RiskTier.LOW, "No symptoms described", false);
        }

        // First, check if the user is asking about which department to visit.
        // Department-inquiry questions (e.g. "I have chest pain, which department should I go to?")
        // should be routed to the symptom checker for department recommendation,
        // NOT treated as an active emergency — even if they mention serious symptoms.
        boolean isDepartmentInquiry = isDepartmentInquiry(text);

        // Check critical first
        for (Pattern pattern : CRITICAL_PATTERNS) {
            if (pattern.matcher(text).find()) {
                if (isDepartmentInquiry) {
                    log.info("CRITICAL symptoms detected in a department inquiry — downgrading to MEDIUM for routing: {}",
                        text.substring(0, Math.min(text.length(), 100)));
                    return new RiskClassification(RiskTier.MEDIUM,
                        "Serious symptoms noted in a department inquiry. Routing to specialist recommendation.", false);
                }
                log.warn("CRITICAL risk classified for input: {}", text.substring(0, Math.min(text.length(), 100)));
                return new RiskClassification(RiskTier.CRITICAL,
                    "Critical symptoms detected. Immediate medical attention required.", true);
            }
        }

        // Check high risk
        for (Pattern pattern : HIGH_PATTERNS) {
            if (pattern.matcher(text).find()) {
                if (isDepartmentInquiry) {
                    log.info("HIGH-risk symptoms detected in a department inquiry — downgrading to MEDIUM for routing: {}",
                        text.substring(0, Math.min(text.length(), 100)));
                    return new RiskClassification(RiskTier.MEDIUM,
                        "Concerning symptoms noted in a department inquiry. Routing to specialist recommendation.", false);
                }
                log.warn("HIGH risk classified for input: {}", text.substring(0, Math.min(text.length(), 100)));
                return new RiskClassification(RiskTier.HIGH,
                    "High-risk symptoms detected. Escalating to medical professional.", true);
            }
        }

        // Check medium risk
        for (Pattern pattern : MEDIUM_PATTERNS) {
            if (pattern.matcher(text).find()) {
                log.info("MEDIUM risk classified for input: {}", text.substring(0, Math.min(text.length(), 100)));
                return new RiskClassification(RiskTier.MEDIUM,
                    "Moderate symptoms detected. Prompt medical attention recommended.", false);
            }
        }

        // Default to low risk
        return new RiskClassification(RiskTier.LOW,
            "No high-risk symptoms detected. General guidance appropriate.", false);
    }

    /**
     * Check whether the query is asking about which department to visit
     * rather than reporting an active emergency.
     */
    public boolean isDepartmentInquiry(String text) {
        for (Pattern pattern : DEPARTMENT_INQUIRY_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Risk classification result.
     */
    public record RiskClassification(RiskTier riskTier, String reasoning, boolean escalationRequired) {}
}
