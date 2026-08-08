package com.todo.global.config;

import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.jwt.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 발급된 토큰으로 보호 API가 실제로 동작하는지 검증하는 엔드투엔드 인증 테스트.
 *
 * <p>토큰 subject는 userId지만 도메인 서비스는 {@code authentication.getName()}을
 * loginId로 받아 조회한다. Apple 로그인 도입 때 principal username까지 userId로 바뀌며
 * 인증이 필요한 모든 API가 401로 깨졌는데, 필터·서비스 단위 테스트는 principal을 직접
 * 주입하기 때문에 잡지 못했다. 여기서는 실제 필터 체인을 통과시켜 그 간극을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthenticatedRequestSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 발급된_토큰으로_보호_API를_호출하면_사용자를_찾는다() throws Exception {
        User user = userRepository.save(
                User.create("principal-e2e-tester", "encoded-password", "이투이테스터", null));
        String token = jwtUtil.generateToken(user.getId());

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.loginId").value("principal-e2e-tester"))
                .andExpect(jsonPath("$.data.userId").value(user.getId()));
    }
}
