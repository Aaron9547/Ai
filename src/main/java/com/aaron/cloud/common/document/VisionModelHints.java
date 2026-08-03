package com.aaron.cloud.common.document;

import com.aaron.cloud.common.api.enums.llm.LlmModelKind;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import java.util.regex.Pattern;

/** 判断租户 {@code llm_model} 是否可用于图片 OCR（OpenAI 兼容 {@code image_url}）。 */
public final class VisionModelHints {

    private static final Pattern VISION_HINT =
            Pattern.compile(
                    "(?i)(vl|vision|gpt-4o|gpt-4\\.1|omni|qwen-vl|qwen3-vl|doubao.*vision|seed.*vision|"
                            + "glm-4v|glm4v|moonshot.*vision|ui-tars|hunyuan.*vision|ernie.*vl|internvl|"
                            + "minicpm-v|pixtral|llava|step-1v|mimo-vl|kimi.*vision|deepseek.*vl)");

    private VisionModelHints() {}

    public static boolean looksVisionCapable(SysLlmModel m) {
        if (m == null) {
            return false;
        }
        if (m.getModelKind() == LlmModelKind.VISION) {
            return true;
        }
        return hintIn(m.getOpenaiModelId()) || hintIn(m.getAlias()) || hintIn(m.getDisplayName());
    }

    static boolean hintIn(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        return VISION_HINT.matcher(raw.trim()).find();
    }
}
