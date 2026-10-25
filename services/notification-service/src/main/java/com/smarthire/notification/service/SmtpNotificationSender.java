package com.smarthire.notification.service;

import com.smarthire.notification.config.NotificationSenderProperties;
import com.smarthire.notification.domain.Notification;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Real email delivery over SMTP.
 *
 * <p>The sender is deliberately quiet: it logs the notification id and failure class only, never
 * recipient addresses or message bodies. Transient failures are retried a bounded number of times
 * before the notification is marked FAILED.
 */
public class SmtpNotificationSender implements NotificationSender {

  private static final Logger log = LoggerFactory.getLogger(SmtpNotificationSender.class);

  private final JavaMailSender mailSender;
  private final NotificationSenderProperties properties;

  public SmtpNotificationSender(
      JavaMailSender mailSender, NotificationSenderProperties properties) {
    this.mailSender = mailSender;
    this.properties = properties;
  }

  @Override
  public void send(Notification notification) throws NotificationSenderException {
    int maxAttempts = Math.max(1, properties.getMaxAttempts());
    long backoffMs = Math.max(0, properties.getRetryBackoffMs());
    Exception lastFailure = null;

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        mailSender.send(buildMessage(notification));
        if (attempt > 1) {
          log.info(
              "Notification {} delivered on attempt {}", notification.getNotificationId(), attempt);
        }
        return;
      } catch (MailException | MessagingException failure) {
        lastFailure = failure;
        log.warn(
            "Notification {} SMTP attempt {}/{} failed ({})",
            notification.getNotificationId(),
            attempt,
            maxAttempts,
            failure.getClass().getSimpleName());
        if (attempt == maxAttempts) {
          break;
        }
        if (backoffMs > 0) {
          try {
            Thread.sleep(backoffMs * attempt);
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new NotificationSenderException("SMTP delivery interrupted", interrupted);
          }
        }
      }
    }

    throw new NotificationSenderException(
        "SMTP delivery failed after " + maxAttempts + " attempt(s)", lastFailure);
  }

  private MimeMessage buildMessage(Notification notification) throws MessagingException {
    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
    helper.setFrom(properties.getSmtp().getFrom());
    helper.setTo(notification.getRecipientEmail());
    helper.setSubject(notification.getSubject());
    helper.setText(notification.getMessage(), false);
    helper.setSentDate(Date.from(Instant.now()));
    return message;
  }
}
