package com.aaron.cloud.common.message;

import com.aaron.cloud.common.api.enums.message.MessageChannelStatus;
import com.aaron.cloud.common.message.entity.MsgChannel;
import com.aaron.cloud.common.message.mapper.MsgChannelMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MsgChannelRepository {

    private final MsgChannelMapper mapper;

    public List<MsgChannel> listByTenant(long tenantId) {
        return mapper.selectList(
                Wrappers.<MsgChannel>lambdaQuery()
                        .eq(MsgChannel::getTenantId, tenantId)
                        .orderByAsc(MsgChannel::getChannelCode));
    }

    public Page<MsgChannel> pageByTenant(long tenantId, long pageNo, long pageSize) {
        return mapper.selectPage(
                Page.of(pageNo, pageSize),
                Wrappers.<MsgChannel>lambdaQuery()
                        .eq(MsgChannel::getTenantId, tenantId)
                        .orderByDesc(MsgChannel::getUpdatedAt));
    }

    public Optional<MsgChannel> findById(long id, long tenantId) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<MsgChannel>lambdaQuery()
                                .eq(MsgChannel::getId, id)
                                .eq(MsgChannel::getTenantId, tenantId)));
    }

    public Optional<MsgChannel> findByCode(long tenantId, String channelCode) {
        if (channelCode == null || channelCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<MsgChannel>lambdaQuery()
                                .eq(MsgChannel::getTenantId, tenantId)
                                .eq(MsgChannel::getChannelCode, channelCode.trim())));
    }

    public boolean existsCode(long tenantId, String channelCode, Long excludeId) {
        var q =
                Wrappers.<MsgChannel>lambdaQuery()
                        .eq(MsgChannel::getTenantId, tenantId)
                        .eq(MsgChannel::getChannelCode, channelCode.trim());
        if (excludeId != null) {
            q.ne(MsgChannel::getId, excludeId);
        }
        return mapper.selectCount(q) > 0;
    }

    public int insert(MsgChannel row) {
        return mapper.insert(row);
    }

    public int updateById(MsgChannel row, long tenantId) {
        return mapper.update(
                row,
                Wrappers.<MsgChannel>lambdaUpdate()
                        .eq(MsgChannel::getId, row.getId())
                        .eq(MsgChannel::getTenantId, tenantId));
    }

    public int deleteById(long id, long tenantId) {
        return mapper.delete(
                Wrappers.<MsgChannel>lambdaQuery()
                        .eq(MsgChannel::getId, id)
                        .eq(MsgChannel::getTenantId, tenantId));
    }

    public long countActiveByTenant(long tenantId) {
        return mapper.selectCount(
                Wrappers.<MsgChannel>lambdaQuery()
                        .eq(MsgChannel::getTenantId, tenantId)
                        .eq(MsgChannel::getStatus, MessageChannelStatus.ACTIVE));
    }
}
