package com.todo.domain.team.service;

import com.todo.domain.team.entity.Team;
import com.todo.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamInviteMailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    public void sendInvitations(Team team, String inviteLink, List<String> emails) {
        try {
            for (String email : emails) {
                mailSender.send(buildMessage(team, inviteLink, email));
            }
        } catch (MailException e) {
            throw new BusinessException("초대 메일 발송에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private SimpleMailMessage buildMessage(Team team, String inviteLink, String email) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (fromEmail != null && !fromEmail.isBlank()) {
            message.setFrom(fromEmail);
        }
        message.setTo(email);
        message.setSubject("[Todo] " + team.getTeamName() + " 팀 초대");
        message.setText(buildBody(team, inviteLink));
        return message;
    }

    private String buildBody(Team team, String inviteLink) {
        return """
                %s 팀에 초대되었습니다.

                아래 링크로 이동해 팀에 참여할 수 있습니다.
                %s

                직접 입력용 초대 코드: %s

                로그인이 필요하면 로그인 또는 회원가입 후 같은 초대 코드로 참여해주세요.
                """.formatted(team.getTeamName(), inviteLink, team.getInviteCode());
    }
}
