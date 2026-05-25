package com.aaron.cloud.chat.rest.partner;

import com.aaron.cloud.common.context.AccessPartyContextHolder;
import com.aaron.cloud.common.web.rest.PartnerV1ControllerBases;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PartnerChatProbeRestController extends PartnerV1ControllerBases.Chat {

    @GetMapping("/probe")
    public Map<String, Object> probe() {
        var ap = AccessPartyContextHolder.getOrNull();
        return Map.of(
                "ok", true,
                "accessPartyAppId", ap == null ? null : ap.getAppId());
    }
}
