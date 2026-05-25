package com.aaron.cloud.gateway.accessparty;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AccessPartyRequestAttributes {

    public static final String ACCESS_PARTY_MODE = "accessPartyMode";
    public static final String MATCHED_ENDPOINT_ID = "accessPartyMatchedEndpointId";
    public static final String MATCHED_GRANT_ID = "accessPartyMatchedGrantId";
}
