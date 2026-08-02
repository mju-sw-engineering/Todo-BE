package com.todo.domain.notification.repository;

import com.todo.domain.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 응답 매핑에서 actor 닉네임으로 문구를 치환하므로 함께 가져와 N+1을 막는다.
    // LEFT JOIN이어야 한다. 암묵 경로 조인은 INNER JOIN으로 번역되어 actor가 null인 행
    // (행위자 탈퇴, 시스템 알림, 구조 도입 이전 데이터)을 통째로 떨어뜨린다.
    @Query("""
            SELECT n FROM Notification n
            LEFT JOIN FETCH n.actor
            WHERE n.receiver.id = :receiverId
            ORDER BY n.id DESC
            """)
    List<Notification> findLatestByReceiverId(@Param("receiverId") Long receiverId, Pageable pageable);

    @Query("""
            SELECT n FROM Notification n
            LEFT JOIN FETCH n.actor
            WHERE n.receiver.id = :receiverId AND n.id < :cursorId
            ORDER BY n.id DESC
            """)
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

    /**
     * 탈퇴자가 유발한 알림은 다른 팀원이 받은 것이므로 삭제하지 않고 행위자 관계만 끊는다.
     * 조회 시점에 {@code 탈퇴한 사용자}로 렌더링된다.
     *
     * <p>actor_id FK도 RESTRICT이므로 이 정리를 빠뜨리면 users 삭제가 FK 위반으로 실패한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.actor = null WHERE n.actor.id = :actorId")
    int clearActorByUserId(@Param("actorId") Long actorId);
}
