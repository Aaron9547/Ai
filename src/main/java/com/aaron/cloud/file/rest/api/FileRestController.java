package com.aaron.cloud.file.rest.api;

import com.aaron.cloud.common.api.ports.FileStoragePort;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FileRestController extends ApiV1ControllerBases.FileRoot {

    private final FileStoragePort fileStoragePort;

    @PostMapping("/presign-upload")
    public PresignResponse presign(@RequestBody PresignBody body) {
        var snap = TenantContextHolder.require();
        String url =
                fileStoragePort.presignUpload(
                        snap.getTenantId(), body.getBucket(), body.getObjectKey(), body.getContentType());
        return new PresignResponse(url);
    }

    @Data
    public static class PresignBody {
        private String bucket;
        private String objectKey;
        private String contentType;
    }

    public record PresignResponse(String url) {}
}
