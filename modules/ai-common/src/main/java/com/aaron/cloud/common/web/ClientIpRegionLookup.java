package com.aaron.cloud.common.web;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.lionsoul.ip2region.xdb.Searcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 无边缘地理头时，用 classpath 内嵌 {@code ip2region_v4.xdb} 将公网 IP 解析为 {@link LoginRegionResolver} 同款
 * {@code CC|省|市} 字符串；内网/回环 IP 返回 {@code null}。
 */
public final class ClientIpRegionLookup {

    private static final Logger LOG = LoggerFactory.getLogger(ClientIpRegionLookup.class);
    private static final String XDB_CLASSPATH = "/ip2region/ip2region_v4.xdb";

    private static final Map<String, String> COUNTRY_ZH_TO_ISO2 =
            Map.ofEntries(
                    Map.entry("中国", "CN"),
                    Map.entry("美国", "US"),
                    Map.entry("日本", "JP"),
                    Map.entry("韩国", "KR"),
                    Map.entry("英国", "GB"),
                    Map.entry("德国", "DE"),
                    Map.entry("法国", "FR"),
                    Map.entry("加拿大", "CA"),
                    Map.entry("澳大利亚", "AU"),
                    Map.entry("新加坡", "SG"),
                    Map.entry("印度", "IN"),
                    Map.entry("俄罗斯", "RU"),
                    Map.entry("巴西", "BR"),
                    Map.entry("马来西亚", "MY"),
                    Map.entry("泰国", "TH"),
                    Map.entry("越南", "VN"),
                    Map.entry("印度尼西亚", "ID"),
                    Map.entry("菲律宾", "PH"),
                    Map.entry("中国香港", "HK"),
                    Map.entry("中国澳门", "MO"),
                    Map.entry("中国台湾", "TW"));

    private static final AtomicReference<Searcher> SEARCHER = new AtomicReference<>();
    private static volatile boolean loadFailed;

    private ClientIpRegionLookup() {}

    /** 解析为写入 {@code last_login_region} 的字符串；不可用则 {@code null}。 */
    public static String resolveRegion(String ip) {
        if (ip == null) {
            return null;
        }
        String trimmed = ip.trim();
        if (trimmed.isEmpty() || isPrivateOrLocal(trimmed)) {
            return null;
        }
        Searcher searcher = getSearcher();
        if (searcher == null) {
            return null;
        }
        try {
            return parseSearchResult(searcher.search(trimmed));
        } catch (Exception ex) {
            return null;
        }
    }

    static String parseSearchResult(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split("\\|", -1);
        if (parts.length == 0) {
            return null;
        }
        String countryField = parts[0].trim();
        if (countryField.isEmpty() || "0".equals(countryField) || countryField.contains("内网")) {
            return null;
        }

        String cc = null;
        String province = null;
        String city = null;

        if (parts.length >= 5) {
            String iso = parts[4].trim();
            if (iso.length() == 2 && Character.isLetter(iso.charAt(0)) && Character.isLetter(iso.charAt(1))) {
                cc = iso.toUpperCase();
                province = zeroToNull(parts[1]);
                city = zeroToNull(parts[2]);
            }
        }
        if (cc == null) {
            cc = countryZhToIso2(countryField);
            if (parts.length >= 4) {
                province = zeroToNull(parts[2]);
                city = zeroToNull(parts[3]);
            } else if (parts.length >= 2) {
                province = zeroToNull(parts[1]);
            }
        }
        if (cc == null || cc.isBlank()) {
            return null;
        }
        return LoginRegionResolver.formatRegion(cc, province, city);
    }

    private static Searcher getSearcher() {
        if (loadFailed) {
            return null;
        }
        Searcher existing = SEARCHER.get();
        if (existing != null) {
            return existing;
        }
        synchronized (ClientIpRegionLookup.class) {
            existing = SEARCHER.get();
            if (existing != null) {
                return existing;
            }
            if (loadFailed) {
                return null;
            }
            try (InputStream in = ClientIpRegionLookup.class.getResourceAsStream(XDB_CLASSPATH)) {
                if (in == null) {
                    LOG.warn("ip2region xdb missing on classpath: {}", XDB_CLASSPATH);
                    loadFailed = true;
                    return null;
                }
                Searcher created = Searcher.newWithBuffer(in.readAllBytes());
                SEARCHER.set(created);
                return created;
            } catch (Exception ex) {
                LOG.warn("ip2region init failed: {}", ex.toString());
                loadFailed = true;
                return null;
            }
        }
    }

    static boolean isPrivateOrLocal(String ip) {
        if ("unknown".equalsIgnoreCase(ip) || "localhost".equalsIgnoreCase(ip)) {
            return true;
        }
        if (ip.startsWith("::ffff:")) {
            ip = ip.substring(7);
        }
        if (ip.contains(":")) {
            String lower = ip.toLowerCase();
            return lower.equals("::1")
                    || lower.startsWith("fe80:")
                    || lower.startsWith("fc")
                    || lower.startsWith("fd");
        }
        String[] octets = ip.split("\\.");
        if (octets.length != 4) {
            return false;
        }
        try {
            int a = Integer.parseInt(octets[0]);
            int b = Integer.parseInt(octets[1]);
            if (a == 10 || a == 127) {
                return true;
            }
            if (a == 172 && b >= 16 && b <= 31) {
                return true;
            }
            if (a == 192 && b == 168) {
                return true;
            }
            return a == 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static String zeroToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty() || "0".equals(t)) {
            return null;
        }
        return t;
    }

    private static String countryZhToIso2(String country) {
        if (country == null || country.isBlank()) {
            return null;
        }
        String t = country.trim();
        if (t.length() == 2 && Character.isLetter(t.charAt(0)) && Character.isLetter(t.charAt(1))) {
            return t.toUpperCase();
        }
        return COUNTRY_ZH_TO_ISO2.get(t);
    }
}
