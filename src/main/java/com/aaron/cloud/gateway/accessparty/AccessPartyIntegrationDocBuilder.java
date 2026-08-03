package com.aaron.cloud.gateway.accessparty;

import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.aaron.cloud.common.gateway.AccessPartyHttpHeaders;
import com.aaron.cloud.common.gateway.GwApiEndpointSpecSupport;
import com.aaron.cloud.common.gateway.entity.GwAccessParty;
import com.aaron.cloud.common.gateway.entity.GwApiEndpoint;
import com.aaron.cloud.common.gateway.openapi.GwApiOpenApiCatalogService;
import com.aaron.cloud.gateway.GatewayAccessPartyGrantApplicationService.GrantView;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 生成接入方 Markdown 对接文档（管理端导出）。 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AccessPartyIntegrationDocBuilder {

    private static final String EMPTY_BODY_SHA256 = AccessPartyHmacSupport.sha256Hex(new byte[0]);
    private static final DateTimeFormatter DOC_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public record IntegrationDoc(String filename, String markdown) {}

    public static IntegrationDoc build(
            GwAccessParty party, List<GrantView> grants, String baseUrl, java.time.LocalDateTime generatedAt) {
        return build(party, grants, baseUrl, generatedAt, null);
    }

    public static IntegrationDoc build(
            GwAccessParty party,
            List<GrantView> grants,
            String baseUrl,
            java.time.LocalDateTime generatedAt,
            GwApiOpenApiCatalogService openApiCatalog) {
        String root = normalizeBaseUrl(baseUrl);
        String appId = party.getAppId();
        String filename = "access-party-" + sanitizeFilename(appId) + "-integration.md";
        StringBuilder md = new StringBuilder(4096);
        md.append("# ").append(party.getDisplayName()).append(" — 第三方接入对接文档\n\n");
        md.append("> 生成时间：").append(generatedAt.format(DOC_TIME)).append(" (Asia/Shanghai)\n");
        md.append("> App Id：`").append(appId).append("`\n\n");

        md.append("## 1. 网关地址\n\n");
        if (root.isBlank()) {
            md.append("请向平台方确认 **HTTPS 网关根地址**（不含路径后缀），例如 `https://api.example.com`。\n\n");
        } else {
            md.append("网关根地址：`").append(root).append("`\n\n");
        }
        md.append("- **Partner 路径**（推荐）：`/partner/v1/**`，每个请求须携带 HMAC 签名。\n");
        md.append("- **Open 路径**（可选）：`/open/v1/**`，同样须携带完整签名头时走接入方鉴权链。\n");
        md.append("- 管理端 `/api/**` 与用户端无签名 `/open/**` **不属于**接入方调用方式。\n\n");

        appendAuthSection(md);
        appendQuotaSection(md, party);
        appendEndpointsSection(md, grants);
        appendApiDetailSection(md, grants, openApiCatalog);
        appendExamplesSection(md, root, appId);
        appendErrorSection(md);

        if (party.getRemark() != null && !party.getRemark().isBlank()) {
            md.append("## 8. 备注\n\n").append(party.getRemark().trim()).append("\n\n");
        }

        md.append("---\n\n");
        md.append("*本文档由管理端自动生成；Secret 不在线查询，请使用创建或轮换密钥时保存的值。*\n");

        return new IntegrationDoc(filename, md.toString());
    }

    private static void appendAuthSection(StringBuilder md) {
        md.append("## 2. 鉴权（HMAC-SHA256）\n\n");
        md.append("每个请求须携带以下 HTTP Header：\n\n");
        md.append("| Header | 必填 | 说明 |\n");
        md.append("|--------|------|------|\n");
        md.append("| `").append(AccessPartyHttpHeaders.APP_ID).append("` | 是 | 接入方 App Id |\n");
        md.append("| `").append(AccessPartyHttpHeaders.TIMESTAMP).append("` | 是 | Unix 秒；与服务端时差 ±300 秒 |\n");
        md.append("| `").append(AccessPartyHttpHeaders.NONCE).append("` | 是 | 随机串；600 秒内不可重复 |\n");
        md.append("| `").append(AccessPartyHttpHeaders.SIGNATURE).append("` | 是 | Base64(HMAC-SHA256(Secret, canonical)) |\n\n");

        md.append("**Secret**：创建接入方或「轮换密钥」时 **一次性** 下发，平台不存储明文，**无法再次查询**。\n\n");

        md.append("**Canonical 字符串**（UTF-8，5 行，换行符 `\\n`）：\n\n");
        md.append("```text\n");
        md.append("{HTTP_METHOD_UPPER}\n");
        md.append("{path_with_sorted_query}\n");
        md.append("{timestamp}\n");
        md.append("{nonce}\n");
        md.append("{sha256_hex_of_body}\n");
        md.append("```\n\n");

        md.append("- `path_with_sorted_query`：URI 路径 + 排序后的 Query（按 key 字典序，同 key 多值再排序）。\n");
        md.append("- `sha256_hex_of_body`：请求体 SHA-256 **小写十六进制**；无 body 时为 `")
                .append(EMPTY_BODY_SHA256)
                .append("`。\n");
        md.append("- Query 排序规则与网关 `AccessPartyHmacSupport#buildCanonicalPathWithQuery` 一致。\n\n");
    }

    private static void appendQuotaSection(StringBuilder md, GwAccessParty party) {
        md.append("## 3. 配额（RPM）\n\n");
        int total = party.getTotalRpmCap() == null ? 0 : party.getTotalRpmCap();
        if (total <= 0) {
            md.append("- 接入方 **总 RPM 上限**：未限制（0）。\n");
        } else {
            md.append("- 接入方 **总 RPM 上限**：").append(total).append(" 次/分钟（所有接口授权 RPM 之和不可超过此值）。\n");
        }
        md.append("- 下表 **授权 RPM** 为单接口每分钟上限；`0` 或未授权表示 **禁止调用**。\n");
        md.append("- 超限返回 HTTP **429**，业务码 `ACCESS_PARTY_RATE_LIMITED`。\n\n");
    }

    private static void appendEndpointsSection(StringBuilder md, List<GrantView> grants) {
        md.append("## 4. 已授权接口\n\n");
        List<GrantView> active =
                grants.stream()
                        .filter(
                                g ->
                                        g.getGrant().getEnabled() == ToggleState.ON
                                                && g.getGrant().getGrantedRpm() != null
                                                && g.getGrant().getGrantedRpm() > 0)
                        .sorted(Comparator.comparing(g -> endpointSortKey(g.getEndpoint())))
                        .toList();
        if (active.isEmpty()) {
            md.append("*当前无已启用且 RPM &gt; 0 的授权。请在管理端「授权绑定」向导配置后再导出。*\n\n");
            return;
        }
        md.append("| HTTP | 路径 | 授权 RPM | 说明 |\n");
        md.append("|------|------|----------|------|\n");
        for (GrantView v : active) {
            GwApiEndpoint ep = v.getEndpoint();
            String method = ep != null && ep.getHttpMethod() != null ? ep.getHttpMethod().trim() : "*";
            String path = ep != null && ep.getPathPattern() != null ? ep.getPathPattern() : String.valueOf(v.getGrant().getEndpointId());
            String name = ep != null && ep.getDisplayName() != null ? ep.getDisplayName() : "-";
            md.append("| `").append(method).append("` | `").append(path).append("` | ");
            md.append(v.getGrant().getGrantedRpm()).append(" | ").append(escapeCell(name)).append(" |\n");
        }
        md.append("\n");
    }

    private static void appendApiDetailSection(
            StringBuilder md, List<GrantView> grants, GwApiOpenApiCatalogService openApiCatalog) {
        md.append("## 5. 接口明细（入参 / 出参）\n\n");
        List<GrantView> active =
                grants.stream()
                        .filter(
                                g ->
                                        g.getGrant().getEnabled() == ToggleState.ON
                                                && g.getGrant().getGrantedRpm() != null
                                                && g.getGrant().getGrantedRpm() > 0)
                        .sorted(Comparator.comparing(g -> endpointSortKey(g.getEndpoint())))
                        .toList();
        if (active.isEmpty()) {
            md.append("*无已授权接口。*\n\n");
            return;
        }
        int idx = 1;
        for (GrantView v : active) {
            GwApiEndpoint ep = v.getEndpoint();
            String method = ep != null && ep.getHttpMethod() != null ? ep.getHttpMethod().trim() : "*";
            String path = ep != null && ep.getPathPattern() != null ? ep.getPathPattern() : "-";
            String name = ep != null && ep.getDisplayName() != null ? ep.getDisplayName() : "接口";
            md.append("### 5.").append(idx++).append(" ").append(name).append("\n\n");
            md.append("- **方法**：`").append(method).append("`\n");
            md.append("- **路径**：`").append(path).append("`\n");
            md.append("- **授权 RPM**：").append(v.getGrant().getGrantedRpm()).append("\n\n");
            if (ep != null) {
                String requestJson =
                        resolveRequestSpecJson(ep, openApiCatalog);
                String responseJson =
                        resolveResponseSpecJson(ep, openApiCatalog);
                md.append(GwApiEndpointSpecSupport.toRequestMarkdown(requestJson));
                md.append(GwApiEndpointSpecSupport.toResponseMarkdown(responseJson));
            }
        }
    }

    private static String resolveRequestSpecJson(GwApiEndpoint ep, GwApiOpenApiCatalogService openApiCatalog) {
        if (openApiCatalog == null) {
            return ep.getRequestSpecJson();
        }
        return openApiCatalog.effectiveRequestSpecJson(
                ep.getRequestSpecJson(), ep.getPathPattern(), ep.getHttpMethod());
    }

    private static String resolveResponseSpecJson(GwApiEndpoint ep, GwApiOpenApiCatalogService openApiCatalog) {
        if (openApiCatalog == null) {
            return ep.getResponseSpecJson();
        }
        return openApiCatalog.effectiveResponseSpecJson(
                ep.getResponseSpecJson(), ep.getPathPattern(), ep.getHttpMethod());
    }

    private static void appendExamplesSection(StringBuilder md, String root, String appId) {
        md.append("## 6. 调用示例\n\n");
        String healthPath = "/partner/v1/health";
        String url = root.isBlank() ? healthPath : root + healthPath;
        md.append("### 6.1 GET 健康检查\n\n");
        md.append("```bash\n");
        md.append("# 变量（示例）\n");
        md.append("APP_ID=\"").append(appId).append("\"\n");
        md.append("SECRET=\"<创建或轮换时保存的 Secret>\"\n");
        md.append("METHOD=\"GET\"\n");
        md.append("PATH=\"").append(healthPath).append("\"\n");
        md.append("TS=\"$(date +%s)\"\n");
        md.append("NONCE=\"$(uuidgen | tr -d '-')\"\n");
        md.append("BODY_HASH=\"").append(EMPTY_BODY_SHA256).append("\"\n");
        md.append("CANONICAL=\"${METHOD}\\n${PATH}\\n${TS}\\n${NONCE}\\n${BODY_HASH}\"\n");
        md.append("SIG=\"$(printf '%s' \"$CANONICAL\" | openssl dgst -sha256 -hmac \"$SECRET\" -binary | base64)\"\n\n");
        md.append("curl -sS -X GET \"").append(url).append("\" \\\n");
        md.append("  -H \"").append(AccessPartyHttpHeaders.APP_ID).append(": $APP_ID\" \\\n");
        md.append("  -H \"").append(AccessPartyHttpHeaders.TIMESTAMP).append(": $TS\" \\\n");
        md.append("  -H \"").append(AccessPartyHttpHeaders.NONCE).append(": $NONCE\" \\\n");
        md.append("  -H \"").append(AccessPartyHttpHeaders.SIGNATURE).append(": $SIG\"\n");
        md.append("```\n\n");

        md.append("### 6.2 Java 签名片段\n\n");
        md.append("```java\n");
        md.append("String canonical = method.toUpperCase() + \"\\n\" + pathWithQuery + \"\\n\"\n");
        md.append("    + timestamp + \"\\n\" + nonce + \"\\n\" + sha256Hex(bodyBytes);\n");
        md.append("Mac mac = Mac.getInstance(\"HmacSHA256\");\n");
        md.append("mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), \"HmacSHA256\"));\n");
        md.append("String signature = Base64.getEncoder().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));\n");
        md.append("```\n\n");
    }

    private static void appendErrorSection(StringBuilder md) {
        md.append("## 7. 常见错误\n\n");
        md.append("| HTTP | code | 说明 |\n");
        md.append("|------|------|------|\n");
        md.append("| 401 | `ACCESS_PARTY_UNAUTHORIZED` | App Id 无效、签名错误、时间戳/Nonce 无效 |\n");
        md.append("| 403 | `ACCESS_PARTY_ENDPOINT_DENIED` | 接口未授权或授权 RPM 为 0 |\n");
        md.append("| 429 | `ACCESS_PARTY_RATE_LIMITED` | 超过单接口或接入方总 RPM |\n\n");
    }

    private static String endpointSortKey(GwApiEndpoint ep) {
        if (ep == null) {
            return "zzzz";
        }
        String path = ep.getPathPattern() == null ? "" : ep.getPathPattern();
        String method = ep.getHttpMethod() == null ? "" : ep.getHttpMethod();
        return path + "#" + method;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        String s = baseUrl.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String sanitizeFilename(String appId) {
        if (appId == null || appId.isBlank()) {
            return "unknown";
        }
        return appId.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String escapeCell(String text) {
        return text.replace("|", "\\|").replace("\n", " ");
    }
}
