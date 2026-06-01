package com.aaron.cloud.chat.support;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;

/** 使用 Apache Tika 抽取常见办公/图片容器中的文本（图片无 OCR 时可能为空）。 */
@Component
public class DocumentTextExtractor {

    private static final int MAX_CHARS = 5_000_000;

    public String extract(byte[] bytes, String fileName) throws Exception {
        return extract(bytes, fileName, null);
    }

    public String extract(byte[] bytes, String fileName, String contentType) throws Exception {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        AutoDetectParser parser = new AutoDetectParser();
        ContentHandler handler = new BodyContentHandler(MAX_CHARS);
        Metadata metadata = new Metadata();
        if (fileName != null && !fileName.isBlank()) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        }
        if (contentType != null && !contentType.isBlank()) {
            metadata.set(Metadata.CONTENT_TYPE, contentType);
        }
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            parser.parse(in, handler, metadata, new ParseContext());
        }
        String text = handler.toString();
        if (text == null) {
            return "";
        }
        return text.trim();
    }
}
