package com.smarthire.notification.service;

import com.smarthire.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logging implementation of {@link NotificationSender}. Used for local development and tests so no
 * real email leaves the machine.
 *
 * <p>Production uses {@link SmtpNotificationSender}. Recipient addresses and message bodies are
 * not logged because notification content is candidate personal data.
 */
public class LoggingNotificationSender implements NotificationSender {

  private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

  @Override
  public void send(Notification notification) {
    log.info(
        "Logging sender accepted notification {} (subject and recipient are not logged)",
        notification.getNotificationId());
  }
}
