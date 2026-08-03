package com.aaron.cloud.chat.rest.open;

import com.aaron.cloud.chat.ChatExternalImageProxyService;
import com.aaron.cloud.chat.support.ChatAttachmentHttpSupport;
import com.aaron.cloud.common.web.rest.OpenV1ControllerBases;
import java.io.IOException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ChatExternalImageController extends OpenV1ControllerBases.Chat {

    private final ChatExternalImageProxyService chatExternalImageProxyService;

    /** 对话 Markdown 外链图片同源代理（展示与分享长图内联；服务端出站拉取，含 SSRF 防护）。 */
    @GetMapping("/external-images/proxy")
    public ResponseEntity<byte[]> proxyExternalImage(@RequestParam("url") String url) {
        try {
            ChatExternalImageProxyService.ProxiedImage proxied = chatExternalImageProxyService.fetch(url);
            return ResponseEntity.ok()
                    .headers(
                            ChatAttachmentHttpSupport.contentHeaders(
                                    proxied.fileName(), proxied.contentType(), proxied.body().length))
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                    .body(proxied.body());
        } catch (IllegalArgumentException ex) {
            log.warn("external image proxy rejected url={}: {}", url, ex.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (IOException ex) {
            log.warn("external image proxy fetch failed url={}: {}", url, ex.getMessage());
            return ResponseEntity.status(502).build();
        }
    }
}
