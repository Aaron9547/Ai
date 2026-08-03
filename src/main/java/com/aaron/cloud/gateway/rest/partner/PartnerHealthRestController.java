package com.aaron.cloud.gateway.rest.partner;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PartnerHealthRestController {

    @GetMapping("/partner/v1/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
