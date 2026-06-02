package com.aaron.cloud.common.document;

import com.benjaminwan.ocrlibrary.OcrResult;
import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;
import io.github.mymonstercat.ocr.config.ParamConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 本机 RapidOCR（ONNX / PP-OCR），纯 JVM 内推理，不依赖视觉大模型或独立 OCR 服务。
 *
 * <p>模型与原生库由 {@code rapidocr-onnx-platform} 按操作系统自动加载；首次识别会初始化引擎（约数秒）。
 */
@Slf4j
@Component
public class LocalRapidOcrOnnxImageOcr {

    private final boolean enabled;
    private final String modelCode;

    private volatile InferenceEngine engine;
    private volatile boolean initFailed;

    public LocalRapidOcrOnnxImageOcr(
            @Value("${com.aaron.cloud.document.local-ocr.rapid.enabled:true}") boolean enabled,
            @Value("${com.aaron.cloud.document.local-ocr.rapid.model:ONNX_PPOCR_V3}") String modelCode) {
        this.enabled = enabled;
        this.modelCode = modelCode == null || modelCode.isBlank() ? "ONNX_PPOCR_V3" : modelCode.trim();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Optional<String> tryExtract(byte[] bytes) {
        if (!enabled || bytes == null || bytes.length == 0 || initFailed) {
            return Optional.empty();
        }
        synchronized (this) {
            try {
                InferenceEngine eng = engine();
                if (eng == null) {
                    return Optional.empty();
                }
                ParamConfig config = ParamConfig.getDefaultConfig();
                config.setDoAngle(true);
                config.setMostAngle(true);
                OcrResult result = runOnTempFile(eng, bytes, config);
                if (result == null) {
                    return Optional.empty();
                }
                List<OcrTextCell> cells = RapidOcrResultCells.from(result);
                String markdown = OcrLayoutMarkdownFormatter.format(cells);
                if (markdown.isBlank()) {
                    String plain = result.getStrRes();
                    if (plain == null || plain.isBlank()) {
                        return Optional.empty();
                    }
                    markdown = OcrLayoutMarkdownFormatter.format(List.of(new OcrTextCell(plain.trim(), 0, 0, 0, 0, 0, 0)));
                }
                log.debug("[本地RapidOCR] 识别成功，字数 {}", markdown.length());
                return Optional.of(markdown);
            } catch (Exception ex) {
                log.warn("[本地RapidOCR] 识别失败：{}", ex.getMessage());
                return Optional.empty();
            }
        }
    }

    /** rapidocr 0.0.7 仅支持路径入参，经临时文件识别后删除。 */
    private static OcrResult runOnTempFile(InferenceEngine eng, byte[] bytes, ParamConfig config)
            throws Exception {
        Path tmp = Files.createTempFile("rapidocr-", ".png");
        try {
            Files.write(tmp, bytes);
            return eng.runOcr(tmp.toString(), config);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private InferenceEngine engine() {
        if (initFailed) {
            return null;
        }
        if (engine != null) {
            return engine;
        }
        try {
            Model model = resolveModel(modelCode);
            engine = InferenceEngine.getInstance(model);
            log.info("[本地RapidOCR] 已启用：model={}", model);
            return engine;
        } catch (Exception ex) {
            initFailed = true;
            log.warn("[本地RapidOCR] 引擎初始化失败：{}", ex.getMessage());
            return null;
        }
    }

    private static Model resolveModel(String code) {
        String normalized = code.toUpperCase(Locale.ROOT);
        for (Model model : Model.values()) {
            if (model.name().equalsIgnoreCase(normalized)) {
                return model;
            }
        }
        return Model.ONNX_PPOCR_V3;
    }
}
