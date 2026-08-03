package com.aaron.cloud.chat.intent.mcp;

import com.aaron.cloud.common.api.dto.mcp.McpToolInvokeResult;
import com.aaron.cloud.common.api.ports.McpInvokePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UtilityClass
public final class IntentMcpSingleInvoke {

    private static final Logger log = LoggerFactory.getLogger(IntentMcpSingleInvoke.class);

    private static final int USER_MESSAGE_MAX_LEN = 500;

    public static McpJsonResult invokeOnce(
            McpInvokePort port,
            long tenantId,
            IntentMcpToolBinding binding,
            JsonNode arguments,
            ObjectMapper objectMapper)
            throws Exception {
        String tool = binding.qualifiedName();
        String action = arguments != null && arguments.has("action") ? arguments.get("action").asText() : "?";
        String utterance =
                arguments != null && arguments.has("utterance")
                        ? abbreviate(arguments.get("utterance").asText(), 80)
                        : "";
        log.info(
                "[意图·MCP] ① 调用开始 tenantId={} tool={} action={} utterance={}",
                tenantId,
                tool,
                action,
                utterance);
        long t0 = System.nanoTime();
        binding.assertToolAvailable(port, tenantId);
        McpToolInvokeResult raw = port.invokeTool(tenantId, tool, arguments);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        if (raw == null) {
            log.warn(
                    "[意图·MCP] ② 调用失败 tenantId={} tool={} reason=无响应 elapsedMs={}",
                    tenantId,
                    tool,
                    elapsedMs);
            return McpJsonResult.fail("话术解析服务无响应，请稍后重试", null, null);
        }
        String text = raw.getTextContent();
        if (raw.isError()) {
            McpJsonResult fromBody = tryParseErrorPayload(objectMapper, text);
            if (fromBody != null) {
                log.info(
                        "[意图·MCP] ② 协议层 error 但正文可解析 tenantId={} tool={} businessError={} elapsedMs={}",
                        tenantId,
                        tool,
                        abbreviate(fromBody.userFacingMessage(), 120),
                        elapsedMs);
                return fromBody;
            }
            String userMsg =
                    resolveUserMessage(
                            text, null, "话术解析服务返回异常（工具 " + tool + "），请稍后重试或联系管理员");
            log.warn(
                    "[意图·MCP] ② 调用失败 tenantId={} tool={} reason=协议层error userMsg={} elapsedMs={} bodyPreview={}",
                    tenantId,
                    tool,
                    abbreviate(userMsg, 120),
                    elapsedMs,
                    abbreviate(text, 200));
            return McpJsonResult.fail(userMsg, null, text);
        }
        if (text == null || text.isBlank()) {
            log.warn(
                    "[意图·MCP] ② 调用失败 tenantId={} tool={} reason=正文为空 elapsedMs={}",
                    tenantId,
                    tool,
                    elapsedMs);
            return McpJsonResult.fail("话术解析服务返回为空，请稍后重试", null, null);
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            log.info(
                    "[意图·MCP] ② 调用完成 tenantId={} tool={} op={} businessError={} elapsedMs={} invokeReportedMs={}",
                    tenantId,
                    tool,
                    node.path("op").asText(""),
                    abbreviate(jsonErrorField(node), 120),
                    elapsedMs,
                    raw.getElapsedMs());
            return new McpJsonResult(true, null, node, text);
        } catch (Exception e) {
            String userMsg =
                    resolveUserMessage(
                            text, null, "话术解析返回无法识别：" + abbreviate(text, 200));
            log.warn(
                    "[意图·MCP] ② 调用失败 tenantId={} tool={} reason=非JSON elapsedMs={} err={}",
                    tenantId,
                    tool,
                    elapsedMs,
                    e.toString());
            return McpJsonResult.fail(userMsg, null, text);
        }
    }

    /**
     * 从 MCP 正文提取可展示给用户的说明：优先 JSON {@code error}，否则截取正文（避免「MCP 工具返回错误」类空话）。
     */
    private static McpJsonResult tryParseErrorPayload(ObjectMapper objectMapper, String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            String err = jsonErrorField(node);
            if (!err.isEmpty()) {
                return new McpJsonResult(true, null, node, text);
            }
        } catch (Exception ignored) {
            // not JSON
        }
        return null;
    }

    private static String resolveUserMessage(String text, JsonNode node, String fallback) {
        if (node != null) {
            String err = jsonErrorField(node);
            if (!err.isEmpty()) {
                return err;
            }
            String um = node.path("userMessage").asText("").strip();
            if (!um.isEmpty()) {
                return um;
            }
            String msg = node.path("message").asText("").strip();
            if (!msg.isEmpty()) {
                return msg;
            }
        }
        if (text != null && !text.isBlank()) {
            String t = text.strip();
            if (t.startsWith("{")) {
                return fallback;
            }
            return abbreviate(t, USER_MESSAGE_MAX_LEN);
        }
        return fallback;
    }

    private static String jsonErrorField(JsonNode node) {
        if (node == null || !node.has("error") || node.get("error").isNull()) {
            return "";
        }
        return node.get("error").asText("").strip();
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.strip();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "…";
    }

    public record McpJsonResult(boolean ok, String errorMessage, JsonNode body, String rawText) {

        public static McpJsonResult fail(String msg, JsonNode body, String rawText) {
            return new McpJsonResult(false, msg, body, rawText);
        }

        public boolean failed() {
            return !ok;
        }

        /** 给终端用户与运维看的最终文案：优先 JSON {@code error}，其次传输层 message，最后正文摘要。 */
        public String userFacingMessage() {
            if (body != null) {
                String err = jsonErrorField(body);
                if (!err.isEmpty()) {
                    return err;
                }
                String um = body.path("userMessage").asText("").strip();
                if (!um.isEmpty()) {
                    return um;
                }
            }
            if (errorMessage != null && !errorMessage.isBlank()) {
                return errorMessage;
            }
            if (rawText != null && !rawText.isBlank()) {
                return abbreviate(rawText.strip(), USER_MESSAGE_MAX_LEN);
            }
            return "话术解析失败，请稍后重试";
        }

        public <T> T bodyAs(ObjectMapper mapper, Class<T> type) throws com.fasterxml.jackson.core.JsonProcessingException {
            return mapper.treeToValue(body, type);
        }
    }
}
