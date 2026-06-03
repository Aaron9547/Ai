package com.aaron.cloud.chat.intent.followup;

/** 意图回合结束时下发的多轮追问阶段（由 {@link IntentFollowUpPromptCatalog} 统一解析文案）。 */
public enum IntentFollowUpStage {
    /** 不下发追问 chip。 */
    NONE,
    /** 出差：材料阶段完成，等待用户进入行程阶段。 */
    TRAVEL_AFTER_DOC_COMPLETE,
    /** 一句话办事：回合成功后可取消/管理提醒。 */
    REMINDER_AFTER_SUCCESS
}
