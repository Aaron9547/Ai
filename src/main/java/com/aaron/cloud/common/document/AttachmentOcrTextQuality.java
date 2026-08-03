package com.aaron.cloud.common.document;

import java.util.regex.Pattern;

/** 附件 OCR 正文质量判定（本机 RapidOCR/Tesseract 与视觉模型回退共用）。 */
public final class AttachmentOcrTextQuality {

    private static final Pattern MEANINGFUL_CJK = Pattern.compile("[\\u4e00-\\u9fff]{2,}");

    private static final Pattern MEANINGFUL_ALNUM =
            Pattern.compile("(?i)(?:[A-Z]{1,4}\\d{2,6}|\\d{1,2}#\\s*楼|YJ\\s*\\d+)");

    private static final Pattern MARKDOWN_TABLE_SEPARATOR = Pattern.compile("^[|\\s\\-—─:：]+$");

    private static final Pattern GENERIC_FILE_STEM =
            Pattern.compile("(?i)^(img|image|pic|photo|scan|screenshot|upload|file|document|未命名|新建)[-_\\s\\d]*$");

    private AttachmentOcrTextQuality() {}

    /** 本机 OCR 结果是否应触发视觉模型回退（空、占位、或仅为尺寸表格噪声）。 */
    public static boolean needsVisionFallback(String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            return true;
        }
        if (ExtractedDocumentTexts.isPlaceholder(extractedText)) {
            return true;
        }
        return !isUsableEntityHint(extractedText);
    }

    /** 压缩摘要或 OCR 全文是否含可用于检索/理解的实体（中文主题、型号、有意义文件名等）。 */
    public static boolean isUsableEntityHint(String hint) {
        if (hint == null || hint.isBlank()) {
            return false;
        }
        String t = hint.trim();
        if (MEANINGFUL_CJK.matcher(t).find()) {
            return true;
        }
        if (MEANINGFUL_ALNUM.matcher(t).find()) {
            return true;
        }
        if (isTableNoise(t)) {
            return false;
        }
        return t.length() >= 6 && !GENERIC_FILE_STEM.matcher(t.replace(' ', '_')).matches();
    }

    public static boolean isTableNoise(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String t = text.trim();
        int pipes = countChar(t, '|') + countChar(t, '｜');
        int digits = 0;
        for (int i = 0; i < t.length(); i++) {
            if (Character.isDigit(t.charAt(i))) {
                digits++;
            }
        }
        boolean noCjk = !MEANINGFUL_CJK.matcher(t).find();
        if (noCjk && pipes >= 2) {
            return true;
        }
        if (noCjk && !MEANINGFUL_ALNUM.matcher(t).find() && digits >= 4 && digits * 2 >= t.length()) {
            return true;
        }
        return false;
    }

    public static boolean isNoiseLine(String line) {
        if (line == null || line.isBlank()) {
            return true;
        }
        String t = line.trim();
        if (MARKDOWN_TABLE_SEPARATOR.matcher(t).matches()) {
            return true;
        }
        if (isTableNoise(t)) {
            return true;
        }
        int pipes = countChar(t, '|') + countChar(t, '｜');
        if (pipes >= 2 && !MEANINGFUL_CJK.matcher(t).find()) {
            return true;
        }
        if (MEANINGFUL_CJK.matcher(t).find()) {
            return false;
        }
        if (MEANINGFUL_ALNUM.matcher(t).find()) {
            return false;
        }
        return t.length() <= 24 && t.replaceAll("[\\d\\s|｜\\-—:：.,，]", "").length() <= 2;
    }

    private static int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }
}
