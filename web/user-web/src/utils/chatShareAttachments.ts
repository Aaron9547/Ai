import * as chatApi from "@/api/chat";

export type ShareCaptureUserAttachment = chatApi.ChatAttachmentMessage & {
  /** 截图/分享页展示用，图片附件预取 blob URL */
  previewUrl?: string;
};

export function isAttachImageKind(kind?: string | null): boolean {
  return kind === "image";
}

export async function prefetchShareImageAttachments(
  conversationId: string,
  attachments: chatApi.ChatAttachmentMessage[] | undefined | null,
): Promise<{ items: ShareCaptureUserAttachment[]; blobUrls: string[] }> {
  if (!attachments?.length) {
    return { items: [], blobUrls: [] };
  }
  const blobUrls: string[] = [];
  const items = await Promise.all(
    attachments.map(async (a) => {
      const item: ShareCaptureUserAttachment = { ...a };
      if (!isAttachImageKind(a.kind)) {
        return item;
      }
      try {
        const blob = await chatApi.fetchChatAttachmentBlob(conversationId, a.id);
        const previewUrl = URL.createObjectURL(blob);
        blobUrls.push(previewUrl);
        item.previewUrl = previewUrl;
      } catch {
        /* 保留文件名回退展示 */
      }
      return item;
    }),
  );
  return { items, blobUrls };
}

export function revokeShareAttachmentBlobUrls(urls: string[]): void {
  for (const url of urls) {
    if (url.startsWith("blob:")) {
      URL.revokeObjectURL(url);
    }
  }
}
