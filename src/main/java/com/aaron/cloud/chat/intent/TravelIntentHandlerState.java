package com.aaron.cloud.chat.intent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 序列化进 {@link com.aaron.cloud.chat.intent.flow.IntentFlowSession#getHandlerStateJson()} 的差旅私有状态。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TravelIntentHandlerState {
    private String docSummary;
    private boolean planDirectConsumed;
    private boolean applyConfirmed;
}
