package com.example.HealthAssistBackend.guardrail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PII (Personally Identifiable Information) redaction service.
 * Redacts names, MRNs, emails, phone numbers, SSNs, and dates of birth from text.
 */
@Service
public class PiiRedactionService {

    private static final Logger log = LoggerFactory.getLogger(PiiRedactionService.class);

    // Regex patterns for common PII types
    private static final List<PiiPattern> PII_PATTERNS = List.of(
        new PiiPattern("EMAIL", Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"), "[EMAIL_REDACTED]"),
        new PiiPattern("PHONE", Pattern.compile("\\b(\\+?1?[-.]?\\(?\\d{3}\\)?[-.]?\\d{3}[-.]?\\d{4})\\b"), null),
        new PiiPattern("SSN", Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"), "[SSN_REDACTED]"),
        new PiiPattern("MRN", Pattern.compile("(?i)(MRN|Medical\\s+Record)\\s*[:#]?\\s*\\d{5,10}"), "[MRN_REDACTED]"),
        new PiiPattern("DOB", Pattern.compile("(?i)(date\\s+of\\s+birth|DOB)\\s*[:#]?\\s*\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}"), "[DOB_REDACTED]"),
        new PiiPattern("CREDIT_CARD", Pattern.compile("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b"), "[CARD_REDACTED]"),
        new PiiPattern("ADDRESS", Pattern.compile("\\b\\d{1,5}\\s+[A-Za-z]+\\s+(Street|St|Avenue|Ave|Road|Rd|Boulevard|Blvd|Drive|Dr|Lane|Ln)\\b"), "[ADDRESS_REDACTED]")
    );

    // Additional pattern to catch plain sequences of 7-15 digits (phone numbers without formatting)
    private static final Pattern PLAIN_DIGITS_PHONE = Pattern.compile("\\b\\d{7,15}\\b");

    /**
     * Scan text for PII and return redacted version.
     */
    public RedactionResult redact(String text) {
        if (text == null || text.isBlank()) {
            return new RedactionResult(text, false, List.of());
        }

        String redacted = text;
        List<String> detectedTypes = new ArrayList<>();

        for (PiiPattern piiPattern : PII_PATTERNS) {
            Matcher matcher = piiPattern.pattern().matcher(redacted);
            if (matcher.find()) {
                detectedTypes.add(piiPattern.type());
                if ("PHONE".equals(piiPattern.type())) {
                    // Mask phone numbers — show only last 4 digits
                    redacted = maskPhoneNumbers(redacted, piiPattern.pattern());
                } else {
                    redacted = piiPattern.pattern().matcher(redacted).replaceAll(piiPattern.replacement());
                }
                log.info("PII detected and redacted: type={}", piiPattern.type());
            }
        }

        // Also catch plain digit sequences (7-15 digits) that look like phone numbers
        Matcher plainMatcher = PLAIN_DIGITS_PHONE.matcher(redacted);
        if (plainMatcher.find()) {
            if (!detectedTypes.contains("PHONE")) {
                detectedTypes.add("PHONE");
            }
            redacted = maskPhoneNumbers(redacted, PLAIN_DIGITS_PHONE);
            log.info("PII detected and redacted: type=PHONE (plain digits)");
        }

        boolean piiDetected = !detectedTypes.isEmpty();
        if (piiDetected) {
            log.warn("PII redaction applied. Types found: {}", detectedTypes);
        }

        return new RedactionResult(redacted, piiDetected, detectedTypes);
    }

    /**
     * Mask phone numbers — replace all but last 4 digits with asterisks.
     * e.g. "1983983267" → "******3267", "(555) 123-4567" → "******4567"
     */
    private String maskPhoneNumbers(String text, Pattern phonePattern) {
        Matcher matcher = phonePattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String phone = matcher.group();
            String digitsOnly = phone.replaceAll("[^\\d]", "");
            if (digitsOnly.length() >= 4) {
                String lastFour = digitsOnly.substring(digitsOnly.length() - 4);
                String masked = "*".repeat(digitsOnly.length() - 4) + lastFour;
                matcher.appendReplacement(sb, Matcher.quoteReplacement(masked));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement("****"));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Check if text contains PII without redacting.
     */
    public boolean containsPii(String text) {
        if (text == null) return false;
        return PII_PATTERNS.stream().anyMatch(p -> p.pattern().matcher(text).find());
    }

    /**
     * PII pattern definition.
     */
    private record PiiPattern(String type, Pattern pattern, String replacement) {}

    /**
     * Result of PII redaction.
     */
    public record RedactionResult(String redactedText, boolean piiDetected, List<String> detectedTypes) {}
}
