/** 一轮对话：用户提问 + 助手回复 */
export type ChatShareTurn = {
  index: number;
  userIdx: number;
  assistantIdx: number;
  userId?: number;
  assistantId?: number;
  label: string;
  /** 用户提问摘要，用于紧凑选择器展示 */
  snippet: string;
};

function turnSnippet(text: string | undefined, maxLen = 24): string {
  const raw = (text ?? "").replace(/\s+/g, " ").trim();
  if (!raw) {
    return "";
  }
  return raw.length > maxLen ? `${raw.slice(0, maxLen)}…` : raw;
}

type ShareMsgLike = {
  id?: number;
  role: "user" | "assistant" | "system";
  content?: string;
};

export function buildChatShareTurns(
  messages: ShareMsgLike[],
  labelFn: (n: number) => string,
): ChatShareTurn[] {
  const turns: ChatShareTurn[] = [];
  for (let i = 0; i < messages.length; i++) {
    const m = messages[i];
    if (m.role !== "assistant") {
      continue;
    }
    let userIdx = -1;
    for (let j = i - 1; j >= 0; j--) {
      if (messages[j]?.role === "user") {
        userIdx = j;
        break;
      }
      if (messages[j]?.role === "assistant") {
        break;
      }
    }
    if (userIdx < 0) {
      continue;
    }
    const user = messages[userIdx]!;
    turns.push({
      index: turns.length,
      userIdx,
      assistantIdx: i,
      userId: user.id,
      assistantId: m.id,
      label: labelFn(turns.length + 1),
      snippet: turnSnippet(user.content),
    });
  }
  return turns;
}

export function findTurnByAssistantIndex(turns: ChatShareTurn[], assistantIdx: number): number {
  const t = turns.find((x) => x.assistantIdx === assistantIdx);
  return t ? t.index : turns.length > 0 ? turns[turns.length - 1]!.index : 0;
}

export function messageIdsForTurns(
  turns: ChatShareTurn[],
  selectedIndexes: number[],
): number[] {
  const ids: number[] = [];
  const set = new Set(selectedIndexes);
  for (const t of turns) {
    if (!set.has(t.index)) {
      continue;
    }
    if (t.userId != null) {
      ids.push(t.userId);
    }
    if (t.assistantId != null) {
      ids.push(t.assistantId);
    }
  }
  return ids;
}
