package com.aaron.cloud.common.message;

import com.aaron.cloud.common.api.enums.message.MessageDeliveryStatus;
import com.aaron.cloud.common.api.enums.message.MessageSceneCode;
import com.aaron.cloud.common.message.entity.MsgDeliveryLog;
import com.aaron.cloud.common.message.mapper.MsgDeliveryLogMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MsgDeliveryLogRepository {

    private final MsgDeliveryLogMapper mapper;

    public Optional<MsgDeliveryLog> findById(long id, long tenantId) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<MsgDeliveryLog>lambdaQuery()
                                .eq(MsgDeliveryLog::getId, id)
                                .eq(MsgDeliveryLog::getTenantId, tenantId)));
    }

    public Optional<MsgDeliveryLog> findByIdempotency(long tenantId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<MsgDeliveryLog>lambdaQuery()
                                .eq(MsgDeliveryLog::getTenantId, tenantId)
                                .eq(MsgDeliveryLog::getIdempotencyKey, idempotencyKey.trim())
                                .last("LIMIT 1")));
    }

    public Page<MsgDeliveryLog> pageByTenant(
            long tenantId,
            long pageNo,
            long pageSize,
            MessageSceneCode scene,
            MessageDeliveryStatus status,
            String recipientLike,
            LocalDateTime from,
            LocalDateTime to) {
        var q =
                Wrappers.<MsgDeliveryLog>lambdaQuery()
                        .eq(MsgDeliveryLog::getTenantId, tenantId)
                        .orderByDesc(MsgDeliveryLog::getCreatedAt);
        if (scene != null) {
            q.eq(MsgDeliveryLog::getSceneCode, scene);
        }
        if (status != null) {
            q.eq(MsgDeliveryLog::getStatus, status);
        }
        if (recipientLike != null && !recipientLike.isBlank()) {
            q.like(MsgDeliveryLog::getRecipient, recipientLike.trim());
        }
        if (from != null) {
            q.ge(MsgDeliveryLog::getCreatedAt, from);
        }
        if (to != null) {
            q.le(MsgDeliveryLog::getCreatedAt, to);
        }
        return mapper.selectPage(Page.of(pageNo, pageSize), q);
    }

    public int insert(MsgDeliveryLog row) {
        return mapper.insert(row);
    }

    public int updateById(MsgDeliveryLog row, long tenantId) {
        return mapper.update(
                row,
                Wrappers.<MsgDeliveryLog>lambdaUpdate()
                        .eq(MsgDeliveryLog::getId, row.getId())
                        .eq(MsgDeliveryLog::getTenantId, tenantId));
    }
}
