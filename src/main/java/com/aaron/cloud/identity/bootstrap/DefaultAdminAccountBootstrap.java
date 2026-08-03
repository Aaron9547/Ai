package com.aaron.cloud.identity.bootstrap;

import com.aaron.cloud.common.api.enums.tenant.TenantMemberRole;
import com.aaron.cloud.common.api.enums.identity.UserAccountStatus;
import com.aaron.cloud.common.api.enums.identity.UserRegistrationChannel;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.security.SysTenantMemberRepository;
import com.aaron.cloud.common.security.entity.SecUserAccount;
import com.aaron.cloud.common.security.entity.SysTenantMember;
import com.aaron.cloud.identity.account.UserAccountProvisioningService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultAdminAccountBootstrap implements ApplicationRunner {

    private static final String ADMIN_USER = "admin";

    private final SecUserAccountRepository userAccountRepository;
    private final SysTenantMemberRepository tenantMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAccountProvisioningService userAccountProvisioningService;

    @Value("${ai.tenant.default-id:1}")
    private long defaultTenantId;

    @Override
    public void run(ApplicationArguments args) {
        var existing = userAccountRepository.findByLoginName(ADMIN_USER);
        if (existing.isEmpty()) {
            var u = new SecUserAccount();
            u.setLoginName(ADMIN_USER);
            u.setDisplayName("Administrator");
            u.setPasswordHash(passwordEncoder.encode("admin"));
            u.setStatus(UserAccountStatus.ACTIVE);
            u.setRegistrationChannel(UserRegistrationChannel.ADMIN);
            userAccountProvisioningService.insert(u);
            var m = new SysTenantMember();
            m.setTenantId(defaultTenantId);
            m.setUserId(u.getId());
            m.setRoleCode(TenantMemberRole.FOUNDER);
            m.setStatus(UserAccountStatus.ACTIVE);
            tenantMemberRepository.insert(m);
            log.warn("已创建默认管理员账号 {} / 密码 admin（生产环境请立即修改并改用 jwt-local/oauth2-resource）", ADMIN_USER);
            return;
        }
        repairAdminMembershipIfNeeded(existing.get());
    }

    /**
     * 若库中已存在 {@code admin} 账号（手工导入等），首次引导不会执行；此处补齐常见疏漏，避免管理端登录 403：
     *
     * <ul>
     *   <li>默认租户下无成员行 → 插入 FOUNDER + ACTIVE
     *   <li>仅有禁用成员行 → 激活；若角色为纯 MEMBER → 升为 OWNER（否则无法进控制台）
     * </ul>
     */
    private void repairAdminMembershipIfNeeded(SecUserAccount u) {
        if (u.getStatus() != UserAccountStatus.ACTIVE) {
            return;
        }
        List<SysTenantMember> all = tenantMemberRepository.listByUserId(u.getId());
        boolean hasActiveElevated =
                all.stream()
                        .anyMatch(
                                m ->
                                        m.getStatus() == UserAccountStatus.ACTIVE
                                                && m.getRoleCode() != TenantMemberRole.MEMBER);
        if (hasActiveElevated) {
            return;
        }
        boolean hasActiveMemberOnly =
                all.stream()
                        .anyMatch(
                                m ->
                                        m.getStatus() == UserAccountStatus.ACTIVE
                                                && m.getRoleCode() == TenantMemberRole.MEMBER);
        if (hasActiveMemberOnly) {
            SysTenantMember m =
                    all.stream()
                            .filter(x -> x.getStatus() == UserAccountStatus.ACTIVE)
                            .findFirst()
                            .orElse(null);
            if (m != null) {
                m.setRoleCode(TenantMemberRole.OWNER);
                tenantMemberRepository.updateById(m);
                log.warn(
                        "已将内置账号 {} 在租户 id={} 的成员角色由 MEMBER 升为 OWNER（纯成员无法访问管理端 API）",
                        ADMIN_USER,
                        m.getTenantId());
            }
            return;
        }

        var opt = tenantMemberRepository.find(defaultTenantId, u.getId());
        if (opt.isEmpty()) {
            var m = new SysTenantMember();
            m.setTenantId(defaultTenantId);
            m.setUserId(u.getId());
            m.setRoleCode(TenantMemberRole.FOUNDER);
            m.setStatus(UserAccountStatus.ACTIVE);
            tenantMemberRepository.insert(m);
            log.warn(
                    "已为内置账号 {} 补充默认租户 id={} 的 FOUNDER 成员关系（此前无可用成员行，管理端登录会 403）",
                    ADMIN_USER,
                    defaultTenantId);
            return;
        }
        SysTenantMember m = opt.get();
        m.setStatus(UserAccountStatus.ACTIVE);
        if (m.getRoleCode() == TenantMemberRole.MEMBER) {
            m.setRoleCode(TenantMemberRole.OWNER);
            log.warn(
                    "已将 {} 在默认租户 id={} 的角色由 MEMBER 升为 OWNER，并已激活成员关系",
                    ADMIN_USER,
                    defaultTenantId);
        } else {
            log.warn("已激活 {} 在默认租户 id={} 的成员关系（status 置为 ACTIVE）", ADMIN_USER, defaultTenantId);
        }
        tenantMemberRepository.updateById(m);
    }
}
