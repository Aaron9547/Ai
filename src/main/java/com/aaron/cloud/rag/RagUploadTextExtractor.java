package com.aaron.cloud.rag;

import java.io.InputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.web.multipart.MultipartFile;

/** 管理端上传文件正文抽取（Tika），供入库与分析复用。 */
public final class RagUploadTextExtractor {

    static final int TIKA_MAX_CHARS = 2_000_000;

    private RagUploadTextExtractor() {}

    public static String extractFromMultipart(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            return "";
        }
        try (InputStream in = file.getInputStream()) {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(TIKA_MAX_CHARS);
            Metadata meta = new Metadata();
            parser.parse(in, handler, meta, new ParseContext());
            String extracted = handler.toString();
            return extracted != null ? extracted.trim() : "";
        }
    }
}
