package com.aaron.cloud.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * 登录成功后写入 {@code sec_user_account.last_login_region} 的来源说明：
 *
 * <ul>
 *   <li>优先使用边缘/网关注入的地理请求头（无需本机再查 IP 库）。</li>
 *   <li>Cloudflare：{@code CF-IPCountry}（ISO3166-1 alpha-2）+ 可选 {@code CF-Region}（省/州名）+ 可选 {@code CF-IPCity}（城市名）。</li>
 *   <li>有省或市时存为 {@code CC|region|city}（两段竖线分隔，便于前端展示「国家·省·市」；世界地图取首段 ISO 码聚合）。</li>
 *   <li>仅有国家码时仍只存两位大写国家码（与历史数据兼容）。</li>
 *   <li>Google App Engine：{@code X-Appengine-Country}（仅国家）。</li>
 * </ul>
 *
 * <p>若部署前未经过上述代理，则回退 {@link ClientIpRegionLookup}（内嵌 {@code ip2region_v4.xdb}，仅公网 IP）；内网/回环仍可能为空。
 */
public final class LoginRegionResolver {

    private static final int MAX_LEN = 128;

    private LoginRegionResolver() {}

    /** 解析为写入库的字符串；无可用信息时返回 {@code null}。 */
    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String fromHeaders = resolveFromHeaders(request);
        if (fromHeaders != null) {
            return fromHeaders;
        }
        return ClientIpRegionLookup.resolveRegion(HttpClientIp.resolve(request));
    }

    /** 与 {@link #resolve(HttpServletRequest)} 相同格式，供大屏在 {@code last_login_region} 为空时按 IP 回退。 */
    public static String formatRegion(String country2, String region, String city) {
        return compose(country2, region, city);
    }

    private static String resolveFromHeaders(HttpServletRequest request) {
        String cfCountry = trimHeader(request, "CF-IPCountry");
        if (cfCountry != null && !isUnknownCountry(cfCountry)) {
            return compose(
                    normalizeCountryCode(cfCountry),
                    trimHeader(request, "CF-Region"),
                    trimHeader(request, "CF-IPCity"));
        }
        String cloudFront = trimHeader(request, "CloudFront-Viewer-Country");
        if (cloudFront != null && !isUnknownCountry(cloudFront)) {
            return compose(normalizeCountryCode(cloudFront), null, null);
        }
        String xCountry = trimHeader(request, "X-Country-Code");
        if (xCountry != null && !isUnknownCountry(xCountry)) {
            return compose(normalizeCountryCode(xCountry), null, null);
        }
        String gae = trimHeader(request, "X-Appengine-Country");
        if (gae != null && !isUnknownCountry(gae)) {
            return compose(normalizeCountryCode(gae), null, null);
        }
        String legacy = trimHeader(request, "True-Client-Geo");
        if (legacy != null && !isUnknownCountry(legacy)) {
            return compose(normalizeCountryCode(legacy), null, null);
        }
        return null;
    }

    private static String trimHeader(HttpServletRequest request, String name) {
        String v = request.getHeader(name);
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean isUnknownCountry(String raw) {
        String t = raw.trim();
        return t.isEmpty()
                || "XX".equalsIgnoreCase(t)
                || "ZZ".equalsIgnoreCase(t)
                || "T1".equalsIgnoreCase(t);
    }

    /** 规范为两位国家码；异常长度时截取前两位大写字母。 */
    private static String normalizeCountryCode(String raw) {
        String t = raw.trim().toUpperCase();
        if (t.length() <= 2) {
            return t;
        }
        String two = t.substring(0, 2);
        if (Character.isLetter(two.charAt(0)) && Character.isLetter(two.charAt(1))) {
            return two;
        }
        return t.length() > 2 ? t.substring(0, Math.min(2, t.length())) : t;
    }

    /**
     * @param country2 两位国家码
     * @param region 省/州/大区（英文或中文依边缘注入为准）
     * @param city 城市名
     */
    private static String compose(String country2, String region, String city) {
        if (country2 == null || country2.isBlank()) {
            return null;
        }
        String cc = country2.length() > 2 ? country2.substring(0, 2) : country2;
        String r = blankToNull(region);
        String c = blankToNull(city);
        if (r != null && c != null && r.equalsIgnoreCase(c)) {
            c = null;
        }
        boolean hasDetail = r != null || c != null;
        if (!hasDetail) {
            return truncate(cc, MAX_LEN);
        }
        List<String> parts = new ArrayList<>(4);
        parts.add(cc);
        if (r != null) {
            parts.add(r);
        }
        if (c != null) {
            parts.add(c);
        }
        return truncate(String.join("|", parts), MAX_LEN);
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
