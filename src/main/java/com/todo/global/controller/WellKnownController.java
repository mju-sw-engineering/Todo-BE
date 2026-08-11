package com.todo.global.controller;

import com.todo.global.config.AppleProperties;
import com.todo.global.dto.response.AppleAppSiteAssociationResponse;
import lombok.RequiredArgsConstructor;
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

    private static final List<String> INVITE_LINK_PATHS = List.of("/invite*");

    private final AppleProperties appleProperties;

    @GetMapping("/.well-known/apple-app-site-association")
    public AppleAppSiteAssociationResponse getAppleAppSiteAssociation() {
        String appId = appleProperties.teamId() + "." + appleProperties.iosClientId();
        return AppleAppSiteAssociationResponse.of(appId, INVITE_LINK_PATHS);
    }
}
