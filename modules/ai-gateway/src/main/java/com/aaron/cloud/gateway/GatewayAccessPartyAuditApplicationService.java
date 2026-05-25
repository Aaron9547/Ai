package com.aaron.cloud.gateway;

import com.aaron.cloud.common.gateway.GwAccessPartyCallLogRepository;
import com.aaron.cloud.common.gateway.entity.GwAccessPartyCallLog;
import com.aaron.cloud.common.time.BeijingTime;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GatewayAccessPartyAuditApplicationService {

    private final GwAccessPartyCallLogRepository callLogRepository;

    public Page<GwAccessPartyCallLog> page(
            long tenantId,
            Long accessPartyId,
            Long endpointId,
            Integer httpStatus,
            LocalDateTime since,
            long pageNo,
            long pageSize) {
        return callLogRepository.pageByTenant(tenantId, accessPartyId, endpointId, httpStatus, since, pageNo, pageSize);
    }

    public GwAccessPartyCallLog get(long tenantId, long id) {
        GwAccessPartyCallLog row = callLogRepository.findById(id);
        if (row == null || !row.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("call log not found");
        }
        return row;
    }

    public Map<String, Object> summary(long tenantId, int days) {
        int d = Math.max(1, Math.min(days, 90));
        LocalDateTime since = BeijingTime.nowLocal().minusDays(d);
        long total = callLogRepository.countSince(tenantId, since);
        long success = callLogRepository.countSuccessSince(tenantId, since);
        Map<String, Object> m = new HashMap<>();
        m.put("totalCalls", total);
        m.put("successCalls", success);
        m.put("failureCalls", Math.max(0, total - success));
        m.put("successRate", total == 0 ? 1.0 : (success * 1.0 / total));
        m.put("sinceDays", d);
        return m;
    }
}
