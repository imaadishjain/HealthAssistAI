package com.example.HealthAssistBackend.guardrail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Moderation service to pre-screen user inputs and model outputs.
 * Blocks unsafe, harmful, or off-topic requests in a healthcare context.
 */
@Service
public class ModerationService {

    private static final Logger log = LoggerFactory.getLogger(ModerationService.class);

    // Patterns for detecting unsafe content
    private static final List<Pattern> UNSAFE_PATTERNS = List.of(
        Pattern.compile("(?i)(how\\s+to\\s+)?(kill|harm|hurt|poison|overdose)\\s+(myself|someone|a\\s+person)"),
        Pattern.compile("(?i)(suicide|self[- ]?harm|end\\s+my\\s+life)"),
        Pattern.compile("(?i)(make|create|synthesize)\\s+(drugs|meth|bomb|weapon)"),
        Pattern.compile("(?i)(illegal|illicit)\\s+(drug|substance|prescription)\\s+(recipe|formula|synthesis)"),
        Pattern.compile("(?i)(hack|breach|steal)\\s+(medical|patient|health)\\s+(records|data|information)"),
        // Toxicity / hate-speech patterns
        Pattern.compile("(?i)(racial|ethnic|gender)\\s+(slur|insult|abuse)"),
        Pattern.compile("(?i)\\b(hate|threaten|assault|attack)\\s+(you|doctor|nurse|staff|patient)"),
        // Exploitation patterns
        Pattern.compile("(?i)(fake|forge|falsify)\\s+(prescription|medical\\s+record|certificate|report)"),
        Pattern.compile("(?i)(sell|buy|trade)\\s+(organs|blood|prescription|opioid)")
    );

    // Prompt injection / jailbreak patterns
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
        Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|prior|above)\\s+(instructions|rules|prompts|guidelines)"),
        Pattern.compile("(?i)disregard\\s+(all|your|the)\\s+(rules|instructions|guidelines|constraints)"),
        Pattern.compile("(?i)you\\s+are\\s+now\\s+(a|an|my)"),
        Pattern.compile("(?i)(forget|override|bypass|skip)\\s+(everything|all|your|safety|guardrails|rules)"),
        Pattern.compile("(?i)new\\s+(instructions|rules|persona|role)\\s*:"),
        Pattern.compile("(?i)(system|developer|admin)\\s*(prompt|mode|override|access)\\s*:"),
        Pattern.compile("(?i)act\\s+(as|like)\\s+(a\\s+)?(different|unrestricted|unfiltered|evil)"),
        Pattern.compile("(?i)(jailbreak|DAN|do\\s+anything\\s+now|roleplay\\s+as)"),
        Pattern.compile("(?i)pretend\\s+(you|that)\\s+(are|have)\\s+(no|without)\\s+(rules|restrictions|limits|filters)"),
        Pattern.compile("(?i)(reveal|show|print|output)\\s+(your|the|system)\\s+(prompt|instructions|rules)"),
        Pattern.compile("(?i)what\\s+(are|is)\\s+your\\s+(system|initial|original)\\s+(prompt|instructions)"),
        Pattern.compile("(?i)\\[\\s*(INST|SYS|SYSTEM)\\s*\\]"),
        Pattern.compile("(?i)<\\s*(system|instructions|prompt)\\s*>"),
        Pattern.compile("(?i)(enable|activate|switch\\s+to)\\s+(developer|debug|admin|god|sudo)\\s+(mode|access)")
    );

    // Patterns for off-topic content
    private static final List<Pattern> OFF_TOPIC_PATTERNS = List.of(
        Pattern.compile("(?i)(stock|crypto|bitcoin|investment)\\s+(advice|tip|prediction)"),
        Pattern.compile("(?i)(write|generate)\\s+(essay|code|poem|story)\\s+(?!.*medic)"),
        Pattern.compile("(?i)(political|election|vote)\\s+(opinion|advice)")
    );

    /**
     * Moderate user input. Returns list of issues found. Empty list means safe.
     */
    public ModerationResult moderateInput(String input) {
        List<String> issues = new ArrayList<>();
        boolean blocked = false;

        if (input == null || input.isBlank()) {
            issues.add("Empty input received");
            return new ModerationResult(false, issues, "EMPTY_INPUT");
        }

        // Check for unsafe content
        for (Pattern pattern : UNSAFE_PATTERNS) {
            if (pattern.matcher(input).find()) {
                issues.add("Unsafe content detected: potential harm-related query");
                blocked = true;
                break;
            }
        }

        // Check for off-topic content
        for (Pattern pattern : OFF_TOPIC_PATTERNS) {
            if (pattern.matcher(input).find()) {
                issues.add("Off-topic content detected: not healthcare-related");
                break;
            }
        }

        // Check for excessive length (potential prompt injection)
        if (input.length() > 5000) {
            issues.add("Input exceeds maximum length (5000 characters)");
            blocked = true;
        }

        // Check for prompt injection / jailbreak patterns
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                issues.add("Prompt injection or jailbreak attempt detected");
                log.warn("PROMPT INJECTION BLOCKED: matched pattern in input");
                blocked = true;
                break;
            }
        }

        // Check for encoding-based injection (Base64-wrapped instructions, unicode tricks)
        if (input.matches(".*[\\x00-\\x08\\x0E-\\x1F].*")) {
            issues.add("Suspicious control characters detected in input");
            blocked = true;
        }

        String status = blocked ? "BLOCKED" : (issues.isEmpty() ? "PASS" : "WARNING");
        if (!issues.isEmpty()) {
            log.warn("Moderation issues detected: {}", issues);
        }

        return new ModerationResult(!blocked, issues, status);
    }

    /**
     * Moderate model output for healthcare safety.
     */
    public ModerationResult moderateOutput(String output) {
        List<String> issues = new ArrayList<>();

        if (output == null || output.isBlank()) {
            return new ModerationResult(true, List.of(), "PASS");
        }

        // Check for diagnosis-like statements
        if (Pattern.compile("(?i)(you\\s+(have|are\\s+suffering\\s+from|are\\s+diagnosed\\s+with))").matcher(output).find()) {
            issues.add("Output contains diagnosis-like statement — should include disclaimer");
        }

        // Check for prescription language
        if (Pattern.compile("(?i)(take|prescribe|dosage|mg)\\s+(aspirin|ibuprofen|acetaminophen|medication)").matcher(output).find()) {
            issues.add("Output contains prescription-like language — should include disclaimer");
        }

        return new ModerationResult(true, issues, issues.isEmpty() ? "PASS" : "WARNING");
    }

    /**
     * Result of moderation check.
     */
    public record ModerationResult(boolean allowed, List<String> issues, String status) {}
}
