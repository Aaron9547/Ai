package com.aaron.cloud.file;

import com.aaron.cloud.common.api.ports.FileStoragePort;
import com.aaron.cloud.common.context.TenantContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "ai.providers.file-storage", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageAdapter implements FileStoragePort {

    @Override
    public String presignUpload(Long tenantId, String bucket, String objectKey, String contentType) {
        TenantContextHolder.require();
        return "https://placeholder.local/"
                + tenantId
                + "/"
                + bucket
                + "/"
                + objectKey
                + "?contentType="
                + (contentType == null ? "" : contentType);
    }
}
