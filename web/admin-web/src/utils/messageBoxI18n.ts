import { ElMessageBox } from "element-plus";
import type { ElMessageBoxOptions } from "element-plus";

type Translate = (key: string, ...args: unknown[]) => string;

/** 与 {@code common.confirm} / {@code common.cancel} 对齐，避免 MessageBox 默认英文 OK/Cancel。 */
export function messageBoxButtons(t: Translate): Pick<ElMessageBoxOptions, "confirmButtonText" | "cancelButtonText"> {
  return {
    confirmButtonText: t("common.confirm"),
    cancelButtonText: t("common.cancel"),
  };
}

/**
 * {@link ElMessageBox.confirm} 封装：签名固定为 message + title + options，
 * 禁止把 {@code { type }} 当作第二参数传入（会被当成 title，按钮仍为英文）。
 */
export function confirmMessageBox(
  t: Translate,
  message: string,
  options?: ElMessageBoxOptions & { title?: string },
) {
  const { title, ...rest } = options ?? {};
  return ElMessageBox.confirm(message, title ?? t("common.confirmTitle"), {
    ...messageBoxButtons(t),
    ...rest,
  });
}
