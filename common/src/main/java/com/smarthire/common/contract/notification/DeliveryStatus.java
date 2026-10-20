package com.smarthire.common.contract.notification;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Delivery status of a notification.
 *
 * <p>Shared contract between services.
 */
@Schema(name = "DeliveryStatus", description = "Delivery status of a notification")
public enum DeliveryStatus {
  @Schema(description = "Notification is queued for delivery")
  PENDING,
  @Schema(description = "Notification has been successfully delivered")
  SENT,
  @Schema(description = "Notification delivery failed")
  FAILED
}
