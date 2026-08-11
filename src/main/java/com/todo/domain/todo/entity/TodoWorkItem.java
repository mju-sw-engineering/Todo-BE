package com.todo.domain.todo.entity;

import com.todo.domain.user.entity.User;
import com.todo.global.entity.BaseTimeEntity;
import com.todo.global.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "todo_work_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TodoWorkItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "todo_id", nullable = false)
    private Todo todo;

    /**
     * 완료·실패 행의 null은 탈퇴 익명화이고, 진행 중 행의 null은 미배정이다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkItemType type;

    @Column(name = "task_title")
    private String taskTitle;

    @Column(name = "task_description")
    private String taskDescription;

    private LocalDateTime deadline;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkItemStatus status;

    @Column(unique = true)
    private String proofImageKey;

    private String proofThumbnailKey;

    private LocalDateTime submittedAt;

    public static TodoWorkItem createDirect(Todo todo, User assignee) {
        TodoWorkItem workItem = new TodoWorkItem();
        workItem.todo = todo;
        workItem.assignee = assignee;
        workItem.type = WorkItemType.DIRECT;
        workItem.position = 0;
        workItem.status = WorkItemStatus.IN_PROGRESS;
        return workItem;
    }

    public static TodoWorkItem createTask(
            Todo todo,
            User assignee,
            String taskTitle,
            String taskDescription,
            LocalDateTime deadline,
            int position
    ) {
        validateTask(todo, taskTitle, deadline);

        TodoWorkItem workItem = new TodoWorkItem();
        workItem.todo = todo;
        workItem.assignee = assignee;
        workItem.type = WorkItemType.TASK;
        workItem.taskTitle = taskTitle;
        workItem.taskDescription = taskDescription;
        workItem.deadline = deadline;
        workItem.position = position;
        workItem.status = WorkItemStatus.IN_PROGRESS;
        return workItem;
    }

    public void submit(String proofImageKey) {
        submit(proofImageKey, null);
    }

    public void submit(String proofImageKey, String proofThumbnailKey) {
        if (this.status != WorkItemStatus.IN_PROGRESS) {
            throw new BusinessException("이미 제출되었거나 완료된 투두입니다.", HttpStatus.CONFLICT);
        }
        this.proofImageKey = proofImageKey;
        this.proofThumbnailKey = proofThumbnailKey;
        this.status = WorkItemStatus.SUCCESS;
        this.submittedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public void markAsFail() {
        if (this.status == WorkItemStatus.IN_PROGRESS) {
            this.status = WorkItemStatus.FAIL;
        }
    }

    public void markAsSuccess() {
        if (this.status == WorkItemStatus.IN_PROGRESS) {
            this.status = WorkItemStatus.SUCCESS;
        }
    }

    public void unassign() {
        this.assignee = null;
    }

    public void assign(User assignee) {
        this.assignee = assignee;
    }

    public LocalDateTime getEffectiveDeadline() {
        return deadline != null ? deadline : todo.getDeadline();
    }

    private static void validateTask(Todo todo, String taskTitle, LocalDateTime deadline) {
        if (taskTitle == null || taskTitle.isBlank()) {
            throw new BusinessException("Task 제목은 필수입니다.", HttpStatus.BAD_REQUEST);
        }
        if (deadline != null && deadline.isAfter(todo.getDeadline())) {
            throw new BusinessException("Task 마감일은 Todo 마감일을 넘을 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
    }
}
