package com.aaron.cloud.identity.rest.open;

import com.aaron.cloud.common.web.rest.OpenV1ControllerBases;
import com.aaron.cloud.identity.tenant.TenantBrandLogoApplicationService;
import com.aaron.cloud.identity.tenant.TenantBrandLogoApplicationService.ServedLogo;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OpenAdminBrandLogoRestController extends OpenV1ControllerBases.AdminBrandLogos {

    private final TenantBrandLogoApplicationService tenantBrandLogoApplicationService;

    @GetMapping("/{tenantCode}/{fileName:.+}")
    public ResponseEntity<byte[]> get(@PathVariable String tenantCode, @PathVariable String fileName) throws Exception {
        Optional<ServedLogo> hit = tenantBrandLogoApplicationService.load(tenantCode, fileName);
        if (hit.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ServedLogo s = hit.get();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(1)).cachePublic())
                .header(HttpHeaders.CONTENT_TYPE, s.contentType())
                .body(s.bytes());
    }
}
