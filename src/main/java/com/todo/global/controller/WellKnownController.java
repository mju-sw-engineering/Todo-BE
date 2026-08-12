package com.todo.global.controller;

import com.todo.global.config.AppleProperties;
import com.todo.global.dto.response.AppleAppSiteAssociationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Apple Universal Links 검증용 정적 파일을 서빙한다. 클라이언트(iOS) API가 아니라
 * Apple 크롤러/로그인 전 사용자가 접근하는 인프라 경로라 Swagger 문서화 대상에서 제외한다.
 */
@RestController
@RequiredArgsConstructor
public class WellKnownController {

    private final AppleProperties appleProperties;

    /**
     * TeamService가 초대 링크를 만드는 경로(app.team-invite-link-path)와 반드시 같아야 한다.
     * 하드코딩하면 배포 시 이 값만 바꿨을 때 Universal Links가 조용히 깨진다.
     */
    @Value("${app.team-invite-link-path:/invite}")
    private String teamInviteLinkPath;

    @GetMapping("/.well-known/apple-app-site-association")
    public AppleAppSiteAssociationResponse getAppleAppSiteAssociation() {
        String appId = appleProperties.teamId() + "." + appleProperties.iosClientId();
        String path = teamInviteLinkPath.startsWith("/") ? teamInviteLinkPath : "/" + teamInviteLinkPath;
        return AppleAppSiteAssociationResponse.of(appId, List.of(path + "*"));
    }
}
