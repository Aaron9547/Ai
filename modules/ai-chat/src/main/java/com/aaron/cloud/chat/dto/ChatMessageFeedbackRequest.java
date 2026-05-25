package com.aaron.cloud.chat.dto;

import com.aaron.cloud.common.api.enums.chat.ChatMessageUserFeedback;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatMessageFeedbackRequest {

    /** {@link ChatMessageUserFeedback#NONE} 琛ㄧず娓呴櫎璇勪环锛泏@link ChatMessageUserFeedback#LIKE} / {@link ChatMessageUserFeedback#DISLIKE} 浜掓枼鎸佷箙鍖栥€?*/
    @NotNull
    private ChatMessageUserFeedback vote;
}
