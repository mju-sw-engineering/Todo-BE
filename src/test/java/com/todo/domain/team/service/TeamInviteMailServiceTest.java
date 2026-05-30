package com.todo.domain.team.service;

import com.todo.domain.team.entity.Team;
import com.todo.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class TeamInviteMailServiceTest {

    @InjectMocks
    private TeamInviteMailService teamInviteMailService;

    @Mock
    private JavaMailSender mailSender;

    @Test
    void 초대_메일을_생성해_발송한다() {
        ReflectionTestUtils.setField(teamInviteMailService, "fromEmail", "noreply@example.com");
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        teamInviteMailService.sendInvitations(
                team,
                "https://todo.example.com/teams/join?code=ABCD1234",
                List.of("member@example.com")
        );

        then(mailSender).should().send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("noreply@example.com");
        assertThat(message.getTo()).containsExactly("member@example.com");
        assertThat(message.getSubject()).isEqualTo("[Todo] 스터디 팀 팀 초대");
        assertThat(message.getText()).contains("https://todo.example.com/teams/join?code=ABCD1234");
        assertThat(message.getText()).contains("직접 입력용 초대 코드: ABCD1234");
    }

    @Test
    void 메일_발송_실패시_BusinessException으로_변환한다() {
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        willThrow(new MailSendException("smtp error")).given(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> teamInviteMailService.sendInvitations(
                team,
                "https://todo.example.com/teams/join?code=ABCD1234",
                List.of("member@example.com")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("초대 메일 발송에 실패했습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus())
                        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }
}
