package com.aaron.cloud.common.profile;

import com.aaron.cloud.common.context.TenantContextHolder.TenantSnapshot;

/** 画像与记忆表使用的 {@code subject_key}：已登录 {@code u:{id}}，访客 {@code d:{deviceId}}。 */
public final class ProfileSubjectKey {

    private ProfileSubjectKey() {}

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
