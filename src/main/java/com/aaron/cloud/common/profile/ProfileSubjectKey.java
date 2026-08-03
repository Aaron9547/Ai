package com.aaron.cloud.common.profile;

import com.aaron.cloud.common.context.TenantSnapshot;

/**
 * 画像与记忆表使用的 {@code subject_key}：已登录 {@code u:{id}}，访客 {@code d:{deviceId}}；
 * 无会话主体共用的今日推荐冷启动 {@code tc:{tenantId}}。
 */
public final class ProfileSubjectKey {

    private ProfileSubjectKey() {}

    /** 租户级今日推荐冷启动（未登录且无对话、或已登录但无对话时共用，按日一条）。 */
    public static String tenantColdStartKey(long tenantId) {
        return "tc:" + tenantId;
    }

    public static String fromSnapshot(TenantSnapshot snap) {
        if (snap.getUserId() != null) {
            return userKey(snap.getUserId());
        }
        if (snap.getDeviceId() != null && !snap.getDeviceId().isBlank()) {
            return deviceKey(snap.getDeviceId());
        }
        return null;
    }

    public static String userKey(long userId) {
        return "u:" + userId;
    }

    public static String deviceKey(String deviceId) {
        return "d:" + deviceId.trim();
    }
}
