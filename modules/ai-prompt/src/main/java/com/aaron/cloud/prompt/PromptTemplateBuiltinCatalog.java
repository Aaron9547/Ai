package com.aaron.cloud.prompt;

import com.aaron.cloud.common.api.enums.prompt.PromptTemplateDomain;
import com.aaron.cloud.common.api.enums.prompt.PromptTemplateKind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 代码兜底提示词注册表；与 migrate 种子及 {@link PromptTemplateSeedGenerator} 输出保持一致。 */
public final class PromptTemplateBuiltinCatalog {

    public record Entry(
            String promptCode,
            PromptTemplateKind kind,
            PromptTemplateDomain domain,
            String locale,
            String content) {}

    private static final Map<String, Entry> BY_CODE_LOCALE = new LinkedHashMap<>();

    static {
        register(
                "follow_up_system",
                PromptTemplateKind.SYSTEM,
                PromptTemplateDomain.STARTER,
                "zh-CN",
                """
                根据用户问题与助手回复，生成 2～3 条用户可能继续追问的短句。
                只输出 JSON 数组，不要 markdown。每项中文 8～36 字，与上文强相关、不重复。
                """);
        register(
                "daily_recommend_structure",
                PromptTemplateKind.SYSTEM,
                PromptTemplateDomain.CHAT,
                "zh-CN",
                """
                你是资讯推荐编辑。根据联网检索摘要与用户画像，输出今日个性化资讯卡片列表。
                只输出 JSON 数组，不要 markdown，不要解释。每项字段：
                tag（领域标签，2～8字）、title（标题，12～48字）、summary（摘要，24～120字）、
                source（来源媒体名）、date（发布日期 yyyy-MM-dd，不得晚于今日 ${today}；须来自检索摘要中的发布时间，无法判断时写 ${today}）、
                url（可点击链接，须 http/https，且必须从【联网引用列表】中原样选取，禁止编造域名）。
                共 5～8 条，内容须为近期真实资讯，禁止编造未来日期或虚构事件；若无画像则输出通用热点资讯。
                示例：[{"tag":"科技","title":"…","summary":"…","source":"新华网","date":"${today}","url":"https://…"}]
                """);
        register(
                "daily_recommend_search_query",
                PromptTemplateKind.QUERY,
                PromptTemplateDomain.WEB,
                "*",
                "今日中国 科技 财经 教育 社会 校园 热点资讯 最新 ${year}");
        register(
                "daily_recommend_search_query_profile",
                PromptTemplateKind.QUERY,
                PromptTemplateDomain.WEB,
                "*",
                "今日最新资讯 热点新闻 与以下用户兴趣相关：${profile_excerpt} ${year}");
        register(
                "starter_hot_topic_structure",
                PromptTemplateKind.SYSTEM,
                PromptTemplateDomain.STARTER,
                "zh-CN",
                """
                你是推荐问句编辑。根据用户提供的联网检索摘要，输出适合 AI 对话开场白的短问题。
                只输出 JSON 数组，不要 markdown，不要解释。每项为中文问句，长度 8～36 字，共 8～12 条。
                问句应具体、可点击、避免重复。示例：["AIGC 最近有哪些新应用？","如何写一份周报模板？"]
                """);
        register(
                "starter_hot_search_query",
                PromptTemplateKind.QUERY,
                PromptTemplateDomain.WEB,
                "*",
                "今日中国网络与社会热点新闻 科技 财经 文化 2026 最新");
        register(
                "turn_digest_system",
                PromptTemplateKind.SYSTEM,
                PromptTemplateDomain.CHAT,
                "zh-CN",
                """
                你是对话归档助手。根据「用户问题」和「助手完整回复」，只输出严格 JSON（不要 markdown 代码块、不要多余说明），格式：
                {"contentSummary":"...","conversationTitle":"..."}

                约束：
                - contentSummary：中文，1～3 句，抓住要点，供后续轮次作上下文压缩占位，不超过 420 字。
                - conversationTitle：若输入中 needTitle 为 true，则填写不超过 24 字的简短会话标题（概括主题，不要引号与换行）；若 needTitle 为 false，必须填空字符串 ""。
                """);
        register(
                "memory_abstract_system",
                PromptTemplateKind.SYSTEM,
                PromptTemplateDomain.MEMORY,
                "zh-CN",
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
                """);
        register(
                "memory_abstract_user",
                PromptTemplateKind.USER,
                PromptTemplateDomain.MEMORY,
                "*",
                """
                subject_key=${subject_key}
                trigger=${trigger}

                【旧 abstract JSON（可能为空）】
                ${old_abstract_json}

                【最近双线记忆片段（时间顺序从早到新；USER/ASSISTANT 标注）】
                ${chunk_transcript}

                请输出 JSON，字段要求如下：
                {
                  "schema_version": "memory_abstract_v2",
                  "working_summary": "字符串：近期情景一句话",
                  "episodic_hooks": ["可选：仍活跃的短期话题钩子"],
                  "stable_facts": ["可选：长期稳定事实，短句"],
                  "profile_delta": { "任意键": "任意值" },
                  "forget_candidates": ["可选：建议丢弃的琐碎原句"],
                  "merge_notes": "可选：给前台/模型的合并说明"
                }
                """);
        register(
                "planet_ingest_system",
                PromptTemplateKind.SYSTEM,
                PromptTemplateDomain.PLANET,
                "zh-CN",
                """
                你是对话知识沉淀助手。根据本轮用户问题与助手回复，判断是否值得沉淀为一条「知识节点」。
                只输出严格 JSON（不要 markdown），格式：
                {"skip":true}
                或
                {"skip":false,"title":"不超过24字标题","summary":"1～3句摘要","topicTags":["主题分类","子标签1","子标签2"]}
                topicTags 第一项为「知识星球」主题名（如：排序算法、Java、前端工程化），决定星系中的星球；第 2 项起为子标签（技术名、语言、算法名等，便于与历史节点关联）。
                规则：
                1. 若用户消息中给出【已有主题星球】，且本轮属于同一技术领域，topicTags[0] 必须与列表中某一项完全一致，勿为相近话题另造新名（如已有「排序算法」则勿写「Java排序」「算法」）。
                2. 同一对话内的追问、换语言实现、对比、延伸（如「五种语言冒泡排序」接在「十大排序」后）应沉淀，skip 仅用于纯寒暄或完全无新信息的重复。
                3. 子标签尽量包含能串联历史节点的关键词（如：排序、冒泡、Java、多语言）。
                """);
        register(
                "planet_weekly_system",
                PromptTemplateKind.SYSTEM,
                PromptTemplateDomain.PLANET,
                "zh-CN",
                """
                你是个人成长教练。根据用户过去一周的对话知识节点与记忆摘要，生成本周成长方案。
                只输出严格 JSON（不要 markdown）：
                {"summary":"一句话总览","thinkDirections":["方向1"],"gapAreas":["不足1"],"bookRecommendations":[{"title":"书名","reason":"理由"}]}
                thinkDirections 3～5 条；gapAreas 2～4 条；bookRecommendations 2～4 本。
                """);
        register(
                "chat_assistant_persona",
                PromptTemplateKind.FRAGMENT,
                PromptTemplateDomain.CHAT,
                "zh-CN",
                "你是 Ai 中台助手。");
        register(
                "chat_assistant_persona",
                PromptTemplateKind.FRAGMENT,
                PromptTemplateDomain.CHAT,
                "en-US",
                "You are the Ai platform assistant.");
        register(
                "chat_language_directive",
                PromptTemplateKind.FRAGMENT,
                PromptTemplateDomain.CHAT,
                "zh-CN",
                "\n\n【回复语种】请使用简体中文回复。若用户明确要求使用其他语言，则按用户要求。");
        register(
                "chat_language_directive",
                PromptTemplateKind.FRAGMENT,
                PromptTemplateDomain.CHAT,
                "en-US",
                "\n\n【Response language】Reply in English. If the user explicitly asks for another language, follow the user.");
        register(
                "profile_guard_header",
                PromptTemplateKind.FRAGMENT,
                PromptTemplateDomain.GUARD,
                "zh-CN",
                "【内部·跨会话参考·勿直接向用户暴露】\n");
        register(
                "profile_guard_header",
                PromptTemplateKind.FRAGMENT,
                PromptTemplateDomain.GUARD,
                "en-US",
                "【Internal·cross-session context·do not expose to the user】\n");
        register(
                "profile_guard_usage",
                PromptTemplateKind.FRAGMENT,
                PromptTemplateDomain.GUARD,
                "zh-CN",
                """
                \n\n【使用规则】以上内容来自其它会话/设备的后台记忆，不是用户在本聊天窗口内可见的历史。禁止在思考过程或正文中对用户宣称「您问过/说过多次」「您反复问过」「您之前问过」等，除非下方「历史对话」中已出现相同用户发言。${first_turn_extra}可静默利用记忆改善回答，勿向用户复述其中的统计或元信息。
                """);
        register(
                "profile_guard_usage",
                PromptTemplateKind.FRAGMENT,
                PromptTemplateDomain.GUARD,
                "en-US",
                """
                \n\n【Usage rules】The block above is backend memory from other chats/devices, not what the user sees in this window. Do not claim in reasoning or the reply that the user asked or said something "many times", "again", or "before", unless the conversation history below already contains the same user message. ${first_turn_extra}You may use the memory silently to personalize; do not narrate its metadata.
                """);
        register(
                "profile_guard_first_turn_extra",
                PromptTemplateKind.FRAGMENT,
                PromptTemplateDomain.GUARD,
                "zh-CN",
                "当前窗口尚无历史轮次：默认按用户在本窗口**首次提问**对待，勿根据本块推断其曾反复提问。");
        register(
                "profile_guard_first_turn_extra",
                PromptTemplateKind.FRAGMENT,
                PromptTemplateDomain.GUARD,
                "en-US",
                "This window has no prior turns: treat the latest user message as their first question in this chat unless history below shows otherwise. ");
        register(
                "rag_snippet_header",
                PromptTemplateKind.FRAGMENT,
                PromptTemplateDomain.RAG,
                "zh-CN",
                "可参考知识片段");
        register(
                "rag_snippet_header_web_hint",
                PromptTemplateKind.FRAGMENT,
                PromptTemplateDomain.RAG,
                "zh-CN",
                "（若与当前问题无关请忽略，并优先依据联网检索结果作答）");
        register(
                "web_search_query_rewrite_system",
                PromptTemplateKind.SYSTEM,
                PromptTemplateDomain.WEB,
                "zh-CN",
                """
                你是搜索引擎检索词专家。把用户的聊天内容压缩成一行「检索查询词」，供新闻站、RSS、HTML 搜索等抓取；不是写给 AI 的回答。

                规则：
                1. 只输出一行检索词，≤ 60 个汉字（或等价英文词）；禁止解释、markdown、引号、编号、换行。
                2. 保留：主题词、专有名词、地域（如中国/上海）、时间意图（今日/本周/最近/${year}年）；多主题用空格分隔，不要写成完整问句。
                3. 删除：对 AI 的称呼与指令、礼貌用语（请/帮我）、「联网/搜索/查一下」等动作词、与检索无关的格式要求。
                4. 热点/资讯类可保留「热点 资讯 最新」等检索常用词；用户已列出关键词时做去重与归一化，勿擅自编造具体日期（除非用户写明）。
                5. 禁止拒答或说明无法联网；只做关键词抽取。

                示例：
                用户：请联网搜今天中国科技财经教育热点
                检索词：中国 科技 财经 教育 热点 资讯 今日 最新

                用户：2026年6G进展
                检索词：6G 进展 中国 ${year} 最新
                """);
        register(
                "web_search_query_rewrite_user",
                PromptTemplateKind.USER,
                PromptTemplateDomain.WEB,
                "zh-CN",
                """
                当前日期：${today}（${year} 年）
                将下列用户消息改写为一行检索查询词（仅输出检索词本身）：

                ${user_message}
                """);
        register(
                "websearch_summary_header",
                PromptTemplateKind.FRAGMENT,
                PromptTemplateDomain.WEB,
                "zh-CN",
                "【网络检索摘要】\n");
        register(
                "websearch_citation_header",
                PromptTemplateKind.FRAGMENT,
                PromptTemplateDomain.WEB,
                "zh-CN",
                "【引用】\n");
        register(
                "user_attachment_marker",
                PromptTemplateKind.FRAGMENT,
                PromptTemplateDomain.CHAT,
                "zh-CN",
                "\n\n【以下为用户上传文档摘要，请结合回答】");
        register(
                "attachment_context_intro",
                PromptTemplateKind.FRAGMENT,
                PromptTemplateDomain.CHAT,
                "zh-CN",
                "用户上传了以下文档，正文已合并进 user 消息；请遵守引用边界，勿编造未出现的事实。");
        registerFragment("follow_up_fallback", List.of("能再具体说说吗？", "还有其他需要注意的吗？", "请举一个实际例子"));
        registerFragment(
                "starter_empty_fallback",
                List.of("写一首关于春天的诗", "用通俗语言解释量子纠缠", "帮我生成一份周报模板"));
    }

    private PromptTemplateBuiltinCatalog() {}

    private static void register(
            String code, PromptTemplateKind kind, PromptTemplateDomain domain, String locale, String content) {
        BY_CODE_LOCALE.put(key(code, locale), new Entry(code, kind, domain, locale, content.trim()));
    }

    private static void registerFragment(String prefix, List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            register(prefix + "_" + (i + 1), PromptTemplateKind.FRAGMENT, PromptTemplateDomain.STARTER, "zh-CN", lines.get(i));
        }
    }

    private static String key(String code, String locale) {
        return code + "@" + locale;
    }

    public static Optional<Entry> find(String promptCode, String locale) {
        String code = promptCode == null ? "" : promptCode.trim();
        if (code.isBlank()) {
            return Optional.empty();
        }
        String loc = locale == null || locale.isBlank() ? "zh-CN" : locale.trim();
        Entry exact = BY_CODE_LOCALE.get(key(code, loc));
        if (exact != null) {
            return Optional.of(exact);
        }
        if (!"*".equals(loc)) {
            Entry wild = BY_CODE_LOCALE.get(key(code, "*"));
            if (wild != null) {
                return Optional.of(wild);
            }
        }
        if (!"zh-CN".equals(loc)) {
            Entry zh = BY_CODE_LOCALE.get(key(code, "zh-CN"));
            if (zh != null) {
                return Optional.of(zh);
            }
        }
        return Optional.empty();
    }

    public static List<Entry> allEntries() {
        return List.copyOf(BY_CODE_LOCALE.values());
    }
}
