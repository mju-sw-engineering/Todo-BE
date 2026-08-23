package com.todo.global.file.extract;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 스프레드시트를 마크다운 표로 옮긴다.
 *
 * <p>셀을 그냥 이어붙이면 어느 값이 어느 열인지 사라져 요약이 엉뚱해진다. 표 구조를 유지하는
 * 것이 요약 품질에 직접 영향을 주므로, 문서 중 유일하게 형식을 바꿔 전달한다.
 */
@Component
public class XlsxTextExtractor implements ProofTextExtractor {

    private static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 요약에 필요한 만큼만 읽는다. 수만 행짜리 시트를 통째로 옮길 이유가 없다. */
    private static final int MAX_ROWS_PER_SHEET = 200;
    private static final int MAX_COLUMNS = 30;
    private static final int MAX_SHEETS = 10;

    private final DataFormatter formatter = new DataFormatter();

    @Override
    public boolean supports(String contentType) {
        return CONTENT_TYPE.equals(contentType);
    }

    @Override
    public String extract(byte[] bytes) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            StringBuilder output = new StringBuilder();
            int sheetCount = Math.min(workbook.getNumberOfSheets(), MAX_SHEETS);
            for (int i = 0; i < sheetCount; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                output.append("## ").append(sheet.getSheetName()).append("\n\n");
                appendSheet(sheet, output);
                output.append('\n');
            }
            return output.toString();
        } catch (IOException | RuntimeException e) {
            throw new DocumentExtractionException("엑셀 문서에서 텍스트를 추출하지 못했습니다.", e);
        }
    }

    private void appendSheet(Sheet sheet, StringBuilder output) {
        int lastRow = Math.min(sheet.getLastRowNum(), MAX_ROWS_PER_SHEET - 1);
        boolean headerWritten = false;

        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            List<String> cells = readCells(row);
            if (cells.stream().allMatch(String::isBlank)) {
                continue;
            }

            output.append("| ").append(String.join(" | ", cells)).append(" |\n");
            if (!headerWritten) {
                // 마크다운 표는 첫 행 다음에 구분선이 있어야 표로 읽힌다.
                output.append("|").append(" --- |".repeat(cells.size())).append('\n');
                headerWritten = true;
            }
        }
    }

    private List<String> readCells(Row row) {
        List<String> cells = new ArrayList<>();
        if (row == null) {
            return cells;
        }
        int lastCell = Math.min(row.getLastCellNum(), MAX_COLUMNS);
        for (int i = 0; i < lastCell; i++) {
            Cell cell = row.getCell(i);
            // 셀 안의 파이프와 개행은 표 구조를 깨뜨린다.
            cells.add(cell == null ? "" : formatter.formatCellValue(cell)
                    .replace("|", "\\|")
                    .replaceAll("\\s*\\R\\s*", " ")
                    .strip());
        }
        return cells;
    }
}
