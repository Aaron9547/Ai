/** 与库表 {@code chat_intent_keyword} 种子对齐，用于发送框内轮播试意图。 */
export type IntentQuickLaneId = "reminder" | "travel";

export type IntentQuickLane = {
  id: IntentQuickLaneId;
  phrases: string[];
};

export const INTENT_QUICK_ROTATE_MS = 3200;

export const INTENT_QUICK_LANES: IntentQuickLane[] = [
  {
    id: "reminder",
    phrases: ["提醒我每天8点签到", "提醒我", "定时提醒", "取消提醒"],
  },
  {
    id: "travel",
    phrases: ["出差报销", "差旅报销", "报销差旅费"],
  },
];

export type IntentQuickPhraseEntry = { text: string; laneId: IntentQuickLaneId };

/** 轮播序列：先提醒类话术，再出差类（各条约 {@link INTENT_QUICK_ROTATE_MS} 切换）。 */
export function intentQuickPhraseEntries(): IntentQuickPhraseEntry[] {
  return INTENT_QUICK_LANES.flatMap((lane) =>
    lane.phrases.map((text) => ({ text, laneId: lane.id })),
  );
}
