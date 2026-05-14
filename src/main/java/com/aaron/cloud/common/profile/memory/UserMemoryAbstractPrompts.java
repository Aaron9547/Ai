package com.aaron.cloud.common.profile.memory;

/** 记忆抽象层异步 LLM 的提示词：融合「分层记忆 + 动态注入 + 冲突以新为准」等策略说明。 */
public final class UserMemoryAbstractPrompts {

    public static final String SYSTEM =
            """
            你是「记忆管理员」后台服务，负责把对话-derived 的片段整理成**一份 JSON 对象**（不要 Markdown、不要解释性正文）。
            目标：维护用户长期陪伴所需的**稳定语义**与**可检索要点**，并区分：
            - 工作记忆/近期情景：短周期内仍可能变化的事实；
            - 稳定事实：跨多轮仍成立、值得长期记住的偏好/身份/计划摘要；
            - 画像增量 profile_delta：对结构化用户画像的可合并增量（键冲突时以**新对话为准**覆盖旧值）；
            - 遗忘建议 forget_candidates：明显琐碎、无复用价值的一两句话（可为空数组）。

            硬性规则：
            1) **只输出 JSON**，顶层须为对象，且必须包含字段 schema_version（固定为 memory_abstract_v2）。
            2) 不要编造用户未表达的内容；不确定的写入 stable_facts 时降低确信度或省略。
            3) 若新信息与旧 abstract JSON 冲突，以**当前 chunk 转写**为准，并在 profile_delta 或 stable_facts 中体现修正。
            4) 语言与用户输入一致（中文为主则中文输出）。
            """;

    private UserMemoryAbstractPrompts() {}

    public static String userPayload(String subjectKey, String oldAbstractJson, String chunkTranscript, String trigger) {
        return "subject_key="
                + subjectKey
                + "\ntrigger="
                + (trigger == null ? "" : trigger)
                + "\n\n【旧 abstract JSON（可能为空）】\n"
                + (oldAbstractJson == null || oldAbstractJson.isBlank() ? "{}" : oldAbstractJson)
                + "\n\n【最近双线记忆片段（时间顺序从早到新；USER/ASSISTANT 标注）】\n"
                + (chunkTranscript == null || chunkTranscript.isBlank() ? "（无）" : chunkTranscript)
                + "\n\n请输出 JSON，字段要求如下：\n"
                + "{\n"
                + "  \"schema_version\": \"memory_abstract_v2\",\n"
                + "  \"working_summary\": \"字符串：近期情景一句话\",\n"
                + "  \"episodic_hooks\": [\"可选：仍活跃的短期话题钩子\"],\n"
                + "  \"stable_facts\": [\"可选：长期稳定事实，短句\"],\n"
                + "  \"profile_delta\": { \"任意键\": \"任意值\" },\n"
                + "  \"forget_candidates\": [\"可选：建议丢弃的琐碎原句\"],\n"
                + "  \"merge_notes\": \"可选：给前台/模型的合并说明\"\n"
                + "}\n";
    }
}
