package com.aaron.cloud.chat;

/**
 * 跨会话画像/记忆注入模型的可读性护栏：避免思考链或正文把后台统计误说成「用户在本窗口问过多次」。
 */
public final class ProfilePromptGuard {

    private ProfilePromptGuard() {}

    public static String crossSessionBlockHeader(String responseLocale) {
        if (ChatResponseLocalePrompt.isEnglish(responseLocale)) {
            return "【Internal·cross-session context·do not expose to the user】\n";
        }
        return "【内部·跨会话参考·勿直接向用户暴露】\n";
    }

    /**
     * @param currentWindowHasNoPriorTurns 当前会话在装入提示词前是否尚无历史轮次（新窗口首轮）
     */
    public static void appendUsageDirective(
            StringBuilder sys, String responseLocale, boolean currentWindowHasNoPriorTurns) {
        if (ChatResponseLocalePrompt.isEnglish(responseLocale)) {
            sys.append(
                    "\n\n【Usage rules】The block above is backend memory from other chats/devices, "
                            + "not what the user sees in this window. "
                            + "Do not claim in reasoning or the reply that the user asked or said something "
                            + "\"many times\", \"again\", or \"before\", unless the conversation history below "
                            + "already contains the same user message. ");
            if (currentWindowHasNoPriorTurns) {
                sys.append(
                        "This window has no prior turns: treat the latest user message as their first "
                                + "question in this chat unless history below shows otherwise. ");
            }
            sys.append("You may use the memory silently to personalize; do not narrate its metadata.\n");
        } else {
            sys.append(
                    "\n\n【使用规则】以上内容来自其它会话/设备的后台记忆，不是用户在本聊天窗口内可见的历史。"
                            + "禁止在思考过程或正文中对用户宣称「您问过/说过多次」「您反复问过」「您之前问过」等，"
                            + "除非下方「历史对话」中已出现相同用户发言。");
            if (currentWindowHasNoPriorTurns) {
                sys.append("当前窗口尚无历史轮次：默认按用户在本窗口**首次提问**对待，勿根据本块推断其曾反复提问。");
            }
            sys.append("可静默利用记忆改善回答，勿向用户复述其中的统计或元信息。\n");
        }
    }
}
