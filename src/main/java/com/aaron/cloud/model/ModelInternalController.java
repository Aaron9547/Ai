package com.aaron.cloud.model;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/model")
@RequiredArgsConstructor
public class ModelInternalController {

    private final ModelApplicationService modelApplicationService;

    /** 供 Feign 远程聚合时同步拉取整段文本（非真流式）。 */
    @PostMapping("/completion")
    public String completion(@RequestBody ModelChatRequest request) throws Exception {
        AtomicReference<StringBuilder> buf = new AtomicReference<>(new StringBuilder());
        modelApplicationService.streamCompletion(request, s -> buf.get().append(s));
        return buf.toString();
    }
}
