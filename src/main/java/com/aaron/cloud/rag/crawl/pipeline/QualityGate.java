package com.aaron.cloud.rag.crawl.pipeline;

import com.aaron.cloud.rag.RagHtmlRedirectSupport;
import com.aaron.cloud.rag.crawl.policy.EffectiveSiteCrawlPolicy;
import org.springframework.stereotype.Component;

@Component
public class QualityGate {

    public record QualityResult(boolean pass, String errorCode) {}

    public QualityResult check(
            String markdown, String html, EffectiveSiteCrawlPolicy.IngestPolicy ingest) {
        if (markdown == null || markdown.isBlank()) {
            return new QualityResult(false, "EMPTY_MARKDOWN");
        }
        if (markdown.length() < ingest.minMarkdownChars()) {
            return new QualityResult(false, "EMPTY_MARKDOWN");
        }
        if (ingest.rejectGarbled() && looksGarbled(markdown)) {
            return new QualityResult(false, "GARBLED_TEXT");
        }
        if (ingest.rejectRedirectShellOnly()
                && html != null
                && RagHtmlRedirectSupport.isRedirectShell(html)
                && markdown.length() < ingest.minMarkdownChars() * 2) {
            return new QualityResult(false, "REDIRECT_SHELL");
        }
        return new QualityResult(true, null);
    }

    private static boolean looksGarbled(String md) {
        int weird = 0;
        for (int i = 0; i < md.length(); i++) {
            char c = md.charAt(i);
            if (c == '\uFFFD' || (c < 32 && c != '\n' && c != '\r' && c != '\t')) {
                weird++;
            }
        }
        return weird > md.length() / 20;
    }
}
