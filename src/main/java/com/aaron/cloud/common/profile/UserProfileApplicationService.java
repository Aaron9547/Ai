package com.aaron.cloud.common.profile;

import com.aaron.cloud.common.api.enums.ProfileTagCode;
import com.aaron.cloud.common.context.TenantContextHolder.TenantSnapshot;
import com.aaron.cloud.common.profile.entity.TenProfileTag;
import java.util.List;
import java.util.StringJoiner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 租户内用户/设备画像片段：写入 {@code ten_profile_tag}，供 {@code chat} 编排注入系统提示。
 *
 * <p><strong>语义边界</strong>：{@link ProfileTagCode#TURN_COUNT} 为<strong>同一主体</strong>（{@code u:userId} 或 {@code d:deviceId}）在租户内<strong>跨会话累加</strong>的发言次数，<strong>不是</strong>当前
 * {@code chat_conversation} 内的消息条数；注入模型时的文案须避免被理解为「本会话已进行 N 轮」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileApplicationService {

    private final TenProfileTagRepository tenProfileTagRepository;
    private final UserMemoryApplicationService userMemoryApplicationService;

    /** 用户发言落库后调用：跨会话累加主体发言计数、记录最近一条输入摘要；并写入具体层记忆片段。 */
    public void ingestAfterUserUtterance(
            TenantSnapshot snap, String utterance, Long conversationIdOrNull, String modelAliasOrNull) {
        String subjectKey = ProfileSubjectKey.fromSnapshot(snap);
        if (subjectKey == null) {
            return;
        }
        long tenantId = snap.getTenantId();
        try {
            int nextTurn =
                    tenProfileTagRepository
                            .find(tenantId, subjectKey, ProfileTagCode.TURN_COUNT)
                            .map(t -> parsePositiveInt(t.getTagValue(), 0) + 1)
                            .orElse(1);
            upsertTag(tenantId, subjectKey, ProfileTagCode.TURN_COUNT, String.valueOf(nextTurn));

            String excerpt = normalizeExcerpt(utterance);
            upsertTag(tenantId, subjectKey, ProfileTagCode.LAST_USER_EXCERPT, excerpt);
        } catch (Exception ex) {
            log.warn(
                    "profile ingest failed tenantId={} subjectKey={}",
                    tenantId,
                    subjectKey,
                    ex);
        }
        userMemoryApplicationService.afterUserUtterance(snap, utterance, conversationIdOrNull, modelAliasOrNull);
    }

    /**
     * 拼入首条 system 前的短摘要；无数据时返回空串。字段含义须与模型可读性一致：计数为<strong>跨会话历史累计</strong>，勿与会话内轮次混淆。
     *
     * @param recallQuery 当前用户输入，用于具体层记忆的关键词召回（无命中时附最近片段）。
     */
    public String buildPromptAddendum(TenantSnapshot snap, String recallQuery) {
        String subjectKey = ProfileSubjectKey.fromSnapshot(snap);
        if (subjectKey == null) {
            return "";
        }
        long tenantId = snap.getTenantId();
        List<TenProfileTag> rows =
                tenProfileTagRepository.listByTenantAndSubjectKey(tenantId, subjectKey);
        var tagJoiner = new StringJoiner("；");
        for (TenProfileTag t : rows) {
            if (t.getTagCode() == ProfileTagCode.TURN_COUNT) {
                tagJoiner.add("累计发言约 " + t.getTagValue() + " 次（跨会话，非本会话轮数）");
            } else if (t.getTagCode() == ProfileTagCode.LAST_USER_EXCERPT && !t.getTagValue().isBlank()) {
                tagJoiner.add("最近输入摘要：" + t.getTagValue());
            }
        }
        String tagPart = tagJoiner.toString();
        String memoryPart =
                userMemoryApplicationService.buildMemoryPromptSection(
                        snap, recallQuery == null ? "" : recallQuery);
        if (tagPart.isBlank() && memoryPart.isBlank()) {
            return "";
        }
        var out = new StringJoiner("\n\n");
        if (!tagPart.isBlank()) {
            out.add(tagPart);
        }
        if (!memoryPart.isBlank()) {
            out.add(memoryPart.trim());
        }
        return out.toString();
    }

    private void upsertTag(long tenantId, String subjectKey, ProfileTagCode code, String value) {
        var existing = tenProfileTagRepository.find(tenantId, subjectKey, code);
        if (existing.isPresent()) {
            TenProfileTag u = existing.get();
            u.setTagValue(value);
            tenProfileTagRepository.updateById(u);
        } else {
            var n = new TenProfileTag();
            n.setTenantId(tenantId);
            n.setSubjectKey(subjectKey);
            n.setTagCode(code);
            n.setTagValue(value);
            tenProfileTagRepository.insert(n);
        }
    }

    private static int parsePositiveInt(String raw, int defaultVal) {
        if (raw == null || raw.isBlank()) {
            return defaultVal;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ex) {
            return defaultVal;
        }
    }

    private static String normalizeExcerpt(String utterance) {
        if (utterance == null) {
            return "";
        }
        String s = utterance.strip().replace('\n', ' ').replace('\r', ' ');
        if (s.length() > 400) {
            return s.substring(0, 400);
        }
        return s;
    }
}
