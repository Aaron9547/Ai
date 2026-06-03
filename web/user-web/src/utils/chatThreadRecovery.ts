/** 对话线程断连恢复：判断本地是否落后于服务端已落库内容。 */

export type ThreadRecoveryMsg = {
  role: string;
  content?: string;
  reasoning?: string;
  streaming?: boolean;
  id?: number;
  activeVariantIndex?: number;
  replyVariants?: Array<{
    id?: number;
    content?: string;
    reasoning?: string;
  }>;
};

export function findLastAssistantIndex(msgs: ThreadRecoveryMsg[]): number {
  for (let i = msgs.length - 1; i >= 0; i--) {
    if (msgs[i]?.role === "assistant") {
      return i;
    }
  }
  return -1;
}

export function assistantPersistedId(m: ThreadRecoveryMsg): number | undefined {
  if (m.role !== "assistant") {
    return undefined;
  }
  if (m.replyVariants?.length) {
    for (let i = m.replyVariants.length - 1; i >= 0; i--) {
      const id = m.replyVariants[i]?.id;
      if (id != null) {
        return id;
      }
    }
  }
  return m.id;
}

/** 助手正文体量（含思考），用于比较本地与服务端完整度。 */
export function assistantBodyTextLen(m: ThreadRecoveryMsg): number {
  let best = (m.content ?? "").length + (m.reasoning ?? "").length;
  const vs = m.replyVariants;
  if (vs?.length) {
    const tail = vs[vs.length - 1]!;
    best = Math.max(best, (tail.content ?? "").length + (tail.reasoning ?? "").length);
  }
  return best;
}

/** 本地线程是否可能因断连/后台挂起而落后于服务端。 */
export function threadNeedsRecovery(msgs: ThreadRecoveryMsg[]): boolean {
  const idx = findLastAssistantIndex(msgs);
  if (idx < 0) {
    return false;
  }
  const m = msgs[idx]!;
  if (m.streaming) {
    return true;
  }
  const hasBody = assistantBodyTextLen(m) > 0;
  return hasBody && assistantPersistedId(m) == null;
}

export function serverAssistantRicher(local: ThreadRecoveryMsg, server: ThreadRecoveryMsg): boolean {
  if (local.role !== "assistant" || server.role !== "assistant") {
    return false;
  }
  const localLen = assistantBodyTextLen(local);
  const serverLen = assistantBodyTextLen(server);
  if (serverLen > localLen) {
    return true;
  }
  const localId = assistantPersistedId(local);
  const serverId = assistantPersistedId(server);
  if (serverId != null && localId == null) {
    return true;
  }
  if (local.streaming && serverId != null && !server.streaming) {
    return true;
  }
  return false;
}

export function threadContentTrimEqual(a: ThreadRecoveryMsg, b: ThreadRecoveryMsg): boolean {
  return (a.content ?? "").trim() === (b.content ?? "").trim();
}

/**
 * 是否应整表替换为服务端列表（条数更多，或同长但最后一条助手明显更完整）。
 * 不会在服务端条数更少或助手更短时替换，避免把半截本地盖成更短的服务端草稿。
 */
export function shouldReplaceThreadWithServer(
  local: ThreadRecoveryMsg[],
  server: ThreadRecoveryMsg[],
): boolean {
  if (!server.length) {
    return false;
  }
  if (server.length > local.length) {
    return true;
  }
  if (server.length < local.length) {
    return false;
  }
  for (let i = 0; i < local.length; i++) {
    const l = local[i]!;
    const s = server[i]!;
    if (l.role !== s.role) {
      return true;
    }
    if (l.role === "assistant" && serverAssistantRicher(l, s)) {
      return true;
    }
    if (l.role !== "assistant" && !threadContentTrimEqual(l, s)) {
      return true;
    }
    if (l.role === "assistant" && !threadContentTrimEqual(l, s) && !serverAssistantRicher(l, s)) {
      const localLen = assistantBodyTextLen(l);
      const serverLen = assistantBodyTextLen(s);
      if (serverLen >= localLen) {
        return true;
      }
    }
  }
  return false;
}

/** 同条数时是否仅补丁最后一条助手（服务端更长或已落库 id）。 */
export function shouldPatchLastAssistant(
  local: ThreadRecoveryMsg[],
  server: ThreadRecoveryMsg[],
): boolean {
  if (local.length !== server.length || !local.length) {
    return false;
  }
  const li = findLastAssistantIndex(local);
  const si = findLastAssistantIndex(server);
  if (li < 0 || si < 0 || li !== si) {
    return false;
  }
  return serverAssistantRicher(local[li]!, server[si]!);
}
