package com.smarthire.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link PiiRedactionService}.
 *
 * <p>Verifies that common PII patterns are redacted while non-PII text is preserved.
 */
class PiiRedactionServiceTest {

  private final PiiRedactionService redactionService = new PiiRedactionService();

  @Test
  void redactsEmailAddresses() {
    String input = "Contact me at john.doe@example.com for more info.";
    String result = redactionService.redact(input);

    assertThat(result).contains("[EMAIL_REDACTED]");
    assertThat(result).doesNotContain("john.doe@example.com");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "john.doe@example.com",
        "jane.smith+tag@company.org",
        "user.name@sub.domain.co.uk",
        "test+filter@domain.io"
      })
  void redactsVariousEmailFormats(String email) {
    String result = redactionService.redact("Email: " + email);
    assertThat(result).contains("[EMAIL_REDACTED]");
    assertThat(result).doesNotContain(email);
  }

  @Test
  void redactsPhoneNumbers() {
    String input = "Call me at (555) 123-4567 or 555-123-4567.";
    String result = redactionService.redact(input);

    assertThat(result).contains("[PHONE_REDACTED]");
    assertThat(result).doesNotContain("(555) 123-4567");
    assertThat(result).doesNotContain("555-123-4567");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "555-123-4567",
        "(555) 123-4567",
        "555.123.4567",
        "555 123 4567",
        "+1-555-123-4567",
        "+1 (555) 123-4567"
      })
  void redactsVariousPhoneFormats(String phone) {
    String result = redactionService.redact("Phone: " + phone);
    assertThat(result).contains("[PHONE_REDACTED]");
    assertThat(result).doesNotContain(phone);
  }

  @Test
  void redactsSSN() {
    String input = "My SSN is 123-45-6789.";
    String result = redactionService.redact(input);

    assertThat(result).contains("[SSN_REDACTED]");
    assertThat(result).doesNotContain("123-45-6789");
  }

  @Test
  void redactsCreditCard() {
    String input = "Card: 4111 1111 1111 1111";
    String result = redactionService.redact(input);

    assertThat(result).contains("[CREDIT_CARD_REDACTED]");
    assertThat(result).doesNotContain("4111 1111 1111 1111");
  }

  @Test
  void redactsAddress() {
    String input = "I live at 123 Main Street.";
    String result = redactionService.redact(input);

    assertThat(result).contains("[ADDRESS_REDACTED]");
    assertThat(result).doesNotContain("123 Main Street");
  }

  @Test
  void redactsNamesHeuristic() {
    String input = "John Smith applied for the position.";
    String result = redactionService.redact(input);

    // Note: Name redaction is heuristic and may have false positives/negatives
    assertThat(result).contains("[NAME_REDACTED]");
  }

  @Test
  void preservesTechnicalTerms() {
    String input = "Experience with Java, Spring Boot, PostgreSQL, Docker, Kubernetes, AWS.";
    String result = redactionService.redact(input);

    // Technical terms should NOT be redacted
    assertThat(result).contains("Java");
    assertThat(result).contains("Spring Boot");
    assertThat(result).contains("PostgreSQL");
    assertThat(result).contains("Docker");
    assertThat(result).contains("Kubernetes");
    assertThat(result).contains("AWS");
    assertThat(result).doesNotContain("[NAME_REDACTED]");
  }

  @Test
  void handlesNullAndEmpty() {
    assertThat(redactionService.redact(null)).isEmpty();
    assertThat(redactionService.redact("")).isEmpty();
    assertThat(redactionService.redact("   ")).isEmpty();
  }

  @Test
  void preservesNonPiiText() {
    String input = "The candidate has 5 years of experience in backend development.";
    String result = redactionService.redact(input);

    assertThat(result).isEqualTo(input);
  }

  @Test
  void redactsMultiplePiiInOneText() {
    String input = "John Doe, email john@example.com, phone 555-123-4567, SSN 123-45-6789";
    String result = redactionService.redact(input);

    assertThat(result).contains("[NAME_REDACTED]");
    assertThat(result).contains("[EMAIL_REDACTED]");
    assertThat(result).contains("[PHONE_REDACTED]");
    assertThat(result).contains("[SSN_REDACTED]");
    assertThat(result).doesNotContain("John Doe");
    assertThat(result).doesNotContain("john@example.com");
    assertThat(result).doesNotContain("555-123-4567");
    assertThat(result).doesNotContain("123-45-6789");
  }
}
