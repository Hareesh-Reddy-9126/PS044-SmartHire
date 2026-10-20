package com.smarthire.notification.service;

/**
 * Exception thrown by {@link NotificationSender} implementations when sending fails. This is a
 * checked exception to force callers to handle delivery failures.
 */
public class NotificationSenderException extends Exception {

  public NotificationSenderException(String message) {
    super(message);
  }

  public NotificationSenderException(String message, Throwable cause) {
    super(message, cause);
  }
}
