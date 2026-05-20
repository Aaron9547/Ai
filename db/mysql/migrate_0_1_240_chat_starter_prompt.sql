-- 0.1.240-SNAPSHOT：对话推荐问题（运营池 + 每日联网热点 + 追问 + 埋点）
-- 补丁位须与 pom.xml / PROJECT.md 顶节一致。已建库（如 test_ai）须执行本脚本；新库若 schema_v1 已含表可跳过 DDL，仍建议执行网关 INSERT 段。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS chat_starter_prompt (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  scene VARCHAR(32) NOT NULL COMMENT 'ChatStarterPromptScene：EMPTY|FOLLOW_UP',
  source VARCHAR(32) NOT NULL COMMENT 'ChatStarterPromptSource：MANUAL|HOT_TOPIC_DAILY|LLM_FOLLOW_UP',
  prompt_text VARCHAR(256) NOT NULL COMMENT '推荐问句（展示即发送文案）',
  weight INT NOT NULL DEFAULT 100 COMMENT '抽样权重（越大越易被抽到）',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '0=OFF 1=ON',
  require_thinking TINYINT NULL COMMENT 'NULL=不限；1=仅当模型支持思考时展示',
  require_web_search TINYINT NULL COMMENT 'NULL=不限；1=仅当租户可联网时展示',
  valid_from DATE NULL COMMENT '生效日（含）',
  valid_until DATE NULL COMMENT '失效日（含）',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '同权重时排序',
  batch_key VARCHAR(32) NULL COMMENT 'HOT_TOPIC_DAILY 批次键（如 20260519）',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_csp_tenant_scene_en (tenant_id, scene, enabled),
  KEY idx_csp_tenant_batch (tenant_id, batch_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话推荐问题池';

CREATE TABLE IF NOT EXISTS chat_starter_daily_batch (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  topic_date DATE NOT NULL COMMENT '热点日期（Asia/Shanghai）',
  status VARCHAR(32) NOT NULL COMMENT 'ChatStarterDailyBatchStatus：PENDING|OK|FAILED',
  questions_json MEDIUMTEXT NULL COMMENT 'JSON 字符串数组',
  error_message VARCHAR(512) NULL COMMENT '失败原因',
  fetched_at DATETIME(3) NULL COMMENT '拉取完成时间',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_csdb_tenant_date (tenant_id, topic_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日联网热点推荐批次';

CREATE TABLE IF NOT EXISTS chat_starter_follow_up_cache (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  assistant_message_id BIGINT NOT NULL COMMENT 'chat_message.id（助手消息）',
  questions_json MEDIUMTEXT NOT NULL COMMENT 'JSON 字符串数组',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_csfuc_tenant_msg (tenant_id, assistant_message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='助手消息后猜你想问缓存';

CREATE TABLE IF NOT EXISTS chat_starter_event (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  user_id BIGINT NULL COMMENT '登录用户',
  device_id VARCHAR(64) NULL COMMENT '设备访客',
  prompt_id BIGINT NULL COMMENT 'chat_starter_prompt.id',
  scene VARCHAR(32) NOT NULL COMMENT 'ChatStarterPromptScene',
  event_type VARCHAR(32) NOT NULL COMMENT 'ChatStarterEventType：IMPRESSION|CLICK|SEND',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_cse_tenant_created (tenant_id, created_at),
  KEY idx_cse_prompt (prompt_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐问题埋点';

-- 默认租户种子（运营兜底 + 示例）
SET @def_tid := (SELECT id FROM sys_tenant WHERE code = 'default' LIMIT 1);

INSERT IGNORE INTO chat_starter_prompt (tenant_id, scene, source, prompt_text, weight, enabled, sort_order, created_at, updated_at)
SELECT @def_tid, 'EMPTY', 'MANUAL', '写一首关于春天的诗', 100, 1, 10, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM DUAL WHERE @def_tid IS NOT NULL;

INSERT IGNORE INTO chat_starter_prompt (tenant_id, scene, source, prompt_text, weight, enabled, sort_order, created_at, updated_at)
SELECT @def_tid, 'EMPTY', 'MANUAL', '用通俗语言解释量子纠缠', 100, 1, 11, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM DUAL WHERE @def_tid IS NOT NULL;

INSERT IGNORE INTO chat_starter_prompt (tenant_id, scene, source, prompt_text, weight, enabled, sort_order, created_at, updated_at)
SELECT @def_tid, 'EMPTY', 'MANUAL', '帮我生成一份周报模板', 100, 1, 12, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM DUAL WHERE @def_tid IS NOT NULL;

INSERT IGNORE INTO chat_starter_prompt (tenant_id, scene, source, prompt_text, weight, enabled, sort_order, created_at, updated_at)
SELECT @def_tid, 'EMPTY', 'MANUAL', '总结今天科技圈三条热点', 80, 1, 20, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM DUAL WHERE @def_tid IS NOT NULL;

INSERT IGNORE INTO gw_api_endpoint (path_pattern, http_method, display_name, remark, enabled, sort_order, created_at, updated_at)
VALUES
('/open/v1/chat/starter-prompts', 'GET', 'C端推荐问题抽样', NULL, 1, 2105, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/open/v1/chat/starter-prompts/events', 'POST', 'C端推荐问题埋点', NULL, 1, 2106, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/open/v1/chat/conversations/*/messages/*/follow-up-prompts', 'GET', 'C端助手消息后追问推荐', NULL, 1, 2107, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/chat/starter-prompts', 'GET', '管理端推荐问题列表', NULL, 1, 4370, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/chat/starter-prompts', 'POST', '管理端新建推荐问题', NULL, 1, 4371, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/chat/starter-prompts/*', 'PUT', '管理端更新推荐问题', NULL, 1, 4372, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/chat/starter-prompts/*', 'DELETE', '管理端删除推荐问题', NULL, 1, 4373, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/chat/starter-prompts/refresh-daily-hot', 'POST', '管理端手动刷新每日热点', NULL, 1, 4374, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/chat/starter-prompts/daily-batches', 'GET', '管理端每日热点批次', NULL, 1, 4375, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
