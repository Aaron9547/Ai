package com.aaron.cloud.chat;

import com.aaron.cloud.chat.dto.ChatSensitiveTermAdminDtos;
import com.aaron.cloud.chat.dto.ChatSensitiveTermAdminDtos.SensitiveTermImportResult;
import com.aaron.cloud.chat.dto.ChatSensitiveTermAdminDtos.SensitiveTermRow;
import com.aaron.cloud.common.api.enums.GuardrailSensitivePoolType;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.guardrail.GuardrailSensitiveTermRepository;
import com.aaron.cloud.common.guardrail.entity.GuardrailSensitiveTerm;
import com.aaron.cloud.common.security.AdminQueryTenantSupport;
import com.aaron.cloud.common.tenant.SysTenantRepository;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 管理端敏感词：平台强制池（{@code pool_type=PLATFORM, tenant_id=0}）仅创始人可增删改与导入；租户池按数据租户隔离（创始人可指定目标租户）。
 */
@Service
@RequiredArgsConstructor
public class GuardrailSensitiveTermAdminService {

    private static final int IMPORT_MAX_LINES = 500;
    private static final int WORD_MAX_LEN = 190;

    private final GuardrailSensitiveTermRepository sensitiveTermRepository;
    private final SysTenantRepository sysTenantRepository;

    public Page<SensitiveTermRow> pagePlatform(long pageNo, long pageSize, String q) {
        Page<GuardrailSensitiveTerm> src =
                sensitiveTermRepository.pagePlatformTerms(pageNo, pageSize, normalizeQ(q));
        return mapPage(src);
    }

    public Page<SensitiveTermRow> pageTenant(long pageNo, long pageSize, String q, Long filterTenantId) {
        long dataTenant = AdminQueryTenantSupport.resolveSensitiveTermsDataTenantId(filterTenantId);
        Page<GuardrailSensitiveTerm> src =
                sensitiveTermRepository.pageTenantTerms(dataTenant, pageNo, pageSize, normalizeQ(q));
        return mapPage(src);
    }

    private Page<SensitiveTermRow> mapPage(Page<GuardrailSensitiveTerm> src) {
        List<GuardrailSensitiveTerm> recs = src.getRecords();
        Set<Long> tenantIds =
                recs.stream()
                        .map(GuardrailSensitiveTerm::getTenantId)
                        .filter(Objects::nonNull)
                        .filter(tid -> tid > 0)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> codeById = sysTenantRepository.mapTenantCodeByIds(tenantIds);
        Page<SensitiveTermRow> out = new Page<>(src.getCurrent(), src.getSize(), src.getTotal());
        out.setRecords(recs.stream().map(e -> toRow(e, codeById)).toList());
        return out;
    }

    private static String normalizeQ(String q) {
        if (q == null) {
            return null;
        }
        String t = q.strip();
        return t.isEmpty() ? null : t;
    }

    public void add(ChatSensitiveTermAdminDtos.SensitiveTermAddBody body) {
        var snap = TenantContextHolder.require();
        long dataTenantId = resolveDataTenantForWrite(body.getPool(), body.getTargetTenantId(), snap);
        assertPlatformWriteAllowed(body.getPool(), snap);
        long rowTenantId = resolveRowTenantId(body.getPool(), dataTenantId);
        String word = normalizeWord(body.getWord());
        if (word.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "词不能为空");
        }
        if (sensitiveTermRepository.exists(body.getPool(), rowTenantId, word)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该词已存在");
        }
        var row = new GuardrailSensitiveTerm();
        row.setPoolType(body.getPool());
        row.setTenantId(rowTenantId);
        row.setWord(word);
        sensitiveTermRepository.insert(row);
    }

    public SensitiveTermImportResult importBatch(ChatSensitiveTermAdminDtos.SensitiveTermImportBody body) {
        var snap = TenantContextHolder.require();
        long dataTenantId = resolveDataTenantForWrite(body.getPool(), body.getTargetTenantId(), snap);
        assertPlatformWriteAllowed(body.getPool(), snap);
        long rowTenantId = resolveRowTenantId(body.getPool(), dataTenantId);
        Set<String> seen = new LinkedHashSet<>();
        List<String> lines = splitImportLines(body.getText());
        if (lines.size() > IMPORT_MAX_LINES) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "单次最多导入 " + IMPORT_MAX_LINES + " 条有效词");
        }
        int inserted = 0;
        int skippedDup = 0;
        int skippedInv = 0;
        for (String raw : lines) {
            String w = normalizeWord(raw);
            if (w.isEmpty()) {
                skippedInv++;
                continue;
            }
            if (!seen.add(w)) {
                skippedDup++;
                continue;
            }
            if (sensitiveTermRepository.exists(body.getPool(), rowTenantId, w)) {
                skippedDup++;
                continue;
            }
            var row = new GuardrailSensitiveTerm();
            row.setPoolType(body.getPool());
            row.setTenantId(rowTenantId);
            row.setWord(w);
            sensitiveTermRepository.insert(row);
            inserted++;
        }
        return new SensitiveTermImportResult(inserted, skippedDup, skippedInv);
    }

    public void delete(long id) {
        var snap = TenantContextHolder.require();
        long jwtTenantId = snap.getTenantId();
        GuardrailSensitiveTerm row =
                sensitiveTermRepository
                        .findById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在"));
        if (row.getPoolType() == GuardrailSensitivePoolType.PLATFORM) {
            if (snap.getMemberRole() == null || !snap.getMemberRole().isFounder()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅创始人可删除平台强制词");
            }
            if (row.getTenantId() != 0L) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据异常");
            }
        } else {
            boolean founder = snap.getMemberRole() != null && snap.getMemberRole().isFounder();
            if (!founder && row.getTenantId() != jwtTenantId) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权删除其他租户的敏感词");
            }
        }
        sensitiveTermRepository.deleteById(id);
    }

    private static long resolveDataTenantForWrite(
            GuardrailSensitivePoolType pool, Long targetTenantId, TenantContextHolder.TenantSnapshot snap) {
        if (pool == GuardrailSensitivePoolType.PLATFORM) {
            return snap.getTenantId();
        }
        return AdminQueryTenantSupport.resolveSensitiveTermsDataTenantId(targetTenantId);
    }

    private static void assertPlatformWriteAllowed(GuardrailSensitivePoolType pool, TenantContextHolder.TenantSnapshot snap) {
        if (pool == GuardrailSensitivePoolType.PLATFORM) {
            if (snap.getMemberRole() == null || !snap.getMemberRole().isFounder()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅创始人可维护平台强制默认词库");
            }
        }
    }

    private static long resolveRowTenantId(GuardrailSensitivePoolType pool, long dataTenantId) {
        if (pool == GuardrailSensitivePoolType.PLATFORM) {
            return 0L;
        }
        return dataTenantId;
    }

    private static SensitiveTermRow toRow(GuardrailSensitiveTerm e, Map<Long, String> tenantCodeById) {
        String code = null;
        if (e.getPoolType() == GuardrailSensitivePoolType.TENANT) {
            Long tid = e.getTenantId();
            if (tid != null && tid > 0) {
                code = tenantCodeById.get(tid);
            }
        }
        return new SensitiveTermRow(e.getId(), e.getPoolType(), code, e.getWord(), e.getCreatedAt());
    }

    private static String normalizeWord(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.strip();
        if (t.length() > WORD_MAX_LEN) {
            t = t.substring(0, WORD_MAX_LEN);
        }
        return t;
    }

    private static List<String> splitImportLines(String text) {
        String[] parts = text.replace("\r\n", "\n").replace('\r', '\n').split("[\n,，、;；]+");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                out.add(p.strip());
            }
        }
        return out;
    }
}
