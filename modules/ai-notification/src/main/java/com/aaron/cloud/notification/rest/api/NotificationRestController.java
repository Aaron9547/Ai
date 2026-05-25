package com.aaron.cloud.notification.rest.api;

import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.notification.NotificationApplicationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NotificationRestController extends ApiV1ControllerBases.Notifications {

    private final NotificationApplicationService notificationApplicationService;

    @PostMapping("/webhooks")
    public RegisterResponse register(@RequestBody RegisterBody body) {
        long id =
                notificationApplicationService.registerWebhook(
                        body.getTargetUrl(), body.getSecretHandle());
        return new RegisterResponse(id);
    }

    @Data
    public static class RegisterBody {
        private String targetUrl;
        private String secretHandle;
    }

    public record RegisterResponse(long subscriptionId) {}
}
