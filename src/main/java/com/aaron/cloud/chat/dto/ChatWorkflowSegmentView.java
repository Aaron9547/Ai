package com.aaron.cloud.chat.dto;

/** 助手消息中意图工作流阶段快照（与 SSE {@code workflowStage} 帧字段对齐，供历史展示）。 */
public record ChatWorkflowSegmentView(
        String segmentId,
        String title,
        String mode,
        String status,
        String text) {}
