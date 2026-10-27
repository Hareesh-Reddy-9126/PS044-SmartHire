package com.smarthire.notification.config;

import com.smarthire.notification.service.LoggingNotificationSender;
import com.smarthire.notification.service.NotificationSender;
import com.smarthire.notification.service.ResendNotificationSender;
import com.smarthire.notification.service.SmtpNotificationSender;
import java.time.Duration;
import java.util.Properties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.client.RestClient;

/**
 * Chooses the {@link NotificationSender} implementation from configuration.
 *
 * <p>Production uses {@code smtp} or {@code resend}; local development and tests use {@code
 * logging}. An unknown sender type fails startup instead of silently falling back to logging.
 */
@Configuration
@EnableConfigurationProperties(NotificationSenderProperties.class)
public class NotificationSenderConfig {

  @Bean
  public NotificationSender notificationSender(NotificationSenderProperties properties) {
    return switch (properties.getType().toLowerCase(java.util.Locale.ROOT)) {
      case "logging" -> new LoggingNotificationSender();
      case "smtp" -> new SmtpNotificationSender(buildMailSender(properties), properties);
      case "resend" -> new ResendNotificationSender(buildResendClient(properties), properties);
      default ->
          throw new IllegalStateException(
              "Unsupported notification sender type: "
                  + properties.getType()
                  + " (supported: logging, smtp, resend)");
    };
  }

  private RestClient buildResendClient(NotificationSenderProperties properties) {
    NotificationSenderProperties.Resend resend = properties.getResend();
    if (resend.getApiKey() == null || resend.getApiKey().isBlank()) {
      throw new IllegalStateException(
          "NOTIFICATION_RESEND_API_KEY is required when NOTIFICATION_SENDER_TYPE=resend");
    }
    if (resend.getFrom() == null || resend.getFrom().isBlank()) {
      throw new IllegalStateException(
          "NOTIFICATION_RESEND_FROM is required when NOTIFICATION_SENDER_TYPE=resend");
    }

    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(resend.getConnectTimeoutMs()));
    requestFactory.setReadTimeout(Duration.ofMillis(resend.getReadTimeoutMs()));

    return RestClient.builder()
        .baseUrl(resend.getBaseUrl())
        .requestFactory(requestFactory)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + resend.getApiKey())
        .build();
  }

  private JavaMailSender buildMailSender(NotificationSenderProperties properties) {
    NotificationSenderProperties.Smtp smtp = properties.getSmtp();
    if (smtp.getHost() == null || smtp.getHost().isBlank()) {
      throw new IllegalStateException(
          "NOTIFICATION_SMTP_HOST is required when NOTIFICATION_SENDER_TYPE=smtp");
    }
    if (smtp.getFrom() == null || smtp.getFrom().isBlank()) {
      throw new IllegalStateException(
          "NOTIFICATION_SMTP_FROM is required when NOTIFICATION_SENDER_TYPE=smtp");
    }

    JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
    mailSender.setHost(smtp.getHost());
    mailSender.setPort(smtp.getPort());
    if (smtp.getUsername() != null && !smtp.getUsername().isBlank()) {
      mailSender.setUsername(smtp.getUsername());
      mailSender.setPassword(smtp.getPassword());
    }

    Properties javaMail = mailSender.getJavaMailProperties();
    javaMail.setProperty(
        "mail.smtp.auth",
        String.valueOf(smtp.getUsername() != null && !smtp.getUsername().isBlank()));
    javaMail.setProperty("mail.smtp.starttls.enable", String.valueOf(smtp.isStarttls()));
    javaMail.setProperty("mail.smtp.starttls.required", String.valueOf(smtp.isStarttls()));
    javaMail.setProperty("mail.smtp.ssl.enable", String.valueOf(smtp.isSsl()));
    javaMail.setProperty(
        "mail.smtp.connectiontimeout", String.valueOf(smtp.getConnectionTimeoutMs()));
    javaMail.setProperty("mail.smtp.timeout", String.valueOf(smtp.getTimeoutMs()));
    javaMail.setProperty("mail.smtp.writetimeout", String.valueOf(smtp.getWriteTimeoutMs()));
    return mailSender;
  }
}
