package com.todo.domain.todo.dto.response;

import com.todo.domain.team.entity.Team;
import com.todo.domain.todo.entity.ProofAiAnalysis;
import com.todo.domain.todo.entity.ProofAnalysisStatus;
import com.todo.domain.todo.entity.ProofKind;
import com.todo.domain.todo.entity.ProofVerdict;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoMode;
import com.todo.domain.todo.entity.TodoWorkItem;
import com.todo.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이 클래스가 지키는 경계는 하나다 — 불일치 사유는 제출자 본인에게만 간다.
 * 팀원 화면에 사유가 보이면 오탐 한 번이 팀 앞에서 팀원을 몰아세우는 일이 된다.
 */
class ProofAiAnalysisResponseTest {

    private static final Long SUBMITTER_ID = 1L;
    private static final Long TEAMMATE_ID = 2L;

    @Test
    void 불일치_사유는_제출자_본인에게만_내려간다() {
        ProofAiAnalysis analysis = analysis(ProofVerdict.MISMATCH, "칫솔 사진입니다.", "할 일과 다른 사진으로 보여요.");

        ProofAiAnalysisResponse forSubmitter = ProofAiAnalysisResponse.from(analysis, SUBMITTER_ID);
        ProofAiAnalysisResponse forTeammate = ProofAiAnalysisResponse.from(analysis, TEAMMATE_ID);

        assertThat(forSubmitter.mismatchReason()).isEqualTo("할 일과 다른 사진으로 보여요.");
        assertThat(forTeammate.mismatchReason()).isNull();
        // 팀원도 판정 자체와 요약은 볼 수 있다. 숨기는 건 사유뿐이다.
        assertThat(forTeammate.verdict()).isEqualTo(ProofVerdict.MISMATCH);
        assertThat(forTeammate.summary()).isEqualTo("칫솔 사진입니다.");
    }

    @Test
    void 요청자를_알_수_없으면_사유를_내려주지_않는다() {
        ProofAiAnalysis analysis = analysis(ProofVerdict.MISMATCH, "요약", "사유");

        assertThat(ProofAiAnalysisResponse.from(analysis, null).mismatchReason()).isNull();
    }

    @Test
    void 탈퇴로_익명화된_제출은_누구에게도_사유를_내려주지_않는다() {
        ProofAiAnalysis analysis = analysis(ProofVerdict.MISMATCH, "요약", "사유");
        ReflectionTestUtils.setField(analysis.getWorkItem(), "assignee", null);

        assertThat(ProofAiAnalysisResponse.from(analysis, SUBMITTER_ID).mismatchReason()).isNull();
    }

    @Test
    void 불확실_판정은_요약을_팀에_공개하지_않는다() {
        // 근거가 없을 때 모델이 없는 내용을 지어내는 것을 실측으로 확인했다.
        ProofAiAnalysis analysis = analysis(ProofVerdict.UNCERTAIN, "무언가 보이는 것 같습니다.", null);

        ProofAiAnalysisResponse response = ProofAiAnalysisResponse.from(analysis, TEAMMATE_ID);

        assertThat(response.verdict()).isEqualTo(ProofVerdict.UNCERTAIN);
        assertThat(response.summary()).isNull();
    }

    @Test
    void 부합_판정은_요약을_공개한다() {
        ProofAiAnalysis analysis = analysis(ProofVerdict.VERIFIED, "짜장면 사진입니다.", null);

        ProofAiAnalysisResponse response = ProofAiAnalysisResponse.from(analysis, TEAMMATE_ID);

        assertThat(response.status()).isEqualTo(ProofAnalysisStatus.DONE);
        assertThat(response.verdict()).isEqualTo(ProofVerdict.VERIFIED);
        assertThat(response.summary()).isEqualTo("짜장면 사진입니다.");
        assertThat(response.mismatchReason()).isNull();
    }

    @Test
    void 대기_중이면_상태만_내려간다() {
        ProofAiAnalysis analysis = ProofAiAnalysis.pending(workItem(), ProofKind.IMAGE, LocalDateTime.now());

        ProofAiAnalysisResponse response = ProofAiAnalysisResponse.from(analysis, SUBMITTER_ID);

        assertThat(response.status()).isEqualTo(ProofAnalysisStatus.PENDING);
        assertThat(response.verdict()).isNull();
        assertThat(response.summary()).isNull();
    }

    @Test
    void 판정_대상이_아니거나_행이_없으면_null이다() {
        ProofAiAnalysis skipped = ProofAiAnalysis.skipped(workItem(), ProofKind.DOCUMENT, LocalDateTime.now());

        assertThat(ProofAiAnalysisResponse.from(skipped, SUBMITTER_ID)).isNull();
        assertThat(ProofAiAnalysisResponse.from(null, SUBMITTER_ID)).isNull();
    }

    private ProofAiAnalysis analysis(ProofVerdict verdict, String summary, String mismatchReason) {
        ProofAiAnalysis analysis = ProofAiAnalysis.pending(workItem(), ProofKind.IMAGE, LocalDateTime.now());
        analysis.complete(verdict, summary, mismatchReason, "gpt-5.6-luna");
        return analysis;
    }

    private TodoWorkItem workItem() {
        User submitter = User.create("user1", "encoded", "닉네임", null);
        ReflectionTestUtils.setField(submitter, "id", SUBMITTER_ID);
        Todo todo = Todo.create(Team.create("팀", null, "ABCDEFGH"), submitter, "점심 먹기", null,
                LocalDateTime.now().plusDays(1), TodoMode.DIRECT);
        TodoWorkItem workItem = TodoWorkItem.createDirect(todo, submitter);
        workItem.submit("proofs/1/1/1/a.jpg", null, "image/jpeg", "a.jpg");
        return workItem;
    }
}
