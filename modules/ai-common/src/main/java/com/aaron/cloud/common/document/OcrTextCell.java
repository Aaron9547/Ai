package com.aaron.cloud.common.document;

/** OCR 识别出的单个文本块及其包围盒（像素坐标）。 */
public record OcrTextCell(
        String text, int left, int top, int right, int bottom, int centerX, int centerY) {

    public int height() {
        return Math.max(1, bottom - top);
    }

    public int width() {
        return Math.max(1, right - left);
    }
}
