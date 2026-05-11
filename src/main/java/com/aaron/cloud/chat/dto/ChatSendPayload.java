package com.aaron.cloud.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class ChatSendPayload {

    @NotBlank
    @Size(max = 8000)
    private String content;

    @NotBlank private String modelAlias;

    /** 浠呭綋妯″瀷鏀寔鎬濊€冧笖鐢ㄦ埛寮€鍚椂锛孲SE 鍙兘涓嬪彂 reasoning 鍒嗙墖 */
    private boolean thinkingEnabled;

    private List<Long> attachmentIds;
}
