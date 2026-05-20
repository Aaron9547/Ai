package com.aaron.cloud.rag;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.nodes.Element;

/** 从 {@code <a>} 提取 href（含 onclick / data-href），对齐 ly-ai {@code JsoupHelper#extractRawLink}。 */
public final class RagCrawlLinkExtractSupport {

    private static final Pattern ONCLICK_URL_PATTERN =
            Pattern.compile("(?i)(?:window\\.)?(?:location(?:\\.href)?|open)\\s*\\(\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern RAW_URL_IN_JS_PATTERN =
            Pattern.compile("['\"]((?:https?:)?//[^'\"\\s]+|/[^'\"\\s]+|\\.\\.?/[^'\"\\s]+)['\"]");

    private RagCrawlLinkExtractSupport() {}

    public static String extractHref(Element element, String pageUrl) {
        if (element == null) {
            return "";
        }
        String href = element.hasAttr("href") ? element.attr("href").trim() : "";
        if (!href.isEmpty() && !href.toLowerCase(Locale.ROOT).startsWith("javascript:") && !href.startsWith("#")) {
            return toAbsoluteUrl(pageUrl, element.absUrl("href"));
        }
        if (element.hasAttr("data-href") && !element.attr("data-href").isBlank()) {
            return toAbsoluteUrl(pageUrl, element.absUrl("data-href"));
        }
        if (element.hasAttr("data-url") && !element.attr("data-url").isBlank()) {
            return toAbsoluteUrl(pageUrl, element.absUrl("data-url"));
        }
        String onclick = element.attr("onclick").trim();
        if (onclick.isEmpty()) {
            return "";
        }
        Matcher direct = ONCLICK_URL_PATTERN.matcher(onclick);
        if (direct.find()) {
            return toAbsoluteUrl(pageUrl, direct.group(1).trim());
        }
        Matcher raw = RAW_URL_IN_JS_PATTERN.matcher(onclick);
        if (raw.find()) {
            return toAbsoluteUrl(pageUrl, raw.group(1).trim());
        }
        return "";
    }

    public static String linkTitle(Element element) {
        if (element == null) {
            return "";
        }
        if (element.hasAttr("title") && !element.attr("title").isBlank()) {
            return element.attr("title").trim();
        }
        return element.text() == null ? "" : element.text().trim();
    }

    private static String toAbsoluteUrl(String pageUrl, String rawLink) {
        if (rawLink == null || rawLink.isBlank()) {
            return "";
        }
        String trimmed = rawLink.trim();
        if (trimmed.startsWith("//")) {
            try {
                URI base = URI.create(pageUrl);
                return base.getScheme() + ":" + trimmed;
            } catch (Exception e) {
                return "https:" + trimmed;
            }
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        try {
            return URI.create(pageUrl).resolve(trimmed).toString();
        } catch (Exception e) {
            return trimmed;
        }
    }
}
