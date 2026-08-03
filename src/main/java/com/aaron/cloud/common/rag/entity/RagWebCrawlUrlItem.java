package com.aaron.cloud.common.rag.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("rag_web_crawl_url_item")
public class RagWebCrawlUrlItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long scheduleId;
    private String url;
    private Long documentId;
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
