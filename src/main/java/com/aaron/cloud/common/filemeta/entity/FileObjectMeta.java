package com.aaron.cloud.common.filemeta.entity;

import com.aaron.cloud.common.api.enums.FileScanStatus;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("file_object_meta")
public class FileObjectMeta {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String bucket;
    private String objectKey;
    private Long sizeBytes;
    private String contentType;
    private FileScanStatus scanStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
