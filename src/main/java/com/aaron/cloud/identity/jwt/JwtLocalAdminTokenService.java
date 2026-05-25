package com.aaron.cloud.identity.jwt;

import com.aaron.cloud.common.api.enums.TenantMemberRole;
import com.aaron.cloud.common.context.LoginUser;
import com.aaron.cloud.common.security.entity.SecUserAccount;
import com.aaron.cloud.common.security.entity.SysTenantMember;
import com.aaron.cloud.common.tenant.SysTenantRepository;
import com.aaron.cloud.identity.rest.open.AuthLoginController.LoginResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtLocalAdminTokenService {

    private final SysTenantRepository tenantRepository;
    private final JwtEncoder jwtEncoder;
    private final ObjectMapper objectMapper;

    @Value("${ai.tenant.default-id:1}")
    private long defaultTenantId;

    public LoginResponse buildLoginResponse(SecUserAccount user, List<SysTenantMember> active, Long headerTid)
            throws Exception {
        boolean founder = active.stream().anyMatch(m -> m.getRoleCode() == TenantMemberRole.FOUNDER);
        boolean anyElevated = active.stream().anyMatch(m -> m.getRoleCode() != TenantMemberRole.MEMBER);

        ArrayNode tmsArr = objectMapper.createArrayNode();
        for (SysTenantMember m : active) {
            ObjectNode o = objectMapper.createObjectNode();
            o.put("tid", m.getTenantId());
            o.put("tmr", m.getRoleCode().name());
            tmsArr.add(o);
        }
        String tmsJson = objectMapper.writeValueAsString(tmsArr);

        long tid;
        TenantMemberRole tmr;
        if (founder) {
            tid =
                    active.stream()
                            .filter(m -> m.getRoleCode() == TenantMemberRole.FOUNDER)
                            .map(SysTenantMember::getTenantId)
                            .findFirst()
                            .orElse(defaultTenantId);
            tmr = TenantMemberRole.FOUNDER;
        } else if (anyElevated) {
            SysTenantMember primary = pickElevatedPrimary(active, headerTid);
            tid = primary.getTenantId();
            tmr = primary.getRoleCode();
        } else {
            SysTenantMember primary = pickMemberPrimary(active, headerTid);
            tid = primary.getTenantId();
            tmr = TenantMemberRole.MEMBER;
        }

        return encode(user, active, tid, tmr, tmsJson);
    }

    /** 签发明确租户与角色的管理端令牌（须经调用方校验 {@code memberships}）。 */
    public LoginResponse mintExplicitContext(LoginUser user, List<SysTenantMember> active, long tid, TenantMemberRole tmr)
            throws Exception {
        ArrayNode tmsArr = objectMapper.createArrayNode();
        for (SysTenantMember m : active) {
            ObjectNode o = objectMapper.createObjectNode();
            o.put("tid", m.getTenantId());
            o.put("tmr", m.getRoleCode().name());
            tmsArr.add(o);
        }
        String tmsJson = objectMapper.writeValueAsString(tmsArr);
        return encodeLoginUser(user, active, tid, tmr, tmsJson);
    }

    private LoginResponse encode(SecUserAccount user, List<SysTenantMember> active, long tid, TenantMemberRole tmr, String tmsJson)
            throws Exception {
        LoginUser loginUser = LoginUser.fromAccount(user);
        if (loginUser == null) {
            throw new IllegalArgumentException("user required");
        }
        return encodeLoginUser(loginUser, active, tid, tmr, tmsJson);
    }

    private LoginResponse encodeLoginUser(
            LoginUser user, List<SysTenantMember> active, long tid, TenantMemberRole tmr, String tmsJson)
            throws Exception {
        List<LoginResponse.MembershipEntry> membershipDtos = new ArrayList<>();
        for (SysTenantMember m : active) {
            var tenantOpt = tenantRepository.findById(m.getTenantId());
            String code = tenantOpt.map(t -> t.getCode()).orElse("");
            String name = tenantOpt.map(t -> t.getName()).filter(n -> n != null && !n.isBlank()).orElse(code);
            membershipDtos.add(
                    new LoginResponse.MembershipEntry(m.getTenantId(), code, m.getRoleCode(), name));
        }

        Instant now = Instant.now();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer("ai-local")
                        .issuedAt(now)
                        .expiresAt(now.plus(8, ChronoUnit.HOURS))
                        .subject(user.getLoginName())
                        .claim("tid", tid)
                        .claim("uid", user.getId())
                        .claim("jseq", user.jwtSeqOrZero())
                        .claim("tmr", tmr.name())
                        .claim("tms", tmsJson)
                        .build();
        JwsHeader jws = JwsHeader.with(MacAlgorithm.HS256).build();
        var token = jwtEncoder.encode(JwtEncoderParameters.from(jws, claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", 8 * 3600, List.copyOf(membershipDtos));
    }

    private static SysTenantMember pickElevatedPrimary(List<SysTenantMember> active, Long headerTid) {
        if (headerTid != null) {
            return active.stream()
                    .filter(m -> m.getTenantId().equals(headerTid))
                    .filter(m -> m.getRoleCode() != TenantMemberRole.MEMBER)
                    .findFirst()
                    .orElseGet(
                            () ->
                                    active.stream()
                                            .filter(m -> m.getRoleCode() != TenantMemberRole.MEMBER)
                                            .findFirst()
                                            .orElseThrow());
        }
        return active.stream()
                .filter(m -> m.getRoleCode() != TenantMemberRole.MEMBER)
                .findFirst()
                .orElseThrow();
    }

    private static SysTenantMember pickMemberPrimary(List<SysTenantMember> active, Long headerTid) {
        if (headerTid != null) {
            return active.stream()
                    .filter(m -> m.getTenantId().equals(headerTid))
                    .findFirst()
                    .orElse(active.get(0));
        }
        return active.get(0);
    }
}
