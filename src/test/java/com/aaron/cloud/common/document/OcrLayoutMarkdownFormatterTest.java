package com.aaron.cloud.common.document;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class OcrLayoutMarkdownFormatterTest {

    @Test
    void format_tableLikeCells_emitsMarkdownTable() {
        List<OcrTextCell> cells =
                List.of(
                        cell("序号", 10, 10, 50, 30),
                        cell("配件", 60, 10, 120, 30),
                        cell("1", 10, 40, 50, 60),
                        cell("CPU", 60, 40, 120, 60));
        String md = OcrLayoutMarkdownFormatter.format(cells);
        assertTrue(md.contains("| 序号 | 配件 |"));
        assertTrue(md.contains("| --- | --- |"));
        assertTrue(md.contains("| 1 | CPU |"));
    }

    @Test
    void format_singleColumn_emitsPlainLines() {
        String md =
                OcrLayoutMarkdownFormatter.format(
                        List.of(cell("标题行", 0, 0, 100, 20), cell("正文段落", 0, 30, 100, 50)));
        assertTrue(md.contains("标题行"));
        assertTrue(md.contains("正文段落"));
    }

    private static OcrTextCell cell(String text, int left, int top, int right, int bottom) {
        int centerX = (left + right) / 2;
        int centerY = (top + bottom) / 2;
        return new OcrTextCell(text, left, top, right, bottom, centerX, centerY);
    }
}
