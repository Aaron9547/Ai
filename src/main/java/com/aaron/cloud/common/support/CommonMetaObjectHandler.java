package com.aaron.cloud.common.support;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

@Component
public class CommonMetaObjectHandler implements MetaObjectHandler {

    /**
     * DATETIME 列按业务约定写入东八区墙钟（与 {@code spring.jackson.time-zone} 及运营端展示一致）；非 UTC 瞬时语义。
     */
    private static final ZoneId DB_WALL_CLOCK = ZoneId.of("Asia/Shanghai");

    @Override
    public void insertFill(MetaObject metaObject) {
        var now = LocalDateTime.now(DB_WALL_CLOCK);
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now(DB_WALL_CLOCK));
    }
}
