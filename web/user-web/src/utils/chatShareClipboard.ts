import type { ChatShareTurn } from "./chatShareTurns";

export type ShareClipboardInput = {
  title: string;
  url: string;
  expiresAt: string | null;
  turns: ChatShareTurn[];
  selectedIndexes: number[];
  locale: string;
  t: (key: string, params?: Record<string, string | number>) => string;
};

function formatExpiresAt(iso: string | null, locale: string): string {
  if (!iso) {
    return "";
  }
  try {
    return new Date(iso).toLocaleString(locale);
  } catch {
    return iso;
  }
}

/** 网盘式分享文案：标题 + 链接 + 节选说明 + 打开指引 */
export function buildShareClipboardText(input: ShareClipboardInput): string {
  const { title, url, expiresAt, turns, selectedIndexes, locale, t } = input;
  const sel = new Set(selectedIndexes);
  const picked = turns.filter((x) => sel.has(x.index));

  const turnLines = picked.map((turn) => {
    const snippet = turn.snippet ? `：${turn.snippet}` : "";
    return `${turn.label}${snippet}`;
  });

  const expires = expiresAt
    ? t("chat.shareClipboardExpires", { time: formatExpiresAt(expiresAt, locale) })
    : t("chat.shareClipboardExpiresUnknown");

  return [
    t("chat.shareClipboardIntro", { title }),
    "",
    t("chat.shareClipboardLink", { url }),
    turnLines.length ? t("chat.shareClipboardTurns") : "",
    ...turnLines,
    "",
    expires,
    "",
    t("chat.shareClipboardHowTo"),
    "",
    t("chat.shareClipboardFooter"),
  ]
    .filter((line, idx, arr) => {
      if (line !== "") {
        return true;
      }
      return idx > 0 && arr[idx - 1] !== "";
    })
    .join("\n");
}
