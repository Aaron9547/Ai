package com.aaron.cloud.notification.message.admin;

import com.aaron.cloud.common.api.enums.message.MessageChannelStatus;
import com.aaron.cloud.common.message.MsgChannelRepository;
import com.aaron.cloud.common.message.entity.MsgChannel;
import com.aaron.cloud.common.security.AdminQueryTenantSupport;
import com.aaron.cloud.notification.dto.MessageAdminDtos;
import com.aaron.cloud.notification.dto.MessageAdminDtos.ChannelCreateBody;
import com.aaron.cloud.notification.dto.MessageAdminDtos.ChannelRow;
import com.aaron.cloud.notification.dto.MessageAdminDtos.ChannelTestBody;
import com.aaron.cloud.notification.dto.MessageAdminDtos.ChannelUpdateBody;
import com.aaron.cloud.notification.message.MessageSceneReadinessService;
import com.aaron.cloud.notification.message.MessageChannelRouter;
import com.aaron.cloud.notification.message.MessageTemplateSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MessageChannelAdminApplicationService {

    private static final String PASSWORD_MASK = "********";

    private final MsgChannelRepository channelRepository;
    private final MessageSceneReadinessService readinessService;
    private final MessageChannelRouter channelRouter;
    private final ObjectMapper objectMapper;

    public List<ChannelRow> list() {
        long tid = AdminQueryTenantSupport.resolveIntentAdminDataTenantId(null);
        return channelRepository.listByTenant(tid).stream().map(this::toRow).toList();
    }

    public ChannelRow create(ChannelCreateBody body) {
        long tid = AdminQueryTenantSupport.resolveIntentAdminDataTenantId(null);
        validateConfigJson(body.configJson());
        String code = body.channelCode().trim();
        if (channelRepository.existsCode(tid, code, null)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "通道编码已存在");
        }
        MsgChannel row = new MsgChannel();
        row.setTenantId(tid);
        row.setChannelCode(code);
        row.setChannelType(body.channelType());
        row.setName(body.name().trim());
        row.setConfigJson(body.configJson().trim());
        row.setSecretJson(resolveSecretForCreate(body.secretJson()));
        row.setStatus(MessageAdminEnumSupport.parseChannelStatus(body.status()));
        channelRepository.insert(row);
        return toRow(channelRepository.findById(row.getId(), tid).orElse(row));
    }

    public ChannelRow update(long id, ChannelUpdateBody body) {
        long tid = AdminQueryTenantSupport.resolveIntentAdminDataTenantId(null);
        MsgChannel row =
                channelRepository
                        .findById(id, tid)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "通道不存在"));
        if (body.name() != null) {
            row.setName(body.name().trim());
        }
        if (body.configJson() != null) {
            validateConfigJson(body.configJson());
            row.setConfigJson(body.configJson().trim());
        }
        if (body.secretJson() != null) {
            row.setSecretJson(resolveSecretForUpdate(body.secretJson(), row.getSecretJson()));
        }
        if (body.status() != null) {
            row.setStatus(MessageAdminEnumSupport.parseChannelStatus(body.status()));
        }
        channelRepository.updateById(row, tid);
        return toRow(channelRepository.findById(id, tid).orElse(row));
    }

    public void delete(long id) {
        long tid = AdminQueryTenantSupport.resolveIntentAdminDataTenantId(null);
        channelRepository
                .findById(id, tid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "通道不存在"));
        channelRepository.deleteById(id, tid);
    }

    public void testSend(long id, ChannelTestBody body) {
        long tid = AdminQueryTenantSupport.resolveIntentAdminDataTenantId(null);
        MsgChannel channel =
                channelRepository
                        .findById(id, tid)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "通道不存在"));
        if (!readinessService.isChannelReady(channel)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "通道未就绪");
        }
        Map<String, String> vars = body.templateVars() == null ? Map.of("code", "123456") : body.templateVars();
        String subject = MessageTemplateSupport.applyTemplate("测试消息 {code}", vars);
        String text = MessageTemplateSupport.applyTemplate("通道测试：{code}", vars);
        try {
            channelRouter.send(channel, body.recipient().trim(), subject, text, vars);
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, ex.getMessage() == null ? "发送失败" : ex.getMessage());
        }
    }

    private ChannelRow toRow(MsgChannel row) {
        return new ChannelRow(
                row.getId(),
                row.getChannelCode(),
                row.getChannelType(),
                row.getName(),
                row.getConfigJson(),
                isSecretConfigured(row.getSecretJson()),
                MessageAdminEnumSupport.channelStatusLabel(row.getStatus()),
                row.getUpdatedAt());
    }

    private static boolean isSecretConfigured(String secretJson) {
        return secretJson != null && !secretJson.isBlank() && !"{}".equals(secretJson.trim());
    }

    private void validateConfigJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "configJson 须为 JSON 对象");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "configJson 无效");
        }
    }

    private String resolveSecretForCreate(String incoming) {
        if (incoming == null || incoming.isBlank()) {
            return "{}";
        }
        if (PASSWORD_MASK.equals(incoming.trim())) {
            return "{}";
        }
        return incoming.trim();
    }

    private String resolveSecretForUpdate(String incoming, String stored) {
        if (incoming == null || incoming.isBlank() || PASSWORD_MASK.equals(incoming.trim())) {
            return stored == null ? "{}" : stored;
        }
        return incoming.trim();
    }
}
