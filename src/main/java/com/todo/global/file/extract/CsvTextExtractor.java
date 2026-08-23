package com.todo.global.file.extract;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV를 마크다운 표로 옮긴다. 표 구조를 유지해야 요약이 정확해지는 것은 엑셀과 같은 이유다.
 */
@Component
public class CsvTextExtractor implements ProofTextExtractor {

    private static final String CONTENT_TYPE = "text/csv";
    private static final int MAX_ROWS = 200;
    private static final int MAX_COLUMNS = 30;

    /** 엑셀에서 저장한 한국어 CSV는 UTF-8이 아니라 MS949인 경우가 많다. */
    private static final Charset MS949 = Charset.forName("MS949");
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Override
    public boolean supports(String contentType) {
        return CONTENT_TYPE.equals(contentType);
    }

    @Override
    public String extract(byte[] bytes) {
        byte[] content = stripBom(bytes);
        try {
            return parse(content, StandardCharsets.UTF_8);
        } catch (DocumentExtractionException e) {
            throw e;
        } catch (RuntimeException | IOException e) {
            throw new DocumentExtractionException("CSV에서 텍스트를 추출하지 못했습니다.", e);
        }
    }

    private String parse(byte[] content, Charset charset) throws IOException {
        String text = new String(content, charset);
        // UTF-8로 읽었을 때 대체 문자가 나오면 한국어 CSV의 전형적인 인코딩 불일치다.
        if (charset == StandardCharsets.UTF_8 && text.indexOf('�') >= 0) {
            return parse(content, MS949);
        }

        StringBuilder output = new StringBuilder();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(content), charset);
             CSVParser parser = CSVFormat.DEFAULT.builder().setIgnoreSurroundingSpaces(true).build().parse(reader)) {
            boolean headerWritten = false;
            int rowCount = 0;
            for (CSVRecord record : parser) {
                if (rowCount++ >= MAX_ROWS) {
                    break;
                }
                List<String> cells = readCells(record);
                if (cells.isEmpty() || cells.stream().allMatch(String::isBlank)) {
                    continue;
                }
                output.append("| ").append(String.join(" | ", cells)).append(" |\n");
                if (!headerWritten) {
                    output.append("|").append(" --- |".repeat(cells.size())).append('\n');
                    headerWritten = true;
                }
            }
        }
        return output.toString();
    }

    private List<String> readCells(CSVRecord record) {
        List<String> cells = new ArrayList<>();
        int size = Math.min(record.size(), MAX_COLUMNS);
        for (int i = 0; i < size; i++) {
            cells.add(record.get(i)
                    .replace("|", "\\|")
                    .replaceAll("\\s*\\R\\s*", " ")
                    .strip());
        }
        return cells;
    }

    private byte[] stripBom(byte[] bytes) {
        if (bytes.length >= UTF8_BOM.length
                && bytes[0] == UTF8_BOM[0] && bytes[1] == UTF8_BOM[1] && bytes[2] == UTF8_BOM[2]) {
            byte[] stripped = new byte[bytes.length - UTF8_BOM.length];
            System.arraycopy(bytes, UTF8_BOM.length, stripped, 0, stripped.length);
            return stripped;
        }
        return bytes;
    }
}
