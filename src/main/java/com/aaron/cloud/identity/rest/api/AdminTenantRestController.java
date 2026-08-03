package com.aaron.cloud.identity.rest.api;

import com.aaron.cloud.common.api.enums.tenant.TenantStatus;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.tenant.entity.SysTenant;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.identity.tenant.TenantAdminApplicationService;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminTenantRestController extends ApiV1ControllerBases.AdminTenants {

    private final TenantAdminApplicationService tenantAdminApplicationService;

    @GetMapping
    public List<SysTenant> list() {
        TenantContextHolder.require();
        return tenantAdminApplicationService.listAll();
    }

    @PostMapping
    public SysTenant create(@RequestBody CreateTenantBody body) {
        TenantContextHolder.require();
        return tenantAdminApplicationService.create(body.getCode(), body.getName());
    }

    @PutMapping("/{id}")
    public SysTenant update(@PathVariable long id, @RequestBody UpdateTenantBody body) {
        TenantContextHolder.require();
        return tenantAdminApplicationService.update(id, body.getName(), body.getStatus());
    }

    @GetMapping("/{id}/admin-menus")
    public List<String> listAdminMenus(@PathVariable long id) {
        TenantContextHolder.require();
        return tenantAdminApplicationService.listTenantAdminMenus(id);
    }

    @PutMapping("/{id}/admin-menus")
    public void replaceAdminMenus(@PathVariable long id, @RequestBody ReplaceAdminMenusBody body) {
        TenantContextHolder.require();
        tenantAdminApplicationService.replaceTenantAdminMenus(id, body.getMenuCodes());
    }

    @Data
    public static class ReplaceAdminMenusBody {
        private List<String> menuCodes;
    }

    @Data
    public static class CreateTenantBody {
        @NotBlank private String code;
        @NotBlank private String name;
    }

    @Data
    public static class UpdateTenantBody {
        private String name;
        private TenantStatus status;
    }
}
