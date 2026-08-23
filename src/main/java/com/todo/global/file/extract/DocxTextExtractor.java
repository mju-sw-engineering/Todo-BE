package com.todo.global.file.extract;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Component
public class DocxTextExtractor implements ProofTextExtractor {

    private static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Override
    public boolean supports(String contentType) {
        return CONTENT_TYPE.equals(contentType);
    }

    @Override
    public String extract(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (IOException | RuntimeException e) {
            // 확장자만 docx이고 실제로는 다른 ZIP인 경우가 여기 걸린다.
            // 제출 검증이 ZIP 시그니처까지만 보므로 내부 구조 불일치는 이 시점에 드러난다.
            throw new DocumentExtractionException("워드 문서에서 텍스트를 추출하지 못했습니다.", e);
        }
    }
}
