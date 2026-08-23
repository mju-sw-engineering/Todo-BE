package com.todo.domain.todo.service;

import com.todo.domain.todo.entity.ProofKind;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/**
 * 판정 프롬프트를 classpath에서 읽어 보관한다.
 *
 * <p>자바 상수가 아니라 리소스 파일로 둔 이유는 프롬프트를 계속 손볼 것이기 때문이다.
 * 문구 하나 고치려고 자바 파일을 열고 재컴파일하는 것보다 텍스트 파일을 고치는 편이 낫다.
 * 알림 문구를 {@code notification-messages.yml}로 뺀 것과 같은 이유다.
 *
 * <p>기동 시점에 한 번 읽는다. 파일이 없으면 그 자리에서 기동을 실패시킨다 —
 * 프롬프트 없이 뜬 서버는 모든 판정을 조용히 실패시키므로 늦게 아는 것이 더 나쁘다.
 */
@Slf4j
@Component
public class ProofPromptProvider {

    private static final Map<ProofKind, String> PROMPT_PATHS = Map.of(
            ProofKind.IMAGE, "prompts/proof-image-verdict.txt",
            ProofKind.DOCUMENT, "prompts/proof-document-summary.txt"
    );

    private final Map<ProofKind, String> prompts = new EnumMap<>(ProofKind.class);

    @PostConstruct
    void loadPrompts() {
        PROMPT_PATHS.forEach((kind, path) -> prompts.put(kind, read(path)));
    }

    public String systemInstruction(ProofKind kind) {
        String prompt = prompts.get(kind);
        if (prompt == null) {
            throw new IllegalStateException("판정 프롬프트가 없습니다. kind=" + kind);
        }
        return prompt;
    }

    private String read(String path) {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            String content = StreamUtils.copyToString(input, StandardCharsets.UTF_8).strip();
            if (content.isEmpty()) {
                throw new IllegalStateException("판정 프롬프트가 비어 있습니다. path=" + path);
            }
            return content;
        } catch (IOException e) {
            throw new IllegalStateException("판정 프롬프트를 읽지 못했습니다. path=" + path, e);
        }
    }
}
