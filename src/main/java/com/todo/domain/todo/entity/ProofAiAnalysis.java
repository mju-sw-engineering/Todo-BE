package com.todo.domain.todo.entity;

import com.todo.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 인증 파일 AI 판정 결과이자 재시도 큐. {@code AppleRevokeOutbox}와 같은 상태 머신을 쓴다.
 *
 * <p>무한 재시도하지 않는다. 재시도로 해소되는 건 일시적 장애뿐이고, 죽은 API 키나 깨진
 * 파일처럼 다시 보내도 같은 결과가 나오는 실패는 계속 시도해봐야 자원만 쓴다.
 * {@link #MAX_ATTEMPTS}를 넘기면 {@link ProofAnalysisStatus#FAILED}로 확정해 대상에서 뺀다.
 *
 * <p>판정이 실패해도 제출은 유효하다. 뱃지와 요약이 붙지 않을 뿐이라, AI가 죽어도 기능이
 * 저하될 뿐 마비되지 않는다.
 */
@Entity
@Table(name = "proof_ai_analyses",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_proof_ai_analyses_work_item",
                columnNames = "work_item_id"
        ),
        indexes = @Index(
                name = "idx_proof_ai_analyses_status_next",
                columnList = "status, next_attempt_at"
        ))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProofAiAnalysis extends BaseTimeEntity {

    private static final long BASE_BACKOFF_SECONDS = 60;
    private static final long MAX_BACKOFF_SECONDS = 3600;
    static final int MAX_ATTEMPTS = 5;
    private static final int MAX_SUMMARY_LENGTH = 600;
    private static final int MAX_MISMATCH_REASON_LENGTH = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_item_id", nullable = false)
    private TodoWorkItem workItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_kind", nullable = false, columnDefinition = "varchar(20)")
    private ProofKind inputKind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private ProofAnalysisStatus status;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private ProofVerdict verdict;

    @Column(length = MAX_SUMMARY_LENGTH)
    private String summary;

    /** 제출자 본인에게만 노출한다. 팀 브로드캐스트에는 절대 싣지 않는다. */
    @Column(name = "mismatch_reason", length = MAX_MISMATCH_REASON_LENGTH)
    private String mismatchReason;

    @Column(length = 50)
    private String model;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    /** 폴러가 집어갈 분석 대기 행. */
    public static ProofAiAnalysis pending(TodoWorkItem workItem, ProofKind inputKind, LocalDateTime now) {
        ProofAiAnalysis analysis = new ProofAiAnalysis();
        analysis.workItem = workItem;
        analysis.inputKind = inputKind;
        analysis.status = ProofAnalysisStatus.PENDING;
        analysis.attemptCount = 0;
        analysis.nextAttemptAt = now;
        return analysis;
    }

    /**
     * 분석 대상이 아닌 제출. HWP처럼 내용을 읽을 수 없는 형식이 여기 해당한다.
     * 큐에 태우지 않되, "분석하지 않기로 한 건"과 "아직 분석 전인 건"을 구분하기 위해 행은 남긴다.
     */
    public static ProofAiAnalysis skipped(TodoWorkItem workItem, ProofKind inputKind, LocalDateTime now) {
        ProofAiAnalysis analysis = new ProofAiAnalysis();
        analysis.workItem = workItem;
        analysis.inputKind = inputKind;
        analysis.status = ProofAnalysisStatus.SKIPPED;
        analysis.attemptCount = 0;
        analysis.nextAttemptAt = now;
        return analysis;
    }

    public boolean isPending() {
        return status == ProofAnalysisStatus.PENDING;
    }

    public void complete(ProofVerdict verdict, String summary, String mismatchReason, String model) {
        this.status = ProofAnalysisStatus.DONE;
        this.verdict = verdict;
        this.summary = normalize(summary, MAX_SUMMARY_LENGTH);
        // 사유는 불일치로 판정했을 때만 의미가 있다. 다른 verdict에 남아 있으면
        // 노출 조건이 하나 늘어날 뿐이라 여기서 버린다.
        this.mismatchReason = verdict == ProofVerdict.MISMATCH
                ? normalize(mismatchReason, MAX_MISMATCH_REASON_LENGTH)
                : null;
        this.model = model;
    }

    /** 재시도해도 같은 결과가 나오는 실패. 즉시 확정한다. */
    public void failPermanently() {
        this.status = ProofAnalysisStatus.FAILED;
    }

    /** 일시적 실패. 백오프 후 다시 시도하되 횟수를 넘기면 확정한다. */
    public void recordRetryableFailure(LocalDateTime now) {
        this.attemptCount++;
        if (attemptCount >= MAX_ATTEMPTS) {
            this.status = ProofAnalysisStatus.FAILED;
            return;
        }
        this.nextAttemptAt = now.plusSeconds(backoffSeconds());
    }

    /**
     * 팀 전체에 노출해도 되는 요약인지. UNCERTAIN은 모델이 근거를 못 찾은 건인데,
     * 실측에서 근거가 없을 때 없는 내용을 지어내는 것을 확인했다. 신뢰도가 낮은 요약을
     * 팀에 보여주느니 아무것도 보여주지 않는 편이 낫다.
     */
    public boolean hasTeamVisibleSummary() {
        return status == ProofAnalysisStatus.DONE
                && verdict != ProofVerdict.UNCERTAIN
                && summary != null;
    }

    public boolean isVerified() {
        return status == ProofAnalysisStatus.DONE && verdict == ProofVerdict.VERIFIED;
    }

    private long backoffSeconds() {
        long factor = 1L << Math.min(attemptCount - 1, 20);
        return Math.min(BASE_BACKOFF_SECONDS * factor, MAX_BACKOFF_SECONDS);
    }

    /** 모델 출력은 길이도 개행도 보장되지 않는다. 컬럼에 넣기 전에 다듬는다. */
    private static String normalize(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.replaceAll("\\s+", " ").strip();
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }
}
