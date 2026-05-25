package com.aaron.cloud.identity.rest.api;

import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.identity.admin.AdminMeApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminMeRestController extends ApiV1ControllerBases.AdminMe {

    private final AdminMeApplicationService adminMeApplicationService;

    @GetMapping
    public AdminMeApplicationService.AdminMeView me() {
        return adminMeApplicationService.snapshot();
    }
}
