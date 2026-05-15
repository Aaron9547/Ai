package com.aaron.cloud.identity.rest.api;

import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.identity.tenant.TenantShellAdminApplicationService;
import com.aaron.cloud.identity.tenant.TenantShellAdminApplicationService.ShellBrandingPutBody;
import com.aaron.cloud.identity.tenant.TenantShellAdminApplicationService.ShellModelCallingPutBody;
import com.aaron.cloud.identity.tenant.TenantShellAdminApplicationService.ShellOutboundPutBody;
import com.aaron.cloud.identity.tenant.TenantShellAdminApplicationService.ShellPutBody;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 当前工作区租户：管理端壳（LOGO / 标题 / 页脚）、对话侧模型/记忆/联网等运行参数与出站韧性 JSON；与 {@code /admin/tenant-runtime-settings} 列表分离。
 */
@RestController
@RequiredArgsConstructor
public class AdminTenantShellConfigRestController extends ApiV1ControllerBases.AdminTenantShellConfig {

    private final TenantShellAdminApplicationService tenantShellAdminApplicationService;

    @GetMapping
    public TenantShellAdminApplicationService.ShellConfigResponse get() {
        return tenantShellAdminApplicationService.load(TenantContextHolder.require().getTenantId());
    }

    @PutMapping
    public TenantShellAdminApplicationService.ShellConfigResponse put(@RequestBody ShellPutBody body) {
        return tenantShellAdminApplicationService.save(TenantContextHolder.require().getTenantId(), body);
    }

    @PutMapping("/branding")
    public TenantShellAdminApplicationService.ShellConfigResponse putBranding(@RequestBody ShellBrandingPutBody body) {
        return tenantShellAdminApplicationService.saveBranding(
                TenantContextHolder.require().getTenantId(), body);
    }

    @PutMapping("/outbound")
    public TenantShellAdminApplicationService.ShellConfigResponse putOutbound(@RequestBody ShellOutboundPutBody body) {
        return tenantShellAdminApplicationService.saveOutbound(
                TenantContextHolder.require().getTenantId(), body);
    }

    @PutMapping("/model-calling-runtime")
    public TenantShellAdminApplicationService.ShellConfigResponse putModelCallingRuntime(
            @RequestBody ShellModelCallingPutBody body) {
        return tenantShellAdminApplicationService.saveModelCallingRuntime(
                TenantContextHolder.require().getTenantId(), body);
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TenantShellAdminApplicationService.LogoUploadResponse uploadLogo(@RequestPart("file") MultipartFile file)
            throws Exception {
        return tenantShellAdminApplicationService.uploadBrandLogo(file);
    }
}
