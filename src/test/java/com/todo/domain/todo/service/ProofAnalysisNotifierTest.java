package com.todo.domain.todo.service;

import com.todo.domain.notification.entity.NotificationType;
import com.todo.domain.notification.message.NotificationMessage;
import com.todo.domain.notification.message.NotificationMessageFactory;
import com.todo.domain.notification.service.NotificationService;
import com.todo.domain.team.entity.Team;
import com.todo.domain.todo.entity.ProofAiAnalysis;
import com.todo.domain.todo.entity.ProofKind;
import com.todo.domain.todo.entity.ProofVerdict;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoMode;
import com.todo.domain.todo.entity.TodoWorkItem;
import com.todo.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ProofAnalysisNotifierTest {

    @InjectMocks
    private ProofAnalysisNotifier notifier;

    @Mock
    private NotificationService notificationService;
    @Mock
    private NotificationMessageFactory notificationMessageFactory;

    @Test
    void 불일치는_제출자_한_명에게만_알린다() {
        User submitter = user(1L);
        ProofAiAnalysis analysis = analysis(submitter, ProofVerdict.MISMATCH);
        NotificationMessage message = new NotificationMessage(
                NotificationType.AI_PROOF_MISMATCH, "제목", "내용");
        given(notificationMessageFactory.aiProofMismatch(anyString())).willReturn(message);

        notifier.afterAnalyzed(analysis);

        // 팀 전체 발송(sendAll)이 아니라 개인 발송(send)이어야 한다.
        // 오탐 한 번이 팀 앞에서 팀원을 몰아세우는 일이 되면 안 된다.
        then(notificationService).should().send(eq(submitter), isNull(), eq(message), eq(20L), eq(5L));
        then(notificationService).should(never()).sendAll(any(), any(), any(), any(), any());
    }

    @Test
    void 부합_판정은_알림을_보내지_않는다() {
        // 정상 제출마다 알림이 오면 소음이 된다. 뱃지로만 드러난다.
        notifier.afterAnalyzed(analysis(user(1L), ProofVerdict.VERIFIED));

        then(notificationService).shouldHaveNoInteractions();
        then(notificationMessageFactory).shouldHaveNoInteractions();
    }

    @Test
    void 불확실_판정은_아무_신호도_주지_않는다() {
        notifier.afterAnalyzed(analysis(user(1L), ProofVerdict.UNCERTAIN));

        then(notificationService).shouldHaveNoInteractions();
    }

    @Test
    void 탈퇴로_익명화된_제출은_알림_대상이_없다() {
        ProofAiAnalysis analysis = analysis(user(1L), ProofVerdict.MISMATCH);
        ReflectionTestUtils.setField(analysis.getWorkItem(), "assignee", null);

        notifier.afterAnalyzed(analysis);

        then(notificationService).shouldHaveNoInteractions();
    }

    private ProofAiAnalysis analysis(User submitter, ProofVerdict verdict) {
        Team team = Team.create("팀", null, "ABCDEFGH");
        ReflectionTestUtils.setField(team, "id", 5L);
        Todo todo = Todo.create(team, submitter, "점심 먹기", null,
                LocalDateTime.now().plusDays(1), TodoMode.DIRECT);
        ReflectionTestUtils.setField(todo, "id", 10L);
        TodoWorkItem workItem = TodoWorkItem.createDirect(todo, submitter);
        ReflectionTestUtils.setField(workItem, "id", 20L);
        workItem.submit("proofs/5/10/1/a.jpg", null, "image/jpeg", "a.jpg");

        ProofAiAnalysis analysis = ProofAiAnalysis.pending(workItem, ProofKind.IMAGE, LocalDateTime.now());
        analysis.complete(verdict, "요약", verdict == ProofVerdict.MISMATCH ? "사유" : null, "gpt-5.6-luna");
        return analysis;
    }

    private User user(Long id) {
        User user = User.create("user" + id, "encoded", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
