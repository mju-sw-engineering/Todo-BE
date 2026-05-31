package com.todo.domain.team.service;

import com.todo.domain.team.entity.Team;
import com.todo.global.exception.BusinessException;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Properties;

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
    void 초대_메일을_생성해_발송한다() throws Exception {
        ReflectionTestUtils.setField(teamInviteMailService, "fromEmail", "noreply@example.com");
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        org.mockito.BDDMockito.given(mailSender.createMimeMessage()).willReturn(mimeMessage);

        teamInviteMailService.sendInvitations(
                team,
                "https://todo.example.com/teams/join?code=ABCD1234",
                List.of("member@example.com")
        );

        then(mailSender).should().send(captor.capture());
        MimeMessage message = captor.getValue();
        message.saveChanges();
        assertThat(message.getFrom()[0].toString()).isEqualTo("noreply@example.com");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("member@example.com");
        assertThat(message.getSubject()).isEqualTo("팀 초대가 도착했어요");
        assertThat(message.getContentType()).contains("multipart");
        String content = extractContent(message.getContent());
        assertThat(content).contains("팀 초대가 도착했어요");
        assertThat(content).contains("스터디 팀");
        assertThat(content).contains("아래 버튼을 눌러 로그인한 뒤 팀에 참여해 주세요.");
        assertThat(content).contains("https://todo.example.com/teams/join?code=ABCD1234");
        assertThat(content).contains("#5B4FCF");
        assertThat(content).contains("팀 참여하기");
        assertThat(content).contains("초대 코드");
        assertThat(content).contains("ABCD1234");
    }

    @Test
    void 메일_발송_실패시_BusinessException으로_변환한다() {
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        org.mockito.BDDMockito.given(mailSender.createMimeMessage())
                .willReturn(new MimeMessage(Session.getInstance(new Properties())));
        willThrow(new MailSendException("smtp error")).given(mailSender).send(any(MimeMessage.class));

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

    private String extractContent(Object content) throws Exception {
        if (content instanceof Multipart multipart) {
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < multipart.getCount(); index++) {
                builder.append(extractContent(multipart.getBodyPart(index).getContent()));
            }
            return builder.toString();
        }
        return content.toString();
    }
}
