package com.todo.global.file.extract;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTextExtractorTest {

    private static final String PDF = "application/pdf";
    private static final String DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String CSV = "text/csv";

    private final DocumentTextExtractor extractor = new DocumentTextExtractor(List.of(
            new PdfTextExtractor(), new DocxTextExtractor(), new XlsxTextExtractor(), new CsvTextExtractor()));

    @Test
    void PDF에서_본문을_추출한다() throws Exception {
        String text = extractor.extract(PDF, pdf("Team meeting notes", "Decision: ship on Sep 3"));

        assertThat(text).contains("Team meeting notes").contains("ship on Sep 3");
    }

    @Test
    void 워드_문서에서_본문을_추출한다() throws Exception {
        String text = extractor.extract(DOCX, docx("발표자료 초안", "로그인 API 구현 완료"));

        assertThat(text).contains("발표자료 초안").contains("로그인 API 구현 완료");
    }

    @Test
    void 엑셀은_시트별_마크다운_표로_옮긴다() throws Exception {
        // 셀을 그냥 이어붙이면 어느 값이 어느 열인지 사라져 요약이 엉뚱해진다.
        String text = extractor.extract(XLSX, xlsx());

        assertThat(text).contains("## 진행현황");
        assertThat(text).contains("| 담당 | 작업 | 상태 |");
        assertThat(text).contains("| --- | --- | --- |");
        assertThat(text).contains("| 심유진 | 프론트엔드 | 진행중 |");
    }

    @Test
    void CSV도_마크다운_표로_옮긴다() {
        byte[] csv = "이름,역할\n김윤진,백엔드\n라혜준,회의록\n".getBytes(StandardCharsets.UTF_8);

        String text = extractor.extract(CSV, csv);

        assertThat(text).contains("| 이름 | 역할 |");
        assertThat(text).contains("| --- | --- |");
        assertThat(text).contains("| 김윤진 | 백엔드 |");
    }

    @Test
    void 엑셀에서_저장한_MS949_CSV도_한글이_깨지지_않는다() {
        // 국내 환경에서 엑셀이 저장하는 CSV는 UTF-8이 아닌 경우가 흔하다.
        byte[] csv = "이름,역할\n김윤진,백엔드\n".getBytes(Charset.forName("MS949"));

        String text = extractor.extract(CSV, csv);

        assertThat(text).contains("김윤진").contains("백엔드");
    }

    @Test
    void CSV의_BOM은_첫_셀_이름을_오염시키지_않는다() {
        byte[] csv = ("﻿" + "이름,역할\n김윤진,백엔드\n").getBytes(StandardCharsets.UTF_8);

        String text = extractor.extract(CSV, csv);

        assertThat(text).contains("| 이름 | 역할 |");
    }

    @Test
    void 셀_안의_파이프는_표_구조를_깨뜨리지_않는다() {
        byte[] csv = "\"a|b\",c\n1,2\n".getBytes(StandardCharsets.UTF_8);

        String text = extractor.extract(CSV, csv);

        // 이스케이프되지 않은 파이프가 셀 경계이므로, 셀 값 안의 파이프는 앞에 백슬래시가 붙어야
        // 한 행이 두 셀로 남는다. 이스케이프가 없으면 세 셀짜리 행으로 읽힌다.
        assertThat(text.lines().findFirst().orElseThrow()).isEqualTo("| a\\|b | c |");
    }

    @Test
    void 지원하지_않는_형식은_영구_실패다() {
        // HWP는 업로드는 되지만 신뢰할 만한 자바 파서가 없어 요약 대상이 아니다.
        assertThat(extractor.supports("application/x-hwp")).isFalse();
        assertThatThrownBy(() -> extractor.extract("application/x-hwp", new byte[]{1, 2, 3}))
                .isInstanceOf(DocumentExtractionException.class)
                .hasMessageContaining("지원하지 않는 형식");
    }

    @Test
    void 깨진_파일은_재시도_대상이_아니라_영구_실패다() {
        assertThatThrownBy(() -> extractor.extract(PDF, new byte[]{1, 2, 3, 4}))
                .isInstanceOf(DocumentExtractionException.class);
        assertThatThrownBy(() -> extractor.extract(DOCX, new byte[]{1, 2, 3, 4}))
                .isInstanceOf(DocumentExtractionException.class);
    }

    @Test
    void 읽을_텍스트가_없는_문서는_영구_실패다() throws Exception {
        // 스캔 이미지만 든 PDF가 대표적이다. 재시도해도 텍스트가 생기지 않는다.
        assertThatThrownBy(() -> extractor.extract(PDF, emptyPdf()))
                .isInstanceOf(DocumentExtractionException.class)
                .hasMessageContaining("읽을 수 있는 텍스트");
    }

    @Test
    void 지원_형식을_미리_물어볼_수_있다() {
        // 호출부가 이걸로 걸러야 처리 못 할 건이 큐에 영영 대기 상태로 남지 않는다.
        assertThat(extractor.supports(PDF)).isTrue();
        assertThat(extractor.supports(DOCX)).isTrue();
        assertThat(extractor.supports(XLSX)).isTrue();
        assertThat(extractor.supports(CSV)).isTrue();
        assertThat(extractor.supports("image/jpeg")).isFalse();
        assertThat(extractor.supports(null)).isFalse();
    }

    private byte[] pdf(String... lines) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                for (String line : lines) {
                    content.showText(line);
                    content.newLineAtOffset(0, -20);
                }
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] emptyPdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] docx(String... paragraphs) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String paragraph : paragraphs) {
                document.createParagraph().createRun().setText(paragraph);
            }
            document.write(out);
            return out.toByteArray();
        }
    }

    private byte[] xlsx() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("진행현황");
            String[][] rows = {{"담당", "작업", "상태"}, {"심유진", "프론트엔드", "진행중"}, {"김윤진", "백엔드", "완료"}};
            for (int r = 0; r < rows.length; r++) {
                var row = sheet.createRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
