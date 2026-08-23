package com.todo.global.file.extract;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PdfTextExtractor implements ProofTextExtractor {

    private static final String CONTENT_TYPE = "application/pdf";

    /**
     * 요약에 필요한 만큼만 읽는다. 페이지 수 제한이 없으면 수백 페이지 문서 하나가
     * 폴러 스레드를 오래 붙잡는다.
     */
    private static final int MAX_PAGES = 50;

    @Override
    public boolean supports(String contentType) {
        return CONTENT_TYPE.equals(contentType);
    }

    @Override
    public String extract(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(document.getNumberOfPages(), MAX_PAGES));
            return stripper.getText(document);
        } catch (InvalidPasswordException e) {
            // 암호를 모르는 이상 몇 번을 시도해도 열리지 않는다.
            throw new DocumentExtractionException("암호가 걸린 PDF는 요약할 수 없습니다.", e);
        } catch (IOException | RuntimeException e) {
            throw new DocumentExtractionException("PDF에서 텍스트를 추출하지 못했습니다.", e);
        }
    }
}
