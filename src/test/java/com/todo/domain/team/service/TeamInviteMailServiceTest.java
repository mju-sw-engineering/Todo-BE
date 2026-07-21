package com.todo.domain.team.service;

import com.todo.domain.team.entity.Team;
import com.todo.global.mail.entity.MailType;
import com.todo.global.mail.service.MailOutboxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class TeamInviteMailServiceTest {

    @InjectMocks
    private TeamInviteMailService teamInviteMailService;

    @Mock
    private MailOutboxService mailOutboxService;

    @Test
    void 초대_메일을_outbox에_적재한다() {
        Team team = Team.create("스터디 팀", null, "ABCD1234");
        String inviteLink = "https://todo.example.com/teams/join?code=ABCD1234";

        teamInviteMailService.sendInvitations(team, inviteLink, List.of("member@example.com"));

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        then(mailOutboxService).should().enqueue(
                eq(MailType.TEAM_INVITE),
                eq("member@example.com"),
                eq("팀 초대가 도착했어요"),
                textCaptor.capture(),
                htmlCaptor.capture(),
                isNull(LocalDateTime.class),
                eq(5)
        );
        assertThat(textCaptor.getValue()).contains("스터디 팀").contains(inviteLink).contains("ABCD1234");
        assertThat(htmlCaptor.getValue()).contains("스터디 팀").contains(inviteLink).contains("팀 참여하기");
    }

    @Test
    void 수신자마다_한_건씩_적재한다() {
        Team team = Team.create("스터디 팀", null, "ABCD1234");

        teamInviteMailService.sendInvitations(
                team,
                "https://todo.example.com/teams/join?code=ABCD1234",
                List.of("a@example.com", "b@example.com", "c@example.com")
        );

        then(mailOutboxService).should(times(3)).enqueue(
                eq(MailType.TEAM_INVITE),
                org.mockito.ArgumentMatchers.anyString(),
                eq("팀 초대가 도착했어요"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                isNull(LocalDateTime.class),
                eq(5)
        );
    }
}
