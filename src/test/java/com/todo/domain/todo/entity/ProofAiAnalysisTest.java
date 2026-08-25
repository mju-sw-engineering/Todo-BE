package com.todo.domain.todo.entity;

import com.todo.domain.team.entity.Team;
import com.todo.domain.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ProofAiAnalysisTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 23, 12, 0);

    @Test
    void 대기_행은_즉시_처리_대상이다() {
        ProofAiAnalysis analysis = ProofAiAnalysis.pending(workItem(), ProofKind.IMAGE, NOW);

        assertThat(analysis.getStatus()).isEqualTo(ProofAnalysisStatus.PENDING);
        assertThat(analysis.isPending()).isTrue();
        assertThat(analysis.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(analysis.getAttemptCount()).isZero();
    }

    @Test
    void 건너뛴_행은_폴러_대상이_아니다() {
        ProofAiAnalysis analysis = ProofAiAnalysis.skipped(workItem(), ProofKind.DOCUMENT, NOW);

        assertThat(analysis.getStatus()).isEqualTo(ProofAnalysisStatus.SKIPPED);
        assertThat(analysis.isPending()).isFalse();
    }

    @Test
    void 판정_완료는_결과와_모델을_기록한다() {
        ProofAiAnalysis analysis = ProofAiAnalysis.pending(workItem(), ProofKind.IMAGE, NOW);

        analysis.complete(ProofVerdict.VERIFIED, "짜장면을 먹은 사진입니다.", null, "gpt-5.6-luna");

        assertThat(analysis.getStatus()).isEqualTo(ProofAnalysisStatus.DONE);
        assertThat(analysis.getVerdict()).isEqualTo(ProofVerdict.VERIFIED);
        assertThat(analysis.getSummary()).isEqualTo("짜장면을 먹은 사진입니다.");
        assertThat(analysis.getModel()).isEqualTo("gpt-5.6-luna");
        assertThat(analysis.isVerified()).isTrue();
    }

    @Test
    void 불일치가_아니면_사유를_저장하지_않는다() {
        ProofAiAnalysis analysis = ProofAiAnalysis.pending(workItem(), ProofKind.IMAGE, NOW);

        analysis.complete(ProofVerdict.VERIFIED, "요약", "남아 있으면 안 되는 사유", "gpt-5.6-luna");

        assertThat(analysis.getMismatchReason()).isNull();
    }

    @Test
    void 불일치일_때만_사유를_저장한다() {
        ProofAiAnalysis analysis = ProofAiAnalysis.pending(workItem(), ProofKind.IMAGE, NOW);

        analysis.complete(ProofVerdict.MISMATCH, "칫솔 사진입니다.", "할 일과 다른 사진으로 보여요.", "gpt-5.6-luna");

        assertThat(analysis.getMismatchReason()).isEqualTo("할 일과 다른 사진으로 보여요.");
    }

    @Test
    void 요약은_개행을_정리하고_길이를_제한한다() {
        ProofAiAnalysis analysis = ProofAiAnalysis.pending(workItem(), ProofKind.IMAGE, NOW);

        analysis.complete(ProofVerdict.VERIFIED, "첫 줄\n\n  둘째 줄  ", null, "gpt-5.6-luna");
        assertThat(analysis.getSummary()).isEqualTo("첫 줄 둘째 줄");

        ProofAiAnalysis longOne = ProofAiAnalysis.pending(workItem(), ProofKind.IMAGE, NOW);
        longOne.complete(ProofVerdict.VERIFIED, "가".repeat(700), null, "gpt-5.6-luna");
        assertThat(longOne.getSummary()).hasSize(600);
    }

    @Test
    void 불확실_판정은_팀에_요약을_노출하지_않는다() {
        // 근거가 없을 때 모델이 없는 내용을 지어내는 것을 실측으로 확인했다.
        // 신뢰도가 낮은 요약을 팀에 보여주느니 아무것도 보여주지 않는 편이 낫다.
        ProofAiAnalysis uncertain = ProofAiAnalysis.pending(workItem(), ProofKind.IMAGE, NOW);
        uncertain.complete(ProofVerdict.UNCERTAIN, "무언가 보이는 것 같습니다.", null, "gpt-5.6-luna");

        assertThat(uncertain.hasTeamVisibleSummary()).isFalse();
        assertThat(uncertain.isVerified()).isFalse();

        ProofAiAnalysis verified = ProofAiAnalysis.pending(workItem(), ProofKind.IMAGE, NOW);
        verified.complete(ProofVerdict.VERIFIED, "짜장면 사진입니다.", null, "gpt-5.6-luna");
        assertThat(verified.hasTeamVisibleSummary()).isTrue();
    }

    @Test
    void 재시도_실패는_지수_백오프로_다음_시각을_미룬다() {
        ProofAiAnalysis analysis = ProofAiAnalysis.pending(workItem(), ProofKind.IMAGE, NOW);

        analysis.recordRetryableFailure(NOW);
        assertThat(analysis.getAttemptCount()).isEqualTo(1);
        assertThat(analysis.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(analysis.getStatus()).isEqualTo(ProofAnalysisStatus.PENDING);

        analysis.recordRetryableFailure(NOW);
        assertThat(analysis.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(120));

        analysis.recordRetryableFailure(NOW);
        assertThat(analysis.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(240));
    }

    @Test
    void 재시도_횟수를_넘기면_실패로_확정한다() {
        ProofAiAnalysis analysis = ProofAiAnalysis.pending(workItem(), ProofKind.IMAGE, NOW);

        for (int i = 0; i < 5; i++) {
            analysis.recordRetryableFailure(NOW);
        }

        assertThat(analysis.getStatus()).isEqualTo(ProofAnalysisStatus.FAILED);
        assertThat(analysis.isPending()).isFalse();
    }

    @Test
    void 영구_실패는_재시도하지_않고_즉시_확정한다() {
        ProofAiAnalysis analysis = ProofAiAnalysis.pending(workItem(), ProofKind.IMAGE, NOW);

        analysis.failPermanently();

        assertThat(analysis.getStatus()).isEqualTo(ProofAnalysisStatus.FAILED);
        assertThat(analysis.getAttemptCount()).isZero();
    }

    @Test
    void 재분석_리셋은_이전_판정_기록을_모두_지우고_대기로_되돌린다() {
        ProofAiAnalysis analysis = ProofAiAnalysis.pending(workItem(), ProofKind.IMAGE, NOW);
        analysis.complete(ProofVerdict.MISMATCH, "칫솔 사진입니다.", "할 일과 다른 사진으로 보여요.", "gpt-5.6-luna");
        analysis.recordRetryableFailure(NOW);

        LocalDateTime resubmittedAt = NOW.plusHours(1);
        analysis.resetForReanalysis(ProofKind.DOCUMENT, true, resubmittedAt);

        assertThat(analysis.getInputKind()).isEqualTo(ProofKind.DOCUMENT);
        assertThat(analysis.getStatus()).isEqualTo(ProofAnalysisStatus.PENDING);
        assertThat(analysis.isPending()).isTrue();
        assertThat(analysis.getVerdict()).isNull();
        assertThat(analysis.getSummary()).isNull();
        assertThat(analysis.getMismatchReason()).isNull();
        assertThat(analysis.getModel()).isNull();
        assertThat(analysis.getAttemptCount()).isZero();
        assertThat(analysis.getNextAttemptAt()).isEqualTo(resubmittedAt);
    }

    @Test
    void 재분석_대상이_아니면_SKIPPED로_리셋한다() {
        ProofAiAnalysis analysis = ProofAiAnalysis.pending(workItem(), ProofKind.IMAGE, NOW);
        analysis.complete(ProofVerdict.VERIFIED, "짜장면 사진입니다.", null, "gpt-5.6-luna");

        analysis.resetForReanalysis(ProofKind.DOCUMENT, false, NOW.plusHours(1));

        assertThat(analysis.getStatus()).isEqualTo(ProofAnalysisStatus.SKIPPED);
        assertThat(analysis.isPending()).isFalse();
    }

    private TodoWorkItem workItem() {
        User user = User.create("user1", "encoded", "닉네임", null);
        Todo todo = Todo.create(
                Team.create("팀", null, "ABCDEFGH"), user, "투두", "설명",
                LocalDateTime.now().plusDays(1), TodoMode.DIRECT);
        return TodoWorkItem.createDirect(todo, user);
    }
}
