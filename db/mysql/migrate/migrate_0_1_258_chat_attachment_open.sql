-- 开放接口：会话附件 GET 预览/下载（已建库手工执行）
INSERT IGNORE INTO gw_api_endpoint (path_pattern, http_method, display_name, remark, enabled, sort_order, created_at, updated_at) VALUES
('/open/v1/chat/conversations/*/attachments/*', 'GET', '会话附件预览下载', 'inline/attachment', 1, 1101, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
