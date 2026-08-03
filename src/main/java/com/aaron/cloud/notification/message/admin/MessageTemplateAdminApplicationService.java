package com.aaron.cloud.notification.message.admin;

import com.aaron.cloud.common.api.enums.message.MessageTemplateStatus;
import com.aaron.cloud.common.message.MsgChannelRepository;
import com.aaron.cloud.common.message.MsgTemplateRepository;
import com.aaron.cloud.common.message.entity.MsgTemplate;
import com.aaron.cloud.common.security.AdminQueryTenantSupport;
import com.aaron.cloud.notification.dto.MessageAdminDtos.TemplateCreateBody;
import com.aaron.cloud.notification.dto.MessageAdminDtos.TemplateRow;
import com.aaron.cloud.notification.dto.MessageAdminDtos.TemplateUpdateBody;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MessageTemplateAdminApplicationService {

    private final MsgTemplateRepository templateRepository;
    private final MsgChannelRepository channelRepository;

    public List<TemplateRow> list() {
        long tid = AdminQueryTenantSupport.resolveIntentAdminDataTenantId(null);
        return templateRepository.listByTenant(tid).stream().map(this::toRow).toList();
    }

    public TemplateRow create(TemplateCreateBody body) {
        long tid = AdminQueryTenantSupport.resolveIntentAdminDataTenantId(null);
        channelRepository
                .findById(body.channelId(), tid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "通道不存在"));
        String locale = body.locale() == null || body.locale().isBlank() ? "zh-CN" : body.locale().trim();
        if (templateRepository.existsScene(tid, body.sceneCode(), locale, null)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同场景与语言模板已存在");
        }
        MsgTemplate row = new MsgTemplate();
        row.setTenantId(tid);
        row.setSceneCode(body.sceneCode());
        row.setChannelId(body.channelId());
        row.setSubjectTemplate(body.subjectTemplate());
        row.setBodyTemplate(body.bodyTemplate().trim());
        row.setLocale(locale);
        row.setStatus(MessageAdminEnumSupport.parseTemplateStatus(body.status()));
        templateRepository.insert(row);
        return toRow(templateRepository.findById(row.getId(), tid).orElse(row));
    }

    public TemplateRow update(long id, TemplateUpdateBody body) {
        long tid = AdminQueryTenantSupport.resolveIntentAdminDataTenantId(null);
        MsgTemplate row =
                templateRepository
                        .findById(id, tid)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在"));
        if (body.channelId() != null) {
            channelRepository
                    .findById(body.channelId(), tid)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "通道不存在"));
            row.setChannelId(body.channelId());
        }
        if (body.subjectTemplate() != null) {
            row.setSubjectTemplate(body.subjectTemplate());
        }
        if (body.bodyTemplate() != null) {
            row.setBodyTemplate(body.bodyTemplate().trim());
        }
        if (body.locale() != null && !body.locale().isBlank()) {
            String locale = body.locale().trim();
            if (templateRepository.existsScene(tid, row.getSceneCode(), locale, id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "同场景与语言模板已存在");
            }
            row.setLocale(locale);
        }
        if (body.status() != null) {
            row.setStatus(MessageAdminEnumSupport.parseTemplateStatus(body.status()));
        }
        templateRepository.updateById(row, tid);
        return toRow(templateRepository.findById(id, tid).orElse(row));
    }

    public void delete(long id) {
        long tid = AdminQueryTenantSupport.resolveIntentAdminDataTenantId(null);
        templateRepository
                .findById(id, tid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在"));
        templateRepository.deleteById(id, tid);
    }

    private TemplateRow toRow(MsgTemplate row) {
        return new TemplateRow(
                row.getId(),
                row.getSceneCode(),
                row.getChannelId(),
                row.getSubjectTemplate(),
                row.getBodyTemplate(),
                row.getLocale(),
                MessageAdminEnumSupport.templateStatusLabel(row.getStatus()),
                row.getUpdatedAt());
    }
}
