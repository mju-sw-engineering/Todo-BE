package com.todo.domain.team.service;

import com.todo.domain.team.entity.Team;
import com.todo.global.exception.BusinessException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

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
        } catch (MailException | MessagingException e) {
            throw new BusinessException("초대 메일 발송에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private MimeMessage buildMessage(Team team, String inviteLink, String email) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        if (fromEmail != null && !fromEmail.isBlank()) {
            helper.setFrom(fromEmail);
        }
        helper.setTo(email);
        helper.setSubject("팀 초대가 도착했어요");
        helper.setText(buildTextBody(team, inviteLink), buildHtmlBody(team, inviteLink));
        return message;
    }

    private String buildTextBody(Team team, String inviteLink) {
        return """
                팀 초대가 도착했어요

                %s 팀에 초대되었어요.

                아래 링크로 이동해 로그인한 뒤 팀에 참여해 주세요.
                %s

                버튼이 동작하지 않는 경우 아래 초대 코드를 입력해 주세요.
                초대 코드: %s

                또는 링크를 브라우저에 복사해 직접 접속할 수 있어요.
                """.formatted(team.getTeamName(), inviteLink, team.getInviteCode());
    }

    private String buildHtmlBody(Team team, String inviteLink) {
        String teamName = HtmlUtils.htmlEscape(team.getTeamName());
        String escapedInviteLink = HtmlUtils.htmlEscape(inviteLink);
        String inviteCode = HtmlUtils.htmlEscape(team.getInviteCode());

        return """
                <!doctype html>
                <html lang="ko">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>팀 초대</title>
                    <style>
                        a.invite-button:hover { background:#4c41b6 !important; }
                    </style>
                </head>
                <body style="margin:0;padding:0;background:#f6f4ff;font-family:Arial,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;color:#1f2937;">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f6f4ff;padding:36px 16px;">
                        <tr>
                            <td align="center">
                                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:560px;background:#ffffff;border:1px solid #ebe7ff;border-radius:20px;overflow:hidden;box-shadow:0 12px 32px rgba(91,79,207,0.12);">
                                    <tr>
                                        <td style="background:#5B4FCF;padding:30px 32px;color:#ffffff;">
                                            <h1 style="margin:14px 0 0;font-size:25px;line-height:1.35;font-weight:800;">팀 초대가 도착했어요</h1>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:34px 32px 32px;">
                                            <p style="margin:0;font-size:17px;line-height:1.7;color:#111827;"><strong>%s</strong> 팀에 초대되었어요.</p>
                                            <p style="margin:12px 0 28px;font-size:15px;line-height:1.7;color:#4b5563;">아래 버튼을 눌러 로그인한 뒤 팀에 참여해 주세요.</p>
                                            <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="margin:0 0 30px;">
                                                <tr>
                                                    <td align="center" style="background:#5B4FCF;border-radius:12px;">
                                                        <a class="invite-button" href="%s" style="display:block;padding:15px 24px;color:#ffffff;text-decoration:none;font-size:16px;font-weight:800;border-radius:12px;">팀 참여하기</a>
                                                    </td>
                                                </tr>
                                            </table>
                                            <p style="margin:0 0 12px;font-size:14px;line-height:1.7;color:#6b7280;">버튼이 동작하지 않는 경우 아래 초대 코드를 입력해 주세요.</p>
                                            <div style="margin:0 0 26px;padding:20px;background:#f1efff;border:1px solid #ddd8ff;border-radius:14px;text-align:center;">
                                                <div style="font-size:12px;color:#5B4FCF;font-weight:700;margin-bottom:10px;">초대 코드</div>
                                                <div style="font-size:28px;line-height:1.2;font-weight:900;letter-spacing:4px;color:#312e81;">%s</div>
                                            </div>
                                            <p style="margin:0;font-size:13px;line-height:1.7;color:#6b7280;">또는 아래 링크를 브라우저에 복사해 직접 접속할 수 있어요.</p>
                                            <p style="margin:8px 0 0;font-size:13px;line-height:1.7;word-break:break-all;"><a href="%s" style="color:#5B4FCF;text-decoration:underline;">%s</a></p>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """.formatted(teamName, escapedInviteLink, inviteCode, escapedInviteLink, escapedInviteLink);
    }
}
