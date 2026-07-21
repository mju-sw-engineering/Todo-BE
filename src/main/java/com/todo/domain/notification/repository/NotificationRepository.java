package com.todo.domain.notification.repository;

import com.todo.domain.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("SELECT n FROM Notification n WHERE n.receiver.id = :receiverId ORDER BY n.id DESC")
    List<Notification> findLatestByReceiverId(@Param("receiverId") Long receiverId, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.receiver.id = :receiverId AND n.id < :cursorId ORDER BY n.id DESC")
    List<Notification> findByCursorId(@Param("receiverId") Long receiverId, @Param("cursorId") Long cursorId, Pageable pageable);

    long countByReceiverIdAndIsRead(Long receiverId, boolean isRead);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.receiver.id = :receiverId AND n.isRead = false")
    void markAllAsRead(@Param("receiverId") Long receiverId);
}
