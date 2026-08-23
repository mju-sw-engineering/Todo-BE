package com.todo.global.file.extract;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * contentType에 맞는 추출기로 라우팅한다.
 *
 * <p>지원하지 않는 형식이 있다는 점이 중요하다. HWP·HWPX는 업로드는 되지만 신뢰할 만한
 * 자바 파서가 없어 요약 대상이 아니다. 호출부가 {@link #supports(String)}로 먼저 물어보고
 * 대상이 아닌 제출은 큐에 태우지 않아야, 처리되지 않을 건이 계속 대기 상태로 남지 않는다.
 */
@Component
@RequiredArgsConstructor
public class DocumentTextExtractor {

    /**
     * 메모리 폭주를 막는 상한이지 비용 절감 장치가 아니다. 모델 입력이 길다고 해서 자를 이유는
     * 없다 — 앞부분만 남기면 정작 결론이 뒤에 있는 문서를 잘못 요약한다. 정상적인 팀플 문서는
     * 이 선에 걸리지 않는다.
     */
    private static final int MAX_CHARS = 300_000;

    private final List<ProofTextExtractor> extractors;

    public boolean supports(String contentType) {
        return findExtractor(contentType) != null;
    }

    /**
     * @throws DocumentExtractionException 지원하지 않는 형식이거나 추출에 실패했을 때.
     *                                     모두 재시도해도 같은 결과가 나오는 실패다.
     */
    public String extract(String contentType, byte[] bytes) {
        ProofTextExtractor extractor = findExtractor(contentType);
        if (extractor == null) {
            throw new DocumentExtractionException("요약을 지원하지 않는 형식입니다. contentType=" + contentType);
        }

        String text = extractor.extract(bytes);
        if (text == null || text.isBlank()) {
            // 스캔 이미지만 든 PDF가 대표적이다. 재시도해도 텍스트가 생기지 않는다.
            throw new DocumentExtractionException("문서에서 읽을 수 있는 텍스트를 찾지 못했습니다.");
        }

        String normalized = text.replaceAll("[ \\t]+", " ").replaceAll("\\R{3,}", "\n\n").strip();
        return normalized.length() > MAX_CHARS ? normalized.substring(0, MAX_CHARS) : normalized;
    }

    private ProofTextExtractor findExtractor(String contentType) {
        if (contentType == null) {
            return null;
        }
        return extractors.stream()
                .filter(extractor -> extractor.supports(contentType))
                .findFirst()
                .orElse(null);
    }
}
