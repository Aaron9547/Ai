package com.aaron.cloud.common.rag.entity;

import com.aaron.cloud.common.api.enums.rag.RagWebCrawlSyncMode;
import com.aaron.cloud.common.api.enums.scheduled.ScheduledTaskIntervalPreset;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Data;

/** 知识库站点定时爬取业务配置（由调度执行器扫描，非 ten_scheduled_task 行）。 */
@Data
@TableName("rag_web_crawl_site")
public class RagWebCrawlSite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long kbId;
    private String name;
    private String baseUrl;
    private Integer enabled;
    private ScheduledTaskIntervalPreset schedulePreset;
    private LocalTime runAtTime;
    private LocalDateTime lastCrawlAt;
    private Integer firstRunDone;
    private Long categoryId;
    private Integer chunkStrategy;
    private RagWebCrawlSyncMode syncMode;
    private Integer maxDepth;
    private Integer filterCrawled;

    /** JSON：{@link com.aaron.cloud.rag.RagWebCrawlExtractConfig} */
    private String extractConfig;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
