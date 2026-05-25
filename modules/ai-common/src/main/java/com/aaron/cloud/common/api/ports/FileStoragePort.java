package com.aaron.cloud.common.api.ports;

public interface FileStoragePort {

    String presignUpload(Long tenantId, String bucket, String objectKey, String contentType);
}
