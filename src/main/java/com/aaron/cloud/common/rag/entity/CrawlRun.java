package com.aaron.cloud.common.rag.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("crawl_run")
public class CrawlRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long kbId;
    private Long siteId;
    private String baseUrl;
    private String syncMode;
    private String preset;
    private String policySummary;
    private String status;
    private String statsJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
