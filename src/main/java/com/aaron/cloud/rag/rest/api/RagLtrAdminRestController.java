package com.aaron.cloud.rag.rest.api;

import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.rag.ltr.RagLtrAdminApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RagLtrAdminRestController extends ApiV1ControllerBases.AdminRagLtr {

    private final RagLtrAdminApplicationService ragLtrAdminApplicationService;

    @PostMapping("/train")
    public RagLtrAdminApplicationService.TrainResponse train(@RequestBody RagLtrAdminApplicationService.TrainRequest body) {
        return ragLtrAdminApplicationService.enqueueTrain(body);
    }

    @GetMapping("/status")
    public RagLtrAdminApplicationService.StatusResponse status() {
        return ragLtrAdminApplicationService.status();
    }
}
