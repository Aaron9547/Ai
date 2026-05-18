/** fetch / ReadableStream 在 AbortController.abort() 后抛出的错误 */
export function isAbortError(e: unknown): boolean {
  if (e instanceof DOMException && e.name === "AbortError") {
    return true;
  }
  if (e instanceof Error && e.name === "AbortError") {
    return true;
  }
  return false;
}
