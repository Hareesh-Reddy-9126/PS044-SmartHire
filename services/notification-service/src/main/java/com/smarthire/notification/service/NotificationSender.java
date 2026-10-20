package com.smarthire.notification.service;

import com.smarthire.notification.domain.Notification;

/**
 * Strategy interface for sending notifications. Different implementations can use different
 * channels (email, SMS, push, etc.) while the service layer remains unchanged.
 *
 * <p>For Review-1, only the logging sender is implemented (R1 requirement).
 */
public interface NotificationSender {

  /**
   * Sends the given notification.
   *
   * @param notification the notification to send
   * @throws NotificationSenderException if sending fails
   */
  void send(Notification notification) throws NotificationSenderException;
}
