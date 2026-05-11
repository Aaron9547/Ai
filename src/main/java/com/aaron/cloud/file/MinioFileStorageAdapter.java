package com.aaron.cloud.file;

import com.aaron.cloud.common.api.ports.FileStoragePort;
import com.aaron.cloud.common.context.TenantContextHolder;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.providers.file-storage", havingValue = "minio")
public class MinioFileStorageAdapter implements FileStoragePort {

    private final MinioClient minioClient;

    @Override
    public String presignUpload(Long tenantId, String bucket, String objectKey, String contentType) {
        TenantContextHolder.require();
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(60, TimeUnit.MINUTES)
                            .extraQueryParams(
                                    contentType == null || contentType.isBlank()
                                            ? java.util.Map.of()
                                            : java.util.Map.of("Content-Type", contentType))
                            .build());
        } catch (Exception e) {
            log.error(
                    "minio presign failed tenantId={} bucket={} objectKey={}",
                    tenantId,
                    bucket,
                    objectKey,
                    e);
            throw new IllegalStateException("minio presign failed", e);
        }
    }
}
