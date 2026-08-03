package com.aaron.cloud.common.context;

import lombok.Builder;
import lombok.Value;

/** 当前请求已验签的接入方快照（仅 gateway Filter 写入）。 */
@Value
@Builder
public class AccessPartySnapshot {
    Long accessPartyId;
    Long tenantId;
    String appId;
    String displayName;
}
