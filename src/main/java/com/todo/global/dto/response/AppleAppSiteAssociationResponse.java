package com.todo.global.dto.response;

import java.util.List;

/**
 * iOS Universal Links가 초대 링크를 Safari 대신 앱으로 열도록 등록하는 파일의 응답 형태.
 * {@code GET /.well-known/apple-app-site-association}로 서빙된다.
 */
public record AppleAppSiteAssociationResponse(AppLinks applinks) {

    public record AppLinks(List<String> apps, List<AppLinkDetail> details) {}

    public record AppLinkDetail(String appID, List<String> paths) {}

    public static AppleAppSiteAssociationResponse of(String appId, List<String> paths) {
        return new AppleAppSiteAssociationResponse(
                new AppLinks(List.of(), List.of(new AppLinkDetail(appId, paths)))
        );
    }
}
