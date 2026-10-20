package com.smarthire.ai.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * PII redaction utility for sanitizing untrusted text before sending to AI providers.
 *
 * <p>Redacts common PII patterns: email addresses, phone numbers, SSNs, physical addresses,
 * and personal names (heuristic). Uses regex-based pattern matching with replacement tokens.
 *
 * <p><b>Limitations:</b>
 * <ul>
 *   <li>Regex-based redaction is heuristic, not guaranteed complete
 *   <li>Names are detected via capitalization heuristics — false positives/negatives expected
 *   <li>International phone formats and address formats may not be fully covered
 *   <li>Context-aware NER would be more accurate but requires additional dependencies
 * </ul>
 *
 * <p>This utility provides a best-effort safety layer. The AI provider interface contract
 * requires that implementations treat all input as untrusted and apply their own defenses.
 */
@Component
public class PiiRedactionService {

  private static final Logger log = LoggerFactory.getLogger(PiiRedactionService.class);

  // Email pattern
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

  // Phone patterns (US and common international)
  private static final Pattern PHONE_PATTERN =
      Pattern.compile(
          "\\b(\\+?1[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b|\\b\\+\\d{1,3}[-.\\s]?\\d{1,4}[-.\\s]?\\d{1,4}[-.\\s]?\\d{1,9}\\b");

  // SSN pattern (XXX-XX-XXXX or XXXXXXXXX)
  private static final Pattern SSN_PATTERN =
      Pattern.compile("\\b\\d{3}[-.\\s]?\\d{2}[-.\\s]?\\d{4}\\b");

  // Credit card pattern (basic Luhn-agnostic)
  private static final Pattern CREDIT_CARD_PATTERN =
      Pattern.compile("\\b\\d{4}[-.\\s]?\\d{4}[-.\\s]?\\d{4}[-.\\s]?\\d{4}\\b");

  // Address pattern (simple heuristic: number + street name + type)
  private static final Pattern ADDRESS_PATTERN =
      Pattern.compile(
          "\\b\\d+\\s+[A-Za-z]+\\s+(?:Street|St|Avenue|Ave|Road|Rd|Boulevard|Blvd|Lane|Ln|Drive|Dr|Court|Ct|Place|Pl|Way|Circle|Cir|Parkway|Pkwy)\\b",
          Pattern.CASE_INSENSITIVE);

  // Name heuristic: Two or three capitalized words (First [Middle] Last)
  // This is a heuristic and will have false positives/negatives
  private static final Pattern NAME_PATTERN =
      Pattern.compile(
          "(?<!\\w)(?:[A-Z][a-z]+\\s+){1,2}[A-Z][a-z]+(?!\\w)");

  // Replacement tokens
  private static final String EMAIL_REPLACEMENT = "[EMAIL_REDACTED]";
  private static final String PHONE_REPLACEMENT = "[PHONE_REDACTED]";
  private static final String SSN_REPLACEMENT = "[SSN_REDACTED]";
  private static final String CREDIT_CARD_REPLACEMENT = "[CREDIT_CARD_REDACTED]";
  private static final String ADDRESS_REPLACEMENT = "[ADDRESS_REDACTED]";
  private static final String NAME_REPLACEMENT = "[NAME_REDACTED]";

  /**
   * Redacts PII from the given text.
   *
   * @param text the input text (may be null or empty)
   * @return the text with PII patterns replaced by tokens, or empty string if input is null
   */
  public String redact(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }

    String result = text;

    // Apply redaction patterns in order
    result = redactPattern(result, EMAIL_PATTERN, EMAIL_REPLACEMENT);
    result = redactPattern(result, PHONE_PATTERN, PHONE_REPLACEMENT);
    result = redactPattern(result, SSN_PATTERN, SSN_REPLACEMENT);
    result = redactPattern(result, CREDIT_CARD_PATTERN, CREDIT_CARD_REPLACEMENT);
    result = redactPattern(result, ADDRESS_PATTERN, ADDRESS_REPLACEMENT);
    // Name redaction last (heuristic, most prone to false positives)
    result = redactNames(result);

    if (!result.equals(text)) {
      log.debug("PII redaction applied: original length={}, redacted length={}", text.length(), result.length());
    }

    return result;
  }

  /**
   * Redacts names using heuristic pattern. Applied last to avoid interfering with other patterns.
   */
  private String redactNames(String text) {
    Matcher matcher = NAME_PATTERN.matcher(text);
    StringBuffer sb = new StringBuffer();

    while (matcher.find()) {
      String match = matcher.group();
      // Skip common words that match the pattern but aren't names
      if (isCommonFalsePositive(match)) {
        matcher.appendReplacement(sb, match);
      } else {
        matcher.appendReplacement(sb, NAME_REPLACEMENT);
      }
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  /**
   * List of common words that match the name pattern but aren't personal names.
   */
  private boolean isCommonFalsePositive(String word) {
    return switch (word.toLowerCase()) {
      case "java", "spring", "boot", "react", "angular", "vue", "node", "python", "sql", "aws",
           "azure", "gcp", "docker", "kubernetes", "jenkins", "git", "jira", "confluence",
           "github", "gitlab", "bitbucket", "linux", "windows", "macos", "ubuntu", "debian",
           "postgresql", "mysql", "mongodb", "redis", "kafka", "rabbitmq", "elasticsearch",
           "rest", "graphql", "grpc", "microservices", "ci", "cd", "ci/cd", "agile", "scrum",
           "kanban", "junior", "senior", "lead", "principal", "staff", "architect", "manager",
           "director", "vp", "cto", "ceo", "cfo", "coo" -> true;
      default -> false;
    };
  }

  private String redactPattern(String text, Pattern pattern, String replacement) {
    Matcher matcher = pattern.matcher(text);
    return matcher.replaceAll(replacement);
  }
}