package com.aaron.cloud.chat.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 寮€鏀惧璇濆巻鍙蹭腑鐨勪竴鏉℃秷鎭紙鍚姪鎵嬩晶 meta 瑙ｆ瀽鍑虹殑鎬濊€冧笌 token 鐢ㄩ噺锛夈€? */
public record ChatMessageView(
        long id,
        String role,
        String content,
        String reasoning,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        LocalDateTime createdAt,
        /** 鍔╂墜娑堟伅 meta 涓殑妯″瀷鍒悕锛涚敤鎴?绯荤粺娑堟伅涓?{@code null} */
        String modelAlias,
        /** 鍔╂墜娑堟伅鐢ㄦ埛璇勪环锛歿@code LIKE}銆亄@code DISLIKE} 鎴?{@code null}锛堟湭璇勪环 / 宸叉竻闄わ級 */
        String userFeedback,
        /**
         * 閲嶆柊鐢熸垚鍓嶄繚鐣欑殑鍔╂墜蹇収锛堢敱 meta {@code priorVersions} 瑙ｆ瀽锛夛紱浠呭姪鎵嬫秷鎭€佷笖鏇鹃噸鏂扮敓鎴愭椂闈炵┖銆?         */
        List<PriorAssistantVersionView> priorVersions,
        /** RAG 鎰忓浘涓嬪啓鍏ュ姪鎵?meta 鐨勫紩鐢ㄥ垎鐗囧垪琛紱闈?RAG 鎴栨湭鍛戒腑鏃朵负 {@code null} 鎴栫┖鍒楄〃銆?*/
        List<RagCitationView> ragCitations,
        /** 鎰忓浘宸ヤ綔娴佸垎娈垫埅鍥撅紙浠?meta {@code workflowSegments} 瑙ｆ瀽锛夛紱闈炴剰鍥惧姪鎵嬫秷鎭负 {@code null} 鎴栫┖銆?*/
        List<ChatWorkflowSegmentView> workflowSegments,
        /** 联网检索引用（助手 meta {@code webSearchReferences}）；未开联网或未返回时 {@code null} 或空列表 */
        List<WebSearchReferenceView> webSearchReferences,
        /** 助手回复摘要（meta {@code contentSummary}），用于短期记忆与抽检；无则 {@code null} */
        String contentSummary,
        /** 本回合意图命中摘要（user/assistant meta 中 intentRouted / intentHandled 等）；无则 {@code null} */
        ChatIntentTurnHitView intentTurnHit,
        /** 用户消息 meta 中 {@code attachmentIds} 解析并关联 {@code chat_attachment}；非用户消息或非附件轮次为空列表 */
        List<ChatAttachmentMessageView> attachments) {}
