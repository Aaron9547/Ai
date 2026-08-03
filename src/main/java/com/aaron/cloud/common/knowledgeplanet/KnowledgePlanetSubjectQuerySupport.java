package com.aaron.cloud.common.knowledgeplanet;

import com.aaron.cloud.common.context.TenantSnapshot;
import com.aaron.cloud.common.knowledgeplanet.entity.TenUserKnowledgeNode;
import com.aaron.cloud.common.profile.ProfileSubjectKey;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 知识星球读写主体：已登录且携带 {@code X-Device-Id} 时，先将 {@code d:{deviceId}} 节点幂等归并到 {@code u:{userId}}，
 * 再按用户主体查询（与 {@link com.aaron.cloud.common.profile.ProfileDeviceMergeApplicationService} 登录归并互补，可自愈历史漏迁）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgePlanetSubjectQuerySupport {

    private final TenUserKnowledgeNodeRepository nodeRepository;

    /** 登录用户 + 设备码时，将仍挂在访客主体下的知识点迁到用户主体。 */
    public void mergeGuestNodesIfNeeded(TenantSnapshot snap) {
        if (snap == null || snap.getUserId() == null) {
            return;
        }
        String deviceId = snap.getDeviceId();
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        long tenantId = snap.getTenantId();
        String dKey = ProfileSubjectKey.deviceKey(deviceId);
        String uKey = ProfileSubjectKey.userKey(snap.getUserId());
        int moved = nodeRepository.reassignSubject(tenantId, dKey, uKey);
        if (moved > 0) {
            log.info(
                    "[知识星球] 访客节点已归并 tenantId={} userId={} deviceId={} nodes={}",
                    tenantId,
                    snap.getUserId(),
                    deviceId.trim(),
                    moved);
        }
    }

    public String requireQuerySubjectKey(TenantSnapshot snap) {
        mergeGuestNodesIfNeeded(snap);
        String sk = ProfileSubjectKey.fromSnapshot(snap);
        if (sk == null) {
            throw new IllegalStateException("subject_required");
        }
        return sk;
    }

    public List<TenUserKnowledgeNode> listRecent(TenantSnapshot snap, int limit) {
        mergeGuestNodesIfNeeded(snap);
        String sk = ProfileSubjectKey.fromSnapshot(snap);
        if (sk == null) {
            return List.of();
        }
        return nodeRepository.listRecent(snap.getTenantId(), sk, limit);
    }
}
