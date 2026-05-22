package com.aaron.cloud.common.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** {@code ten_profile_tag.tag_code} 与 Java 枚举一一对应。 */
@Getter
@RequiredArgsConstructor
public enum ProfileTagCode {
    /** 同一主体在租户内跨会话累加的用户发言次数，非当前会话消息条数。 */
    TURN_COUNT("TURN_COUNT"),
    /** 最近一次用户输入摘要（跨会话，以主体为键）。 */
    LAST_USER_EXCERPT("LAST_USER_EXCERPT"),
    /** 近期点击的资讯兴趣（JSON 数组，供推荐与画像注入）。 */
    INTEREST_NEWS_JSON("INTEREST_NEWS_JSON");

    @EnumValue
    private final String storageValue;
}
