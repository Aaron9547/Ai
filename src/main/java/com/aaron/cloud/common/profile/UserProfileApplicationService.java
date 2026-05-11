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

    /** 用户发言落库后调用：跨会话累加主体发言计数、记录最近一条输入摘要（均写入画像，非单会话维度）。 */
    public void ingestAfterUserUtterance(TenantSnapshot snap, String utterance) {
        String subjectKey = subjectKey(snap);
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
    }

    /**
     * 拼入首条 system 前的短摘要；无数据时返回空串。字段含义须与模型可读性一致：计数为<strong>跨会话历史累计</strong>，勿与会话内轮次混淆。
     */
    public String buildPromptAddendum(TenantSnapshot snap) {
        String subjectKey = subjectKey(snap);
        if (subjectKey == null) {
            return "";
        }
        List<TenProfileTag> rows =
                tenProfileTagRepository.listByTenantAndSubjectKey(snap.getTenantId(), subjectKey);
        if (rows.isEmpty()) {
            return "";
        }
        var j = new StringJoiner("；");
        for (TenProfileTag t : rows) {
            if (t.getTagCode() == ProfileTagCode.TURN_COUNT) {
                j.add("历史累计发言约 " + t.getTagValue() + " 次（跨会话统计，非本条会话内轮数）");
            } else if (t.getTagCode() == ProfileTagCode.LAST_USER_EXCERPT && !t.getTagValue().isBlank()) {
                j.add("跨会话最近一条用户输入摘要：" + t.getTagValue());
            }
        }
        return j.toString();
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

    private static String subjectKey(TenantSnapshot snap) {
        if (snap.getUserId() != null) {
            return "u:" + snap.getUserId();
        }
        if (snap.getDeviceId() != null && !snap.getDeviceId().isBlank()) {
            return "d:" + snap.getDeviceId().trim();
        }
        return null;
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
