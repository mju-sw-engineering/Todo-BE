package com.todo.domain.auth.dto.response;

import com.todo.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "아이디 찾기 응답")
public record FindIdResponse(
        @Schema(description = "가입 시 사용한 로그인 아이디", example = "user123")
        String loginId
) {
    public static FindIdResponse from(User user) {
        return new FindIdResponse(user.getLoginId());
    }
}
