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

    private static final int MAX_PROOF_FILE_NAME_LENGTH = 255;

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

    /** 이미지뿐 아니라 문서(PDF·DOCX·XLSX·CSV·HWP·HWPX)도 담는다. 컬럼명은 하위호환으로 유지한다. */
    @Column(unique = true)
    private String proofImageKey;

    /** 이미지 제출에만 채워진다. 문서는 썸네일을 만들지 않는다. */
    private String proofThumbnailKey;

    /** 제출 검증 때 S3 HEAD로 확정한 값. 미리보기·요약 분기는 확장자가 아니라 이 값을 따른다. */
    @Column(length = 100)
    private String proofContentType;

    /** 카드에 보여줄 원본 파일명. 오브젝트 키는 UUID라 사람이 알아볼 수 없다. */
    private String proofFileName;

    private LocalDateTime submittedAt;

    /**
     * TODO_DEADLINE_APPROACHING 발송 여부. 스케줄러가 60초마다 도는데 이 값이 없으면
     * 마감 30분 전 구간 내내 매 tick마다 중복 발송된다. 벌크 UPDATE로만 채워진다.
     */
    private LocalDateTime deadlineReminderSentAt;

    /** 마감 전 최소 한 번 이상 재제출됐는지. 최초 제출로는 절대 true가 되지 않는다. */
    @Column(nullable = false)
    private boolean resubmitted;

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
        submit(proofImageKey, null, null, null);
    }

    /**
     * 마감 전이면 이미 제출된 WorkItem도 다시 호출해 파일을 덮어쓸 수 있다(재제출).
     * FAIL만 거부한다 — FAIL은 스케줄러가 마감 경과 시에만 세팅하므로, 이 가드는
     * 마감 체크(서비스 레이어)와 별개 경로로 FAIL이 들어와도 안전하게 막는 방어선이다.
     */
    public void submit(
            String proofImageKey,
            String proofThumbnailKey,
            String proofContentType,
            String proofFileName
    ) {
        if (this.status == WorkItemStatus.FAIL) {
            throw new BusinessException("이미 종료된 WorkItem입니다.", HttpStatus.CONFLICT);
        }
        if (this.status == WorkItemStatus.SUCCESS) {
            this.resubmitted = true;
        }
        this.proofImageKey = proofImageKey;
        this.proofThumbnailKey = proofThumbnailKey;
        this.proofContentType = proofContentType;
        this.proofFileName = sanitizeFileName(proofFileName);
        this.status = WorkItemStatus.SUCCESS;
        this.submittedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public ProofKind getProofKind() {
        return ProofKind.from(proofContentType);
    }

    /**
     * 파일명은 클라이언트가 보낸 값이라 그대로 저장하지 않는다. 경로 구분자를 지우고
     * 컬럼 길이에 맞춰 자른다 — 화면 표시 용도이므로 잘려도 기능에는 지장이 없다.
     */
    private static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String sanitized = fileName.strip().replaceAll("[/\\\\]", "");
        if (sanitized.isBlank()) {
            return null;
        }
        return sanitized.length() > MAX_PROOF_FILE_NAME_LENGTH
                ? sanitized.substring(0, MAX_PROOF_FILE_NAME_LENGTH)
                : sanitized;
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
