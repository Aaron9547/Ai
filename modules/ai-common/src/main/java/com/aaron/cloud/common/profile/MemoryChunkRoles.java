package com.aaron.cloud.common.profile;

/** {@link com.aaron.cloud.common.profile.entity.TenUserMemoryChunk#getChunkRole()} 取值约定。 */
public final class MemoryChunkRoles {

    public static final String USER = "USER";
    public static final String ASSISTANT = "ASSISTANT";
    /** 用户点击「今日画像推荐」资讯卡片。 */
    public static final String INTEREST_NEWS = "INTEREST_NEWS";

    private MemoryChunkRoles() {}
}
