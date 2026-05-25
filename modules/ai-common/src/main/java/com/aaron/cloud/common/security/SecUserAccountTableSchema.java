package com.aaron.cloud.common.security;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 检测 {@code sec_user_account} 是否已执行含 {@code jwt_seq} 的迁移；未迁移时避免 SELECT 带出该列导致登录/鉴权 SQL 失败。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SecUserAccountTableSchema {

    private final DataSource dataSource;

    private volatile Boolean jwtSeqColumnPresent;

    public boolean hasJwtSeqColumn() {
        Boolean cached = jwtSeqColumnPresent;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (jwtSeqColumnPresent != null) {
                return jwtSeqColumnPresent;
            }
            boolean present = probeJwtSeq();
            jwtSeqColumnPresent = present;
            if (!present) {
                log.warn(
                        "未检测到 sec_user_account.jwt_seq 列，已启用兼容模式（登录可用）。请对数据库执行 db/mysql/schema_v1.sql 中与 jwt_seq 相关的 DDL 或补齐列后重启，以启用完整 JWT 代际踢下线。");
            }
            return present;
        }
    }

    private boolean probeJwtSeq() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData md = conn.getMetaData();
            String catalog = conn.getCatalog();
            try (ResultSet rs = md.getColumns(catalog, null, "sec_user_account", "jwt_seq")) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.warn("探测 jwt_seq 列失败，按未迁移处理: {}", e.toString());
            return false;
        }
    }
}
