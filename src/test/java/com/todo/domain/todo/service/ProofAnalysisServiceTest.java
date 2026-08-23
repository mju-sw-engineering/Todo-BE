package com.todo.domain.todo.service;

import com.todo.domain.team.entity.Team;
import com.todo.domain.todo.entity.ProofAiAnalysis;
import com.todo.domain.todo.entity.ProofAnalysisStatus;
import com.todo.domain.todo.entity.ProofKind;
import com.todo.domain.todo.entity.ProofVerdict;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoMode;
import com.todo.domain.todo.entity.TodoWorkItem;
import com.todo.domain.todo.repository.ProofAiAnalysisRepository;
import com.todo.global.ai.AiClientException;
import com.todo.global.ai.AiStructuredRequest;
import com.todo.global.ai.OpenAiClient;
import com.todo.global.ai.OpenAiProperties;
import com.todo.global.file.extract.DocumentExtractionException;
import com.todo.global.file.extract.DocumentTextExtractor;
import com.todo.global.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ProofAnalysisServiceTest {

    private static final Long ANALYSIS_ID = 7L;

    @InjectMocks
    private ProofAnalysisService proofAnalysisService;

    @Mock
    private ProofAiAnalysisRepository proofAiAnalysisRepository;
    @Mock
    private OpenAiClient openAiClient;
    @Mock
    private OpenAiProperties openAiProperties;
    @Mock
    private ProofPromptProvider promptProvider;
    @Mock
    private FileService fileService;
    @Mock
    private ProofAnalysisNotifier notifier;
    @Mock
    private DocumentTextExtractor documentTextExtractor;

    @BeforeEach
    void setUp() {
        lenient().when(openAiProperties.model()).thenReturn("gpt-5.6-luna");
        lenient().when(promptProvider.systemInstruction(any())).thenReturn("판정 지침");
        lenient().when(fileService.readObject(anyString())).thenReturn(new byte[]{1, 2, 3});
    }

    @Test
    void 부합_판정은_결과를_저장하고_후속_통지를_맡긴다() {
        ProofAiAnalysis analysis = givenPendingImageAnalysis();
        givenModelResponse("VERIFIED", "짜장면을 먹은 사진입니다.", "");

        proofAnalysisService.analyze(ANALYSIS_ID);

        assertThat(analysis.getStatus()).isEqualTo(ProofAnalysisStatus.DONE);
        assertThat(analysis.getVerdict()).isEqualTo(ProofVerdict.VERIFIED);
        assertThat(analysis.getSummary()).isEqualTo("짜장면을 먹은 사진입니다.");
        assertThat(analysis.getModel()).isEqualTo("gpt-5.6-luna");
        then(notifier).should().afterAnalyzed(analysis);
    }

    @Test
    void 불일치_판정은_사유까지_저장한다() {
        ProofAiAnalysis analysis = givenPendingImageAnalysis();
        givenModelResponse("MISMATCH", "칫솔 사진입니다.", "할 일과 다른 사진으로 보여요.");

        proofAnalysisService.analyze(ANALYSIS_ID);

        assertThat(analysis.getVerdict()).isEqualTo(ProofVerdict.MISMATCH);
        assertThat(analysis.getMismatchReason()).isEqualTo("할 일과 다른 사진으로 보여요.");
        then(notifier).should().afterAnalyzed(analysis);
    }

    @Test
    void 알_수_없는_verdict는_불이익이_없는_UNCERTAIN으로_떨어뜨린다() {
        ProofAiAnalysis analysis = givenPendingImageAnalysis();
        givenModelResponse("SOMETHING_ELSE", "요약", "");

        proofAnalysisService.analyze(ANALYSIS_ID);

        assertThat(analysis.getVerdict()).isEqualTo(ProofVerdict.UNCERTAIN);
        assertThat(analysis.getStatus()).isEqualTo(ProofAnalysisStatus.DONE);
    }

    @Test
    void 이미지는_presigned_URL이_아니라_base64로_전달한다() {
        givenPendingImageAnalysis();
        givenModelResponse("VERIFIED", "요약", "");

        proofAnalysisService.analyze(ANALYSIS_ID);

        ArgumentCaptor<AiStructuredRequest> captor = ArgumentCaptor.forClass(AiStructuredRequest.class);
        then(openAiClient).should().generateStructured(captor.capture(), any(), anyString());
        AiStructuredRequest request = captor.getValue();
        assertThat(request.hasImage()).isTrue();
        assertThat(request.imageDataUrl()).startsWith("data:image/jpeg;base64,");
        // 할 일 제목이 판단 근거로 함께 전달돼야 한다.
        assertThat(request.userText()).contains("점심 먹기");
        // 할 일 텍스트도 팀원이 쓴 입력이라 문서처럼 구분자로 감싼다.
        assertThat(request.userText()).startsWith("<task>").endsWith("</task>");
        then(fileService).should().readObject("proofs/1/10/1/a.jpg");
    }

    @Test
    void 문서는_추출한_텍스트를_구분자로_감싸_전달한다() {
        ProofAiAnalysis analysis = givenPendingDocumentAnalysis();
        given(documentTextExtractor.extract(eq("application/pdf"), any()))
                .willReturn("회의록 본문");
        givenModelResponse("VERIFIED", "회의록입니다.", "");

        proofAnalysisService.analyze(ANALYSIS_ID);

        ArgumentCaptor<AiStructuredRequest> captor = ArgumentCaptor.forClass(AiStructuredRequest.class);
        then(openAiClient).should().generateStructured(captor.capture(), any(), anyString());
        AiStructuredRequest request = captor.getValue();
        // 파일을 통째로 보내지 않는다. 문서 본문은 신뢰할 수 없는 입력이라 구분자로 감싼다.
        assertThat(request.hasImage()).isFalse();
        assertThat(request.userText()).contains("<document>\n회의록 본문\n</document>");
        assertThat(analysis.getVerdict()).isEqualTo(ProofVerdict.VERIFIED);
    }

    @Test
    void 문서_추출_실패는_재시도하지_않고_즉시_확정한다() {
        // 깨진 파일이나 암호가 걸린 PDF는 다시 시도해도 같은 결과다.
        ProofAiAnalysis analysis = givenPendingDocumentAnalysis();
        given(documentTextExtractor.extract(anyString(), any()))
                .willThrow(new DocumentExtractionException("암호가 걸린 PDF"));

        proofAnalysisService.analyze(ANALYSIS_ID);

        assertThat(analysis.getStatus()).isEqualTo(ProofAnalysisStatus.FAILED);
        assertThat(analysis.getAttemptCount()).isZero();
        then(openAiClient).shouldHaveNoInteractions();
        then(notifier).should(never()).afterAnalyzed(any());
    }

    @Test
    void 일시적_실패는_백오프_후_재시도로_남긴다() {
        ProofAiAnalysis analysis = givenPendingImageAnalysis();
        given(openAiClient.generateStructured(any(), any(), anyString()))
                .willThrow(AiClientException.retryable("타임아웃", null));

        proofAnalysisService.analyze(ANALYSIS_ID);

        assertThat(analysis.getStatus()).isEqualTo(ProofAnalysisStatus.PENDING);
        assertThat(analysis.getAttemptCount()).isEqualTo(1);
        then(notifier).should(never()).afterAnalyzed(any());
    }

    @Test
    void 영구_실패는_재시도하지_않고_즉시_확정한다() {
        ProofAiAnalysis analysis = givenPendingImageAnalysis();
        given(openAiClient.generateStructured(any(), any(), anyString()))
                .willThrow(AiClientException.permanent("키 오류", null));

        proofAnalysisService.analyze(ANALYSIS_ID);

        assertThat(analysis.getStatus()).isEqualTo(ProofAnalysisStatus.FAILED);
        assertThat(analysis.getAttemptCount()).isZero();
        then(notifier).should(never()).afterAnalyzed(any());
    }

    @Test
    void 파일을_읽지_못하면_재시도_대상으로_남긴다() {
        ProofAiAnalysis analysis = givenPendingImageAnalysis();
        given(fileService.readObject(anyString())).willThrow(new IllegalStateException("S3 장애"));

        proofAnalysisService.analyze(ANALYSIS_ID);

        assertThat(analysis.getStatus()).isEqualTo(ProofAnalysisStatus.PENDING);
        assertThat(analysis.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void 이미_처리된_건은_다시_호출하지_않는다() {
        ProofAiAnalysis analysis = pendingAnalysis();
        analysis.complete(ProofVerdict.VERIFIED, "요약", null, "gpt-5.6-luna");
        given(proofAiAnalysisRepository.findByIdForUpdate(ANALYSIS_ID)).willReturn(Optional.of(analysis));

        proofAnalysisService.analyze(ANALYSIS_ID);

        then(openAiClient).shouldHaveNoInteractions();
        then(notifier).shouldHaveNoInteractions();
    }

    @Test
    void 없는_건은_조용히_넘어간다() {
        given(proofAiAnalysisRepository.findByIdForUpdate(ANALYSIS_ID)).willReturn(Optional.empty());

        proofAnalysisService.analyze(ANALYSIS_ID);

        then(openAiClient).shouldHaveNoInteractions();
    }

    private ProofAiAnalysis givenPendingDocumentAnalysis() {
        Team team = Team.create("팀", null, "ABCDEFGH");
        ReflectionTestUtils.setField(team, "id", 1L);
        com.todo.domain.user.entity.User user = com.todo.domain.user.entity.User.create(
                "user1", "encoded", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Todo todo = Todo.create(team, user, "회의록 정리하기", null,
                LocalDateTime.now().plusDays(1), TodoMode.DIRECT);
        ReflectionTestUtils.setField(todo, "id", 10L);
        TodoWorkItem workItem = TodoWorkItem.createDirect(todo, user);
        ReflectionTestUtils.setField(workItem, "id", 20L);
        workItem.submit("proofs/1/10/1/a.pdf", null, "application/pdf", "회의록.pdf");
        ProofAiAnalysis analysis = ProofAiAnalysis.pending(workItem, ProofKind.DOCUMENT, LocalDateTime.now());
        given(proofAiAnalysisRepository.findByIdForUpdate(ANALYSIS_ID)).willReturn(Optional.of(analysis));
        return analysis;
    }

    private ProofAiAnalysis givenPendingImageAnalysis() {
        ProofAiAnalysis analysis = pendingAnalysis();
        given(proofAiAnalysisRepository.findByIdForUpdate(ANALYSIS_ID)).willReturn(Optional.of(analysis));
        return analysis;
    }

    private void givenModelResponse(String verdict, String summary, String mismatchReason) {
        given(openAiClient.generateStructured(any(), eq(ProofAnalysisService.VerdictResponse.class), anyString()))
                .willReturn(new ProofAnalysisService.VerdictResponse("관찰 내용", verdict, summary, mismatchReason));
    }

    private ProofAiAnalysis pendingAnalysis() {
        Team team = Team.create("팀", null, "ABCDEFGH");
        ReflectionTestUtils.setField(team, "id", 1L);
        com.todo.domain.user.entity.User user = com.todo.domain.user.entity.User.create(
                "user1", "encoded", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Todo todo = Todo.create(team, user, "점심 먹기", null,
                LocalDateTime.now().plusDays(1), TodoMode.DIRECT);
        ReflectionTestUtils.setField(todo, "id", 10L);
        TodoWorkItem workItem = TodoWorkItem.createDirect(todo, user);
        ReflectionTestUtils.setField(workItem, "id", 20L);
        workItem.submit("proofs/1/10/1/a.jpg", "proofs/1/10/1/thumbs/a.jpg", "image/jpeg", "인증.jpg");
        return ProofAiAnalysis.pending(workItem, ProofKind.IMAGE, LocalDateTime.now());
    }

    @Test
    void 롤백된_건의_재시도_횟수를_새_트랜잭션에서_올린다() {
        // analyze()의 catch가 올린 횟수는 트랜잭션이 rollback-only면 함께 사라진다.
        // 여기서 전진시키지 않으면 MAX_ATTEMPTS에 닿지 못해 유료 호출이 무한 반복된다.
        ProofAiAnalysis analysis = pendingAnalysis();
        given(proofAiAnalysisRepository.findById(ANALYSIS_ID)).willReturn(Optional.of(analysis));

        proofAnalysisService.recordDispatchFailure(ANALYSIS_ID);

        assertThat(analysis.getAttemptCount()).isEqualTo(1);
        assertThat(analysis.getStatus()).isEqualTo(ProofAnalysisStatus.PENDING);
    }

    @Test
    void 횟수를_다_쓰면_FAILED로_확정해_반복을_멈춘다() {
        ProofAiAnalysis analysis = pendingAnalysis();
        ReflectionTestUtils.setField(analysis, "attemptCount", 4);
        given(proofAiAnalysisRepository.findById(ANALYSIS_ID)).willReturn(Optional.of(analysis));

        proofAnalysisService.recordDispatchFailure(ANALYSIS_ID);

        assertThat(analysis.getAttemptCount()).isEqualTo(5);
        assertThat(analysis.getStatus()).isEqualTo(ProofAnalysisStatus.FAILED);
    }

    @Test
    void 이미_확정된_건은_횟수를_올리지_않는다() {
        // 판정은 커밋됐는데 그 뒤 다른 이유로 예외가 난 경우다. 여기서 횟수를 올리면
        // 멀쩡히 끝난 건이 재시도 대상처럼 보인다.
        ProofAiAnalysis analysis = pendingAnalysis();
        analysis.complete(ProofVerdict.VERIFIED, "요약", null, "gpt-5.6-luna");
        given(proofAiAnalysisRepository.findById(ANALYSIS_ID)).willReturn(Optional.of(analysis));

        proofAnalysisService.recordDispatchFailure(ANALYSIS_ID);

        assertThat(analysis.getAttemptCount()).isZero();
        assertThat(analysis.getStatus()).isEqualTo(ProofAnalysisStatus.DONE);
    }

    @Test
    void 사라진_건이면_아무것도_하지_않는다() {
        // 제출 취소로 행이 지워졌을 수 있다.
        given(proofAiAnalysisRepository.findById(ANALYSIS_ID)).willReturn(Optional.empty());

        proofAnalysisService.recordDispatchFailure(ANALYSIS_ID);
    }

    @Test
    void 설정된_타임아웃은_클라이언트가_관리한다() {
        // 이 서비스는 타임아웃을 직접 다루지 않는다. 폴러 스레드를 지키는 책임은
        // openAiRestClient 빈에 있고, 여기서는 설정이 존재한다는 것만 확인한다.
        assertThat(new OpenAiProperties("k", "https://x/v1", "m", "low", 400, null, null).readTimeout())
                .isEqualTo(Duration.ofSeconds(30));
    }
}
