package com.aaron.cloud.chat.websearch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 启动时打印固定源出站模式，便于对照 Postman / curl 与 JVM 代理差异。 */
@Slf4j
@Component
public class WebSearchFixedSourceOutboundDiagnostics {

    @EventListener(ApplicationReadyEvent.class)
    void logOutboundMode() {
        log.info(
                "[联网搜索] 固定源出站诊断（进程默认）：{}；租户级请在 Shell「外观与模型调用 → 固定源 HTTP 代理」配置",
                WebSearchFixedSourceHttp.outboundDiagnostics());
    }
}
