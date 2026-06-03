package com.aaron.cloud.mcp.remote;

import com.aaron.cloud.common.api.dto.mcp.McpServerProbeResult;
import com.aaron.cloud.common.api.dto.mcp.McpToolDescriptor;
import com.aaron.cloud.common.api.dto.mcp.McpToolInvokeResult;
import com.aaron.cloud.common.api.enums.infra.OutboundKind;
import com.aaron.cloud.common.api.enums.mcp.McpServerStatus;
import com.aaron.cloud.common.mcp.McpServerRegistryRepository;
import com.aaron.cloud.common.mcp.entity.McpServerRegistry;
import com.aaron.cloud.common.outbound.WebSearchFixedSourceOutboundResolver;
import com.aaron.cloud.common.outbound.OutboundCircuitBreakerSupport;
import com.aaron.cloud.common.outbound.OutboundMetricsSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpRemoteSessionService {

    private final McpServerRegistryRepository mcpServerRegistryRepository;
    private final McpRemoteClientFactory mcpRemoteClientFactory;
    private final McpToolSchemaMapper mcpToolSchemaMapper;
    private final ObjectMapper objectMapper;
    private final OutboundCircuitBreakerSupport outboundCircuitBreakerSupport;
    private final OutboundMetricsSupport outboundMetricsSupport;
    private final WebSearchFixedSourceOutboundResolver outboundResolver;

    public List<McpToolDescriptor> listTools(long tenantId, List<Long> serverIds, Duration timeout)
            throws Exception {
        List<McpToolDescriptor> all = new ArrayList<>();
        for (McpServerRegistry server : activeServers(tenantId, serverIds)) {
            all.addAll(listToolsOnServer(tenantId, server, timeout));
        }
        return all;
    }

    /** 仅连接指定名称的远程 MCP（用于意图单工具就绪检查，避免拉起租户其它 MCP）。 */
    public List<McpToolDescriptor> listToolsForServerName(
            long tenantId, String serverName, Duration timeout) throws Exception {
        if (serverName == null || serverName.isBlank()) {
            return List.of();
        }
        String want = serverName.trim();
        for (McpServerRegistry server : activeServers(tenantId, null)) {
            if (server.getName() != null && server.getName().equalsIgnoreCase(want)) {
                return listToolsOnServer(tenantId, server, timeout);
            }
        }
        return List.of();
    }

    private List<McpToolDescriptor> listToolsOnServer(
            long tenantId, McpServerRegistry server, Duration timeout) throws Exception {
        try (McpSyncClient client = mcpRemoteClientFactory.open(tenantId, server, timeout)) {
            client.initialize();
            var tools = client.listTools();
            return mcpToolSchemaMapper.toDescriptors(server, tools.tools());
        }
    }

    public McpToolInvokeResult invokeTool(
            long tenantId, String qualifiedName, JsonNode arguments, Duration timeout) throws Exception {
        var parsed = McpQualifiedToolName.parse(qualifiedName);
        McpServerRegistry server = requireActiveServerByName(tenantId, parsed.serverName());
        long t0 = System.nanoTime();
        var cb = outboundCircuitBreakerSupport.forMcp(tenantId, server.getId());
        if (cb.isPresent() && !cb.get().tryAcquirePermission()) {
            outboundMetricsSupport.recordError(OutboundKind.MCP, "CIRCUIT_OPEN", 503);
            throw OutboundCircuitBreakerSupport.circuitOpen(OutboundKind.MCP, server.getName());
        }
        try (McpSyncClient client = mcpRemoteClientFactory.open(tenantId, server, timeout)) {
            client.initialize();
            Map<String, Object> args = jsonNodeToMap(arguments);
            CallToolResult result = client.callTool(new CallToolRequest(parsed.toolName(), args));
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            cb.ifPresent(c -> c.onSuccess(System.nanoTime() - t0, TimeUnit.NANOSECONDS));
            String text = extractText(result);
            return McpToolInvokeResult.builder()
                    .qualifiedName(qualifiedName)
                    .serverId(server.getId())
                    .toolName(parsed.toolName())
                    .error(result.isError() != null && result.isError())
                    .textContent(text)
                    .elapsedMs(elapsedMs)
                    .build();
        } catch (Exception e) {
            cb.ifPresent(c -> c.onError(System.nanoTime() - t0, TimeUnit.NANOSECONDS, e));
            outboundMetricsSupport.recordError(OutboundKind.MCP, e.getClass().getSimpleName(), null);
            throw e;
        }
    }

    public McpServerProbeResult probe(long tenantId, long serverId, Duration timeout) throws Exception {
        McpServerRegistry server = requireServer(tenantId, serverId);
        String outboundDiag = outboundResolver.diagnostics(tenantId);
        long t0 = System.nanoTime();
        try (McpSyncClient client = mcpRemoteClientFactory.open(tenantId, server, timeout)) {
            client.initialize();
            var listed = client.listTools();
            int count = listed.tools() == null ? 0 : listed.tools().size();
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            server.setLastProbeAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
            server.setLastProbeOk(1);
            mcpServerRegistryRepository.updateById(server);
            return McpServerProbeResult.builder()
                    .ok(true)
                    .message("ok")
                    .toolCount(count)
                    .elapsedMs(elapsedMs)
                    .build();
        } catch (Exception e) {
            server.setLastProbeAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
            server.setLastProbeOk(0);
            mcpServerRegistryRepository.updateById(server);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            log.warn(
                    "mcp probe failed serverId={} name={} baseUrl={} outbound={}: {}",
                    serverId,
                    server.getName(),
                    server.getBaseUrl(),
                    outboundDiag,
                    e.toString());
            return McpServerProbeResult.builder()
                    .ok(false)
                    .message(probeFailureMessage(server, outboundDiag, e))
                    .toolCount(0)
                    .elapsedMs(elapsedMs)
                    .build();
        }
    }

    private List<McpServerRegistry> activeServers(long tenantId, List<Long> serverIds) {
        return mcpServerRegistryRepository.listActiveByTenantAndIds(tenantId, serverIds);
    }

    private McpServerRegistry requireActiveServerByName(long tenantId, String name) {
        return activeServers(tenantId, null).stream()
                .filter(s -> s.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("mcp server not found or disabled: " + name));
    }

    private McpServerRegistry requireServer(long tenantId, long serverId) {
        McpServerRegistry row = mcpServerRegistryRepository.findByIdAndTenant(serverId, tenantId);
        if (row == null) {
            throw new IllegalArgumentException("mcp server not found");
        }
        return row;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, Map.class);
    }

    private static String extractText(CallToolResult result) {
        if (result == null || result.content() == null || result.content().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (var c : result.content()) {
            if (c instanceof TextContent tc && tc.text() != null) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(tc.text());
            }
        }
        return sb.toString();
    }

    private static String probeFailureMessage(McpServerRegistry server, String outboundDiag, Exception e) {
        if (isConnectTimeout(e)) {
            return "HTTP 连接超时（当前出站: "
                    + outboundDiag
                    + "）。请确认 Clash HTTP 口（常见 7890）可达，或配置 ai.websearch.fixed.use-system-proxy=true 后重启";
        }
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static boolean isConnectTimeout(Throwable e) {
        for (Throwable cur = e; cur != null; cur = cur.getCause()) {
            if (cur instanceof HttpConnectTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
