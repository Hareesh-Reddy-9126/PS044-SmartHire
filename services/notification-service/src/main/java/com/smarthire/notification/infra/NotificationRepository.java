package com.smarthire.notification.infra;

import com.smarthire.common.contract.notification.DeliveryStatus;
import com.smarthire.notification.domain.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Persistence port for {@link Notification} (Spring Data JPA). Lives in {@code infra} per
 * governance §3.
 *
 * <p>{@link JpaSpecificationExecutor} supplies the paged {@code findAll(Specification, Pageable)}
 * for future listing use; not yet needed for the minimal Review-1 API but included for consistency.
 */
public interface NotificationRepository
    extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {

  Optional<Notification> findByNotificationId(UUID notificationId);

  Page<Notification> findByCandidateId(UUID candidateId, Pageable pageable);

  List<Notification> findByApplicationId(UUID applicationId);

  List<Notification> findByDeliveryStatus(DeliveryStatus deliveryStatus);
}
