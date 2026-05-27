package com.aaron.cloud.chat.rest.api;

import com.aaron.cloud.chat.dto.ChatStarterPromptDtos;
import com.aaron.cloud.chat.starter.ChatStarterPromptAdminApplicationService;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
public class ChatStarterPromptAdminRestController extends ApiV1ControllerBases.AdminChat {

    private final ChatStarterPromptAdminApplicationService adminService;

    @GetMapping("/starter-prompts")
    public ChatStarterPromptDtos.PromptPageResult list(
            @RequestParam String scene,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String source) {
        return adminService.pagePrompts(scene, source, page, size);
    }

    @PostMapping("/starter-prompts")
    public ChatStarterPromptDtos.PromptRow create(@Valid @RequestBody ChatStarterPromptDtos.PromptCreateBody body) {
        return adminService.create(body);
    }

    @PutMapping("/starter-prompts/{id}")
    public ChatStarterPromptDtos.PromptRow update(
            @PathVariable long id, @Valid @RequestBody ChatStarterPromptDtos.PromptUpdateBody body) {
        return adminService.update(id, body);
    }

    @DeleteMapping("/starter-prompts/{id}")
    public void delete(@PathVariable long id) {
        adminService.delete(id);
    }

    @GetMapping("/starter-prompts/{id}/web-grounding")
    public ChatStarterPromptDtos.WebGroundingDetailView getWebGroundingDetail(@PathVariable long id) {
        return adminService.getWebGroundingDetail(id);
    }

    @GetMapping("/starter-prompts/daily-batches")
    public List<ChatStarterPromptDtos.DailyBatchRow> listDailyBatches(
            @RequestParam(defaultValue = "14") int limit) {
        return adminService.listDailyBatches(limit);
    }

    @PostMapping("/starter-prompts/refresh-daily-hot")
    public ChatStarterPromptDtos.RefreshDailyHotResult refreshDailyHot(
            @RequestParam(defaultValue = "true") boolean force) {
        return adminService.refreshDailyHot(force);
    }
}
