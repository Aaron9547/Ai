package com.aaron.cloud.chat.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class CreateChatShareRequest {

    /** 纳入分享的消息 id（须属于该会话，建议为一轮 user+assistant 或连续多轮）。 */
    @NotEmpty
    private List<Long> messageIds;
}
