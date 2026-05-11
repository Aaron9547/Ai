package com.aaron.cloud.identity.rest.api;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.security.SysTenantMemberRepository;
import com.aaron.cloud.identity.admin.UserAdminMenuApplicationService;
import com.aaron.cloud.identity.tenant.TenantMemberRoleApplicationService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 校验 {@code GET /api/v1/admin/users} 已注册（{@link com.aaron.cloud.common.web.rest.ApiV1ControllerBases.AdminUsers} 单段完整类级路径）。 */
@WebMvcTest(controllers = AdminUserRestController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminUserRestControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SecUserAccountRepository userAccountRepository;

    @MockitoBean
    private SysTenantMemberRepository tenantMemberRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserAdminMenuApplicationService userAdminMenuApplicationService;

    @MockitoBean
    private TenantMemberRoleApplicationService tenantMemberRoleApplicationService;

    @BeforeEach
    void tenantContext() {
        TenantContextHolder.set(
                TenantContextHolder.TenantSnapshot.builder().tenantId(1L).userId(1L).build());
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void listUsersAtApiV1PrefixedPath() throws Exception {
        when(tenantMemberRepository.pageByTenant(eq(1L), anyLong(), anyLong())).thenReturn(new Page<>());
        mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isOk());
    }
}
