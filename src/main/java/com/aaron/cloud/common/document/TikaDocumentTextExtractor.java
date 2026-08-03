package com.aaron.cloud.common.document;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Locale;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.ContentHandler;

/**
 * 使用 Apache Tika 从办公文档/部分图片容器抽取正文（纯图片照片无 OCR 时通常为空）。
 *
 * <p>供对话附件、RAG 上传入库等模块共用；图片在 Tika 无文本后会尝试 {@link LocalChainedImageOcr}
 *（RapidOCR ONNX → Markdown，失败回退 Tesseract）。
 */
@Component
@RequiredArgsConstructor
public class TikaDocumentTextExtractor {

    private final LocalChainedImageOcr localChainedImageOcr;

    /** 对话附件等单文件上限内的抽取字符上限。 */
    public static final int MAX_CHARS_DEFAULT = 5_000_000;

    /** RAG 管理端上传解析（与历史 {@code RagUploadTextExtractor} 一致）。 */
    public static final int MAX_CHARS_RAG_UPLOAD = 2_000_000;

    public String extract(byte[] bytes, String fileName) throws Exception {
        return extract(bytes, fileName, null, MAX_CHARS_DEFAULT);
    }

    public String extract(byte[] bytes, String fileName, String contentType) throws Exception {
        return extract(bytes, fileName, contentType, MAX_CHARS_DEFAULT);
    }

    public String extract(byte[] bytes, String fileName, String contentType, int maxChars) throws Exception {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            String text = parseWithTika(in, fileName, contentType, maxChars);
            if (!text.isBlank()) {
                return text;
            }
            if (isImage(fileName, contentType) && localChainedImageOcr.isEnabled()) {
                return localChainedImageOcr.tryExtract(bytes).orElse("");
            }
            return "";
        }
    }

    public String extract(InputStream in, String fileName, String contentType, int maxChars) throws Exception {
        if (in == null) {
            return "";
        }
        if (isImage(fileName, contentType) && localChainedImageOcr.isEnabled()) {
            byte[] bytes = in.readAllBytes();
            return extract(bytes, fileName, contentType, maxChars);
        }
        return parseWithTika(in, fileName, contentType, maxChars);
    }

    private static String parseWithTika(
            InputStream in, String fileName, String contentType, int maxChars) throws Exception {
        AutoDetectParser parser = new AutoDetectParser();
        ContentHandler handler = new BodyContentHandler(maxChars);
        Metadata metadata = new Metadata();
        if (fileName != null && !fileName.isBlank()) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        }
        if (contentType != null && !contentType.isBlank()) {
            metadata.set(Metadata.CONTENT_TYPE, contentType);
        }
        parser.parse(in, handler, metadata, new ParseContext());
        String text = handler.toString();
        return text == null ? "" : text.trim();
    }

    private static boolean isImage(String fileName, String contentType) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return true;
        }
        return UploadedFileKind.IMAGE.equals(UploadedFileKind.resolve(fileName));
    }

    public String extractFromMultipart(MultipartFile file) throws Exception {
        return extractFromMultipart(file, MAX_CHARS_DEFAULT);
    }

    public String extractFromMultipart(MultipartFile file, int maxChars) throws Exception {
        if (file == null || file.isEmpty()) {
            return "";
        }
        String name = file.getOriginalFilename();
        String contentType = file.getContentType();
        try (InputStream in = file.getInputStream()) {
            return extract(in, name, contentType, maxChars);
        }
    }
}
