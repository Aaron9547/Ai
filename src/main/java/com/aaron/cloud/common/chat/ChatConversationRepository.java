package com.aaron.cloud.common.chat;

import com.aaron.cloud.common.chat.entity.ChatConversation;
import com.aaron.cloud.common.chat.mapper.ChatConversationMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatConversationRepository {

    private final ChatConversationMapper mapper;

    public Optional<ChatConversation> findById(long id, long tenantId) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<ChatConversation>lambdaQuery()
                                .eq(ChatConversation::getId, id)
                                .eq(ChatConversation::getTenantId, tenantId)));
    }

    /** 管理端按主键加载（不按租户），调用方须再做租户/角色授权校验。 */
    public Optional<ChatConversation> findByIdForAdmin(long id) {
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<ChatConversation>lambdaQuery().eq(ChatConversation::getId, id)));
    }

    public List<ChatConversation> listByTenant(long tenantId, int limit) {
        return mapper.selectList(
                Wrappers.<ChatConversation>lambdaQuery()
                        .eq(ChatConversation::getTenantId, tenantId)
                        .orderByDesc(ChatConversation::getUpdatedAt)
                        .last("LIMIT " + limit));
    }

    public Page<ChatConversation> pageByTenant(long tenantId, long pageNo, long pageSize) {
        return mapper.selectPage(
                Page.of(pageNo, pageSize),
                Wrappers.<ChatConversation>lambdaQuery()
                        .eq(ChatConversation::getTenantId, tenantId)
                        .orderByDesc(ChatConversation::getUpdatedAt));
    }

    /** {@code filterTenantId == null} 时不按租户过滤（创始人全量列表）。 */
    public Page<ChatConversation> pageForAdmin(Long filterTenantIdOrNull, long pageNo, long pageSize) {
        var q = Wrappers.<ChatConversation>lambdaQuery().orderByDesc(ChatConversation::getUpdatedAt);
        if (filterTenantIdOrNull != null) {
            q.eq(ChatConversation::getTenantId, filterTenantIdOrNull);
        }
        return mapper.selectPage(Page.of(pageNo, pageSize), q);
    }

    /**
     * 已登录：仅本人 {@code user_id}；访客：仅 {@code user_id IS NULL} 且 {@code device_id} 匹配。
     */
    public List<ChatConversation> listForSubject(long tenantId, Long userId, String deviceId, int limit) {
        var q = Wrappers.<ChatConversation>lambdaQuery().eq(ChatConversation::getTenantId, tenantId);
        if (userId != null) {
            q.eq(ChatConversation::getUserId, userId);
        } else {
            if (deviceId == null || deviceId.isBlank()) {
                return List.of();
            }
            q.isNull(ChatConversation::getUserId).eq(ChatConversation::getDeviceId, deviceId);
        }
        return mapper.selectList(
                q.orderByDesc(ChatConversation::getUpdatedAt).last("LIMIT " + limit));
    }

    public void touchUpdatedAt(long id, long tenantId) {
        mapper.update(
                null,
                new LambdaUpdateWrapper<ChatConversation>()
                        .eq(ChatConversation::getId, id)
                        .eq(ChatConversation::getTenantId, tenantId)
                        .set(ChatConversation::getUpdatedAt, LocalDateTime.now(ZoneOffset.UTC)));
    }

    public int insert(ChatConversation row) {
        return mapper.insert(row);
    }

    public int updateById(ChatConversation row) {
        return mapper.updateById(row);
    }

    public int updateTitle(long id, long tenantId, String title) {
        if (title == null || title.isBlank()) {
            return 0;
        }
        return mapper.update(
                null,
                new LambdaUpdateWrapper<ChatConversation>()
                        .eq(ChatConversation::getId, id)
                        .eq(ChatConversation::getTenantId, tenantId)
                        .set(ChatConversation::getTitle, title.trim())
                        .set(ChatConversation::getUpdatedAt, LocalDateTime.now(ZoneOffset.UTC)));
    }
}
