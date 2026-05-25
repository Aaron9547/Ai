package com.aaron.cloud.identity.rest.open;

import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.profile.ProfileDeviceMergeApplicationService;
import com.aaron.cloud.common.profile.UserProfilePrivacyApplicationService;
import com.aaron.cloud.common.web.rest.OpenV1ControllerBases;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 已登录用户：导出/删除画像与分层记忆、按设备码归并访客数据等（须 JWT；见安全配置 {@code /open/v1/profile/**}）。
 */
@RestController
@RequiredArgsConstructor
public class UserProfilePrivacyController extends OpenV1ControllerBases.Profile {

    private final UserProfilePrivacyApplicationService userProfilePrivacyApplicationService;
    private final ProfileDeviceMergeApplicationService profileDeviceMergeApplicationService;

    @GetMapping(value = "/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode export() throws Exception {
        var snap = TenantContextHolder.require();
        if (snap.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return userProfilePrivacyApplicationService.exportJson(snap.getTenantId(), snap.getUserId());
    }

    @DeleteMapping("/data")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void purge() {
        var snap = TenantContextHolder.require();
        if (snap.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        userProfilePrivacyApplicationService.purgeUserProfile(snap.getTenantId(), snap.getUserId());
    }

    /**
     * 已登录：将<strong>本租户</strong>下指定访客设备码（{@code d:} 主体）的会话与画像/记忆归并到当前用户；可多次调用以合并多台未登录设备。
     */
    @PostMapping(value = "/merge-guest-device", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ProfileDeviceMergeApplicationService.MergeOutcome mergeGuestDevice(@RequestBody MergeGuestDeviceRequest body) {
        var snap = TenantContextHolder.require();
        if (snap.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        String raw = body == null || body.getDeviceId() == null ? "" : body.getDeviceId();
        return profileDeviceMergeApplicationService.mergeGuestDeviceToUser(
                snap.getTenantId(), snap.getUserId(), raw);
    }

    @Data
    public static class MergeGuestDeviceRequest {
        /** 另一台设备「复制设备码」得到的 UUID，与 {@code X-Device-Id} 格式一致。 */
        private String deviceId;
    }
}
