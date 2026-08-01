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

    /**
     * 탈퇴자가 받은 알림은 본인만 보던 개인 데이터이므로 삭제한다.
     * receiver_id FK는 RESTRICT이므로 이 정리를 빠뜨리면 users 삭제가 FK 위반으로 실패한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.receiver.id = :receiverId")
    int deleteByReceiverId(@Param("receiverId") Long receiverId);
}
