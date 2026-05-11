package com.aaron.cloud.gateway.rest.api;

import com.aaron.cloud.common.api.enums.TenantMemberRole;
import com.aaron.cloud.common.api.enums.ToggleState;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.gateway.entity.GwCorsAllowedOrigin;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.gateway.CorsAllowedOriginApplicationService;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminCorsAllowedOriginRestController extends ApiV1ControllerBases.AdminCorsAllowedOrigins {

    private final CorsAllowedOriginApplicationService corsAllowedOriginApplicationService;

    @GetMapping
    public List<GwCorsAllowedOrigin> list() {
        assertFounderForCorsAdmin();
        return corsAllowedOriginApplicationService.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GwCorsAllowedOrigin create(@RequestBody UpsertBody body) {
        assertFounderForCorsAdmin();
        return corsAllowedOriginApplicationService.create(
                body.getOrigin(),
                body.getEnabled(),
                body.getSortOrder() == null ? 0 : body.getSortOrder(),
                body.getRemark());
    }

    @PutMapping("/{id}")
    public GwCorsAllowedOrigin update(@PathVariable long id, @RequestBody UpsertBody body) {
        assertFounderForCorsAdmin();
        return corsAllowedOriginApplicationService.update(
                id, body.getOrigin(), body.getEnabled(), body.getSortOrder(), body.getRemark());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        assertFounderForCorsAdmin();
        corsAllowedOriginApplicationService.delete(id);
    }

    /** CORS 允许来源为<strong>全平台</strong>配置（表无 tenant_id）；仅创始人可读写，避免租户管理员看到其它租户相关全局表。 */
    private static void assertFounderForCorsAdmin() {
        var snap = TenantContextHolder.require();
        TenantMemberRole role = snap.getMemberRole();
        if (role == null || !role.isFounder()) {
            throw new AccessDeniedException("仅创始人可管理跨域允许来源");
        }
    }

    @Data
    public static class UpsertBody {
        private String origin;
        private ToggleState enabled;
        private Integer sortOrder;
        private String remark;
    }
}
