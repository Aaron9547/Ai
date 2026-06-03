/** 在浏览器空闲或短超时后执行，避免阻塞首屏主链路。 */
export function scheduleIdle(fn: () => void, timeoutMs = 2000): void {
  if (typeof requestIdleCallback === "function") {
    requestIdleCallback(() => fn(), { timeout: timeoutMs });
  } else {
    setTimeout(fn, 0);
  }
}
