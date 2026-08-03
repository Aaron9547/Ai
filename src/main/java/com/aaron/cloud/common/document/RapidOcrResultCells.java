package com.aaron.cloud.common.document;

import com.benjaminwan.ocrlibrary.OcrResult;
import com.benjaminwan.ocrlibrary.Point;
import com.benjaminwan.ocrlibrary.TextBlock;
import java.util.ArrayList;
import java.util.List;

/** 自 RapidOCR {@link OcrResult} 提取带坐标的文本块。 */
public final class RapidOcrResultCells {

    private RapidOcrResultCells() {}

    public static List<OcrTextCell> from(OcrResult result) {
        if (result == null || result.getTextBlocks() == null) {
            return List.of();
        }
        List<OcrTextCell> out = new ArrayList<>();
        for (TextBlock block : result.getTextBlocks()) {
            if (block == null) {
                continue;
            }
            String text = block.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            OcrTextCell cell = fromBlock(text.strip(), block);
            if (cell != null) {
                out.add(cell);
            }
        }
        return out;
    }

    private static OcrTextCell fromBlock(String text, TextBlock block) {
        if (block.getBoxPoint() == null || block.getBoxPoint().isEmpty()) {
            return new OcrTextCell(text, 0, 0, 0, 0, 0, 0);
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Point p : block.getBoxPoint()) {
            if (p == null) {
                continue;
            }
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
        }
        if (minX == Integer.MAX_VALUE) {
            return new OcrTextCell(text, 0, 0, 0, 0, 0, 0);
        }
        int centerX = (minX + maxX) / 2;
        int centerY = (minY + maxY) / 2;
        return new OcrTextCell(text, minX, minY, maxX, maxY, centerX, centerY);
    }
}
