package com.aaron.cloud.notification.rest.api;

import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.notification.dto.MessageAdminDtos;
import com.aaron.cloud.notification.message.admin.MessageChannelAdminApplicationService;
import com.aaron.cloud.notification.message.admin.MessageDeliveryLogAdminApplicationService;
import com.aaron.cloud.notification.message.admin.MessageTemplateAdminApplicationService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MessageChannelAdminRestController extends ApiV1ControllerBases.AdminMessageChannels {

    private final MessageChannelAdminApplicationService channelAdminService;

    @GetMapping
    public List<MessageAdminDtos.ChannelRow> list() {
        return channelAdminService.list();
    }

    @PostMapping
    public MessageAdminDtos.ChannelRow create(@Valid @RequestBody MessageAdminDtos.ChannelCreateBody body) {
        return channelAdminService.create(body);
    }

    @PutMapping("/{id}")
    public MessageAdminDtos.ChannelRow update(
            @PathVariable long id, @Valid @RequestBody MessageAdminDtos.ChannelUpdateBody body) {
        return channelAdminService.update(id, body);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        channelAdminService.delete(id);
    }

    @PostMapping("/{id}/test")
    public void test(@PathVariable long id, @Valid @RequestBody MessageAdminDtos.ChannelTestBody body) {
        channelAdminService.testSend(id, body);
    }
}

@RestController
@RequiredArgsConstructor
class MessageTemplateAdminRestController extends ApiV1ControllerBases.AdminMessageTemplates {

    private final MessageTemplateAdminApplicationService templateAdminService;

    @GetMapping
    public List<MessageAdminDtos.TemplateRow> list() {
        return templateAdminService.list();
    }

    @PostMapping
    public MessageAdminDtos.TemplateRow create(@Valid @RequestBody MessageAdminDtos.TemplateCreateBody body) {
        return templateAdminService.create(body);
    }

    @PutMapping("/{id}")
    public MessageAdminDtos.TemplateRow update(
            @PathVariable long id, @Valid @RequestBody MessageAdminDtos.TemplateUpdateBody body) {
        return templateAdminService.update(id, body);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        templateAdminService.delete(id);
    }
}

@RestController
@RequiredArgsConstructor
class MessageDeliveryLogAdminRestController extends ApiV1ControllerBases.AdminMessageDeliveryLogs {

    private final MessageDeliveryLogAdminApplicationService deliveryLogAdminService;

    @GetMapping
    public MessageAdminDtos.DeliveryLogPage page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String sceneCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String recipient,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return deliveryLogAdminService.page(page, pageSize, sceneCode, status, recipient, from, to);
    }

    @GetMapping("/{id}")
    public MessageAdminDtos.DeliveryLogRow get(@PathVariable long id) {
        return deliveryLogAdminService.get(id);
    }
}
