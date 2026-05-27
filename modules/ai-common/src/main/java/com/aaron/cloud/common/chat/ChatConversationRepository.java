package com.aaron.cloud.common.chat;

import com.aaron.cloud.common.api.enums.chat.ConversationRecordStatus;
import com.aaron.cloud.common.chat.entity.ChatConversation;
import com.aaron.cloud.common.time.BeijingTime;
import com.aaron.cloud.common.chat.mapper.ChatConversationMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
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

    public Optional<ChatConversation> findByPublicId(long tenantId, String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                mapper.selectOne(
                        Wrappers.<ChatConversation>lambdaQuery()
                                .eq(ChatConversation::getTenantId, tenantId)
                                .eq(ChatConversation::getPublicId, publicId.trim())));
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
                q.eq(ChatConversation::getStatus, ConversationRecordStatus.ACTIVE)
                        .orderByDesc(ChatConversation::getUpdatedAt)
                        .last("LIMIT " + limit));
    }

    public int archive(long id, long tenantId) {
        return mapper.update(
                null,
                new LambdaUpdateWrapper<ChatConversation>()
                        .eq(ChatConversation::getId, id)
                        .eq(ChatConversation::getTenantId, tenantId)
                        .set(ChatConversation::getStatus, ConversationRecordStatus.ARCHIVED)
                        .set(ChatConversation::getUpdatedAt, BeijingTime.nowLocal()));
    }

    public void touchUpdatedAt(long id, long tenantId) {
        mapper.update(
                null,
                new LambdaUpdateWrapper<ChatConversation>()
                        .eq(ChatConversation::getId, id)
                        .eq(ChatConversation::getTenantId, tenantId)
                        .set(ChatConversation::getUpdatedAt, BeijingTime.nowLocal()));
    }

    public int insert(ChatConversation row) {
        if (row.getPublicId() == null || row.getPublicId().isBlank()) {
            row.setPublicId(ChatConversationPublicIds.generate());
        }
        return mapper.insert(row);
    }

    public long countByTenant(long tenantId) {
        return mapper.selectCount(
                Wrappers.<ChatConversation>lambdaQuery().eq(ChatConversation::getTenantId, tenantId));
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
                        .set(ChatConversation::getUpdatedAt, BeijingTime.nowLocal()));
    }

    /**
     * 注册归并：将该租户下访客设备会话挂到用户，并清空 {@code device_id}（后续仅按 {@code user_id} 访问）。
     */
    public int attachGuestConversationsToUser(long tenantId, String deviceId, long userId) {
        if (deviceId == null || deviceId.isBlank()) {
            return 0;
        }
        return mapper.update(
                null,
                new LambdaUpdateWrapper<ChatConversation>()
                        .eq(ChatConversation::getTenantId, tenantId)
                        .eq(ChatConversation::getDeviceId, deviceId.trim())
                        .isNull(ChatConversation::getUserId)
                        .set(ChatConversation::getUserId, userId)
                        .set(ChatConversation::getDeviceId, null)
                        .set(ChatConversation::getUpdatedAt, BeijingTime.nowLocal()));
    }

    /**
     * 已登录用户访问仍挂在访客设备上的单条会话时，幂等归并到当前用户（与 {@link #attachGuestConversationsToUser} 条件一致）。
     */
    public boolean attachGuestConversationToUser(
            long tenantId, long conversationId, String deviceId, long userId) {
        if (deviceId == null || deviceId.isBlank()) {
            return false;
        }
        int updated =
                mapper.update(
                        null,
                        new LambdaUpdateWrapper<ChatConversation>()
                                .eq(ChatConversation::getId, conversationId)
                                .eq(ChatConversation::getTenantId, tenantId)
                                .eq(ChatConversation::getDeviceId, deviceId.trim())
                                .isNull(ChatConversation::getUserId)
                                .set(ChatConversation::getUserId, userId)
                                .set(ChatConversation::getDeviceId, null)
                                .set(ChatConversation::getUpdatedAt, BeijingTime.nowLocal()));
        return updated > 0;
    }
}
