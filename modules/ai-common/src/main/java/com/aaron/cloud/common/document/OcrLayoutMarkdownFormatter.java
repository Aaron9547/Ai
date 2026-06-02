package com.aaron.cloud.common.document;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** 将带坐标的 OCR 文本块格式化为 Markdown（表格或分段正文）。 */
public final class OcrLayoutMarkdownFormatter {

    private OcrLayoutMarkdownFormatter() {}

    public static String format(List<OcrTextCell> cells) {
        if (cells == null || cells.isEmpty()) {
            return "";
        }
        List<OcrTextCell> valid =
                cells.stream()
                        .filter(c -> c.text() != null && !c.text().isBlank())
                        .sorted(
                                Comparator.comparingInt(OcrTextCell::top)
                                        .thenComparingInt(OcrTextCell::centerX))
                        .toList();
        if (valid.isEmpty()) {
            return "";
        }
        List<List<OcrTextCell>> rows = clusterRows(valid);
        if (looksLikeTable(rows)) {
            return toMarkdownTable(rows);
        }
        return toPlainMarkdown(rows);
    }

    private static List<List<OcrTextCell>> clusterRows(List<OcrTextCell> cells) {
        List<List<OcrTextCell>> rows = new ArrayList<>();
        for (OcrTextCell cell : cells) {
            List<OcrTextCell> target = null;
            for (List<OcrTextCell> row : rows) {
                int avgCenterY =
                        (int)
                                row.stream()
                                        .mapToInt(OcrTextCell::centerY)
                                        .average()
                                        .orElse(cell.centerY());
                int avgHeight =
                        (int)
                                row.stream()
                                        .mapToInt(OcrTextCell::height)
                                        .average()
                                        .orElse(cell.height());
                int tolerance = Math.max(10, avgHeight / 2);
                if (Math.abs(cell.centerY() - avgCenterY) <= tolerance) {
                    target = row;
                    break;
                }
            }
            if (target == null) {
                target = new ArrayList<>();
                rows.add(target);
            }
            target.add(cell);
        }
        for (List<OcrTextCell> row : rows) {
            row.sort(Comparator.comparingInt(OcrTextCell::centerX));
        }
        rows.sort(
                Comparator.comparingInt(
                        row -> row.stream().mapToInt(OcrTextCell::top).min().orElse(0)));
        return rows;
    }

    private static boolean looksLikeTable(List<List<OcrTextCell>> rows) {
        if (rows.size() < 2) {
            return false;
        }
        long multiCellRows = rows.stream().filter(r -> r.size() >= 2).count();
        return multiCellRows >= 2;
    }

    private static String toMarkdownTable(List<List<OcrTextCell>> rows) {
        int cols =
                rows.stream().mapToInt(List::size).max().orElse(0);
        if (cols < 2) {
            return toPlainMarkdown(rows);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            appendTableRow(sb, rows.get(i), cols);
            if (i == 0) {
                sb.append('\n');
                sb.append('|');
                for (int c = 0; c < cols; c++) {
                    sb.append(" --- |");
                }
                sb.append('\n');
            }
        }
        return sb.toString().strip();
    }

    private static void appendTableRow(StringBuilder sb, List<OcrTextCell> row, int cols) {
        sb.append('|');
        for (int c = 0; c < cols; c++) {
            String cell = c < row.size() ? sanitizeCell(row.get(c).text()) : "";
            sb.append(' ').append(cell).append(" |");
        }
        sb.append('\n');
    }

    private static String toPlainMarkdown(List<List<OcrTextCell>> rows) {
        return rows.stream()
                .map(
                        row ->
                                row.stream()
                                        .map(OcrTextCell::text)
                                        .map(OcrLayoutMarkdownFormatter::sanitizeCell)
                                        .collect(Collectors.joining(" ")))
                .filter(line -> !line.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private static String sanitizeCell(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace('|', '｜').replace('\n', ' ').strip();
    }
}
