package com.smarthire.notification.service;

import com.smarthire.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logging implementation of {@link NotificationSender}. Writes the notification content to the log
 * at INFO level. Used for Review-1 to satisfy the notification requirement without external
 * dependencies.
 *
 * <p>In production, this would be replaced with an actual email/SMS sender.
 */
@Component
public class LoggingNotificationSender implements NotificationSender {

  private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

  @Override
  public void send(Notification notification) {
    log.info(
        "Sending notification {} to {}: subject='{}'",
        notification.getNotificationId(),
        notification.getRecipientEmail(),
        notification.getSubject());
    log.debug(
        "Notification {} full message: {}",
        notification.getNotificationId(),
        notification.getMessage());
  }
}
