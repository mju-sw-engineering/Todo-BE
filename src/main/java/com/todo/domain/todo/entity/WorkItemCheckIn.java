package com.todo.domain.todo.entity;

import com.todo.domain.user.entity.User;
import com.todo.global.entity.BaseTimeEntity;
import com.todo.global.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;

/**
 * 진행 중인 WorkItem에 남기는 하루 1번의 진행 기록.
 * 잔디와 팀 리듬 집계에서 "진행을 남김"의 근거가 된다.
 */
@Entity
@Table(name = "work_item_check_ins",
        uniqueConstraints = @UniqueConstraint(columnNames = {"work_item_id", "user_id", "check_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkItemCheckIn extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_item_id", nullable = false)
    private TodoWorkItem workItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "check_date", nullable = false)
    private LocalDate checkDate;

    @Column(nullable = false, length = 100)
    private String memo;

    public static WorkItemCheckIn create(TodoWorkItem workItem, User user, LocalDate checkDate, String memo) {
        if (memo == null || memo.isBlank()) {
            throw new BusinessException("체크인 메모는 필수입니다.", HttpStatus.BAD_REQUEST);
        }
        if (memo.length() > 100) {
            throw new BusinessException("체크인 메모는 100자를 넘을 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
        WorkItemCheckIn checkIn = new WorkItemCheckIn();
        checkIn.workItem = workItem;
        checkIn.user = user;
        checkIn.checkDate = checkDate;
        checkIn.memo = memo.strip();
        return checkIn;
    }
}
