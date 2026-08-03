package com.aaron.cloud.chat.rest.api;

import com.aaron.cloud.chat.ChatApplicationService;
import com.aaron.cloud.chat.GuardrailSensitiveTermAdminService;
import com.aaron.cloud.chat.dto.ChatMessageView;
import com.aaron.cloud.chat.dto.ChatSensitiveTermAdminDtos;
import com.aaron.cloud.chat.dto.ChatSensitiveTermAdminDtos.SensitiveTermRow;
import com.aaron.cloud.common.chat.entity.ChatConversation;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端：当前租户内对话与消息只读抽查（需菜单 {@code CHAT}）。 */
@RestController
@RequiredArgsConstructor
public class ChatAdminRestController extends ApiV1ControllerBases.AdminChat {

    private final ChatApplicationService chatApplicationService;
    private final GuardrailSensitiveTermAdminService sensitiveTermAdminService;

    @GetMapping("/conversations")
    public Page<ChatConversation> listConversations(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long filterTenantId) {
        return chatApplicationService.listConversationsForAdmin(page, size, filterTenantId);
    }

    @GetMapping("/conversations/{id}/messages")
    public List<ChatMessageView> listMessages(@PathVariable("id") long id) {
        return chatApplicationService.listConversationMessagesForAdmin(id);
    }

    /** 鏁忔劅璇嶅钩鍙版睜鍒嗛〉锛堝叧閿瘝鍙€夛級锛涜瘝閲忓ぇ鏃惰鐢ㄦ鎺ュ彛鏇夸唬鍏ㄩ噺鎷夊彇銆?*/
    @GetMapping("/sensitive-terms/platform")
    public Page<SensitiveTermRow> pagePlatformSensitiveTerms(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String q) {
        return sensitiveTermAdminService.pagePlatform(page, size, q);
    }

    /** 鏁忔劅璇嶇鎴锋墿灞曟睜鍒嗛〉锛涘垱濮嬩汉鍙紶 {@code filterTenantId} 鎸囧畾鏁版嵁绉熸埛锛岀己鐪佷负 JWT 宸ヤ綔鍖虹鎴枫€?*/
    @GetMapping("/sensitive-terms/tenant")
    public Page<SensitiveTermRow> pageTenantSensitiveTerms(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long filterTenantId) {
        return sensitiveTermAdminService.pageTenant(page, size, q, filterTenantId);
    }

    @PostMapping("/sensitive-terms")
    public void addSensitiveTerm(@Valid @RequestBody ChatSensitiveTermAdminDtos.SensitiveTermAddBody body) {
        sensitiveTermAdminService.add(body);
    }

    @PostMapping("/sensitive-terms/import")
    public ChatSensitiveTermAdminDtos.SensitiveTermImportResult importSensitiveTerms(
            @Valid @RequestBody ChatSensitiveTermAdminDtos.SensitiveTermImportBody body) {
        return sensitiveTermAdminService.importBatch(body);
    }

    @DeleteMapping("/sensitive-terms/{id}")
    public void deleteSensitiveTerm(@PathVariable("id") long id) {
        sensitiveTermAdminService.delete(id);
    }
}
