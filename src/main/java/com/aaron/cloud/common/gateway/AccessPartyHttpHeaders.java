package com.aaron.cloud.common.gateway;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 接入方 HTTP 头名（南北向与 Feign 透传共用）。 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AccessPartyHttpHeaders {

    public static final String APP_ID = "X-App-Id";
    public static final String TIMESTAMP = "X-Timestamp";
    public static final String NONCE = "X-Nonce";
    public static final String SIGNATURE = "X-Signature";
    public static final String ACCESS_PARTY_ID = "X-Access-Party-Id";
    public static final String ACCESS_PARTY_APP_ID = "X-Access-Party-App-Id";
}
