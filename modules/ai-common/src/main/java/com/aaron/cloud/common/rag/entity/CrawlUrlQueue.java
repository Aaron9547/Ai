package com.aaron.cloud.common.rag.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("crawl_url_queue")
public class CrawlUrlQueue {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;
    private Long tenantId;
    private String url;
    private String urlNorm;
    private String queueRole;
    private String status;
    private Integer score;
    private String sourcesJson;
    private String errorCode;
    private Integer retryCount;
    private String etag;
    private String lastModified;
    private Long documentId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
