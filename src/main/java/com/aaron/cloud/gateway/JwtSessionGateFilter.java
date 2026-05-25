package com.aaron.cloud.gateway;

import com.aaron.cloud.common.api.enums.UserAccountStatus;
import com.aaron.cloud.common.context.LoginUserContextHolder;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.security.entity.SecUserAccount;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 校验 JWT 中的 {@code jseq} 与账号当前 {@code sec_user_account.jwt_seq} 一致；用于踢下线与封禁后立即使令牌失效。
 */
@RequiredArgsConstructor
public final class JwtSessionGateFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtSessionGateFilter.class);

    private final SecUserAccountRepository userAccountRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            chain.doFilter(request, response);
            return;
        }
        Jwt jwt = jwtAuth.getToken();
        Long uid = toLong(jwt.getClaim("uid"));
        if (uid == null) {
            chain.doFilter(request, response);
            return;
        }
        long tokenJseq = toLong(jwt.getClaim("jseq"), 0L);
        SecUserAccount user =
                userAccountRepository.findById(uid).orElse(null);
        if (user == null) {
            log.warn("jwt uid={} not found, rejecting", uid);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "session invalid");
            return;
        }
        if (user.getStatus() != UserAccountStatus.ACTIVE) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "account disabled");
            return;
        }
        long dbJseq = user.getJwtSeq() == null ? 0L : user.getJwtSeq();
        if (dbJseq > tokenJseq) {
            log.warn("jwt jseq stale uid={} tokenJseq={} dbJseq={}", uid, tokenJseq, dbJseq);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "session revoked");
            return;
        }
        LoginUserContextHolder.bindFromAccount(user);
        chain.doFilter(request, response);
    }

    private static Long toLong(Object claim) {
        if (claim == null) {
            return null;
        }
        if (claim instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(claim.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static long toLong(Object claim, long defaultVal) {
        Long v = toLong(claim);
        return v == null ? defaultVal : v;
    }
}
