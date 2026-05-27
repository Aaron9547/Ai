package com.aaron.cloud.common.context;

import com.aaron.cloud.common.api.enums.tenant.TenantMemberRole;
import lombok.Builder;
import lombok.Value;

/** 当前请求的租户/用户 id 快照，由 {@link TenantContextHolder} 持有。 */
@Value
@Builder
public class TenantSnapshot {
    Long tenantId;
    Long userId;
    String deviceId;
    /** 来自 JWT {@code tmr}，开放接口可能为 null */
    @Builder.Default
    TenantMemberRole memberRole = null;
}
