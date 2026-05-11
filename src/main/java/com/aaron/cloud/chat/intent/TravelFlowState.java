package com.aaron.cloud.chat.intent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
final class TravelFlowState {
    private String stage;
    private String docSummary;
    private boolean planDirectConsumed;
    private boolean applyConfirmed;
    private long updatedAtMs;
}
