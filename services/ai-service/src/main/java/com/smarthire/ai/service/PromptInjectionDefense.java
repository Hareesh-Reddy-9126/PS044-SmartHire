package com.smarthire.ai.service;

import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Prompt injection defense utilities.
 *
 * <p>Provides utilities for safely framing untrusted user content (résumés, job descriptions)
 * within system prompts to prevent instruction injection attacks.
 *
 * <p>Key principles:
 *
 * <ul>
 *   <li>Use explicit delimiters to separate system instructions from user content
 *   <li>Validate output against expected schema before returning
 *   <li>Never trust user content to follow formatting instructions
 *   <li>Log injection attempts for monitoring
 * </ul>
 *
 * <p>This is a defense-in-depth layer. The AI provider implementations must also enforce their own
 * safety boundaries.
 */
@Component
public class PromptInjectionDefense {

  private static final Logger log = LoggerFactory.getLogger(PromptInjectionDefense.class);

  // Common injection patterns.
  //
  // Role-play and instruction-leak patterns intentionally allow a small number of
  // filler words between the trigger verb and the target noun so that natural
  // phrasing ("you are now a...", "reveal the system prompt") is still caught,
  // while remaining scoped to injection-relevant vocabulary.
  private static final Pattern IGNORE_INSTRUCTIONS_PATTERN =
      Pattern.compile(
          "(?i)\\b(ignore|ignores|forget|disregard|override|bypass)\\s+(?:previous|prior|earlier|all|the)\\s+(?:instructions?|prompts?|rules?|guidelines?)\\b");

  private static final Pattern ROLE_PLAY_PATTERN =
      Pattern.compile(
          "(?i)\\b(?:act as|pretend to be|roleplay|simulate)\\b(?:\\s+(?:a|an|the|as))?\\s+\\w+");

  private static final Pattern INSTRUCTION_LEAK_PATTERN =
      Pattern.compile(
          "(?i)\\b(?:print|show|output|display|reveal|leak|expose)\\b(?:\\s+\\w+){0,3}\\s+(?:prompt|instruction)s?\\b");

  private static final Pattern CONTINUATION_PATTERN =
      Pattern.compile(
          "(?i)\\b(?:continue|complete|finish)\\b(?:\\s+\\w+){0,3}\\s+(?:prompt|instruction|sentence|paragraph)\\b");

  // Delimiter for framing untrusted content
  public static final String CONTENT_START_DELIMITER = "<<<CONTENT_START>>>";
  public static final String CONTENT_END_DELIMITER = "<<<CONTENT_END>>>";
  public static final String INSTRUCTION_DELIMITER = "<<<INSTRUCTION>>>";

  /**
   * Frames untrusted content with explicit delimiters to separate it from system instructions.
   *
   * @param content the untrusted user content (résumé, JD, etc.)
   * @return the content wrapped in explicit delimiters
   */
  public String frameContent(String content) {
    if (content == null) {
      return "";
    }
    if (content.isBlank()) {
      return CONTENT_START_DELIMITER + "\n" + CONTENT_END_DELIMITER;
    }
    return CONTENT_START_DELIMITER + "\n" + content + "\n" + CONTENT_END_DELIMITER;
  }

  /**
   * Checks if the given text contains potential prompt injection patterns.
   *
   * @param text the text to check
   * @return true if injection patterns detected
   */
  public boolean containsInjectionAttempt(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }

    boolean detected =
        IGNORE_INSTRUCTIONS_PATTERN.matcher(text).find()
            || ROLE_PLAY_PATTERN.matcher(text).find()
            || INSTRUCTION_LEAK_PATTERN.matcher(text).find()
            || CONTINUATION_PATTERN.matcher(text).find();

    if (detected) {
      log.warn("Potential prompt injection detected in input (length={})", text.length());
    }
    return detected;
  }

  /**
   * Sanitizes content by removing or neutralizing potential injection patterns. Used as a
   * defense-in-depth layer before framing.
   *
   * @param content the content to sanitize
   * @return sanitized content
   */
  public String sanitize(String content) {
    if (content == null) {
      return "";
    }

    String result = content;

    // Neutralize injection patterns by replacing keywords
    result = IGNORE_INSTRUCTIONS_PATTERN.matcher(result).replaceAll("[INJECTION_NEUTRALIZED]");
    result = ROLE_PLAY_PATTERN.matcher(result).replaceAll("[ROLE_PLAY_NEUTRALIZED]");
    result = INSTRUCTION_LEAK_PATTERN.matcher(result).replaceAll("[INSTRUCTION_LEAK_NEUTRALIZED]");
    result = CONTINUATION_PATTERN.matcher(result).replaceAll("[CONTINUATION_NEUTRALIZED]");

    return result;
  }

  /**
   * Builds a safe system prompt with explicit boundaries.
   *
   * @param systemInstructions the system instructions
   * @param userContent the user content (already framed)
   * @return the combined prompt
   */
  public String buildSafePrompt(String systemInstructions, String userContent) {
    return INSTRUCTION_DELIMITER
        + "\n"
        + systemInstructions
        + "\n"
        + INSTRUCTION_DELIMITER
        + "\n\nUser content follows:\n"
        + userContent;
  }
}
