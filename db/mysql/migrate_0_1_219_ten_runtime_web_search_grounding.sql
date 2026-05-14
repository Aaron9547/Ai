-- 联网前置多轮检索：向 ten_runtime_setting 写入默认键（依赖列已为 MEDIUMTEXT）。
-- 须先执行同目录 migrate_0_1_219_ten_runtime_setting_mediumtext.sql（仅 ALTER），再执行本脚本。
-- 不使用 JSON_ARRAY()：部分 MariaDB / 旧 MySQL 无该函数。
--
-- 期望语义（与轮数 3 对齐的 JSON 字符串数组；换行为 JSON 转义 \\n）：
--   [0] 空串 — 第 1 轮不追加；
--   [1] 第 2 轮：\\n\\n + 引导语；
--   [2] 第 3 轮：\\n\\n + 引导语。
-- 若 JDBC 对「同文件多条语句」仍报 S1009，请分两次各执行下面一条 INSERT。

-- ---------------------------------------------------------------------------
-- 1/2 联网检索轮数（1～10 的十进制数字字符串）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO ten_runtime_setting (tenant_id, setting_key, value_text, created_at, updated_at)
SELECT
    t.id,
    'WEB_SEARCH_GROUNDING_MULTI_ROUND_COUNT',
    '3',
    UTC_TIMESTAMP(3),
    UTC_TIMESTAMP(3)
FROM sys_tenant t;

-- ---------------------------------------------------------------------------
-- 2/2 各轮问句后缀（JSON 数组；与上条轮数对齐，缺项按代码补空串）
-- value_text 内换行使用 JSON 的 \n 转义（CONCAT 中写作 \\n，落库为合法 JSON 文本）。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO ten_runtime_setting (tenant_id, setting_key, value_text, created_at, updated_at)
SELECT
    t.id,
    'WEB_SEARCH_GROUNDING_ROUND_SUFFIXES_JSON',
    CONCAT(
            '["",',
            '"\\n\\n',
            '【联网检索·第2轮】请在首轮基础上补充检索：优先不同域名或不同类型的站点（如新闻、百科、政府或机构、学术或数据类），避免与上一轮相同的网页链接；可换关键词表述或同义问法以扩大召回。',
            '","\\n\\n',
            '【联网检索·第3轮】请再检索一轮：侧重权威来源、一手数据、统计或官方文档，以及尽可能新的时效信息；同样避免重复已给出的 URL，尽量给出新的独立来源。',
            '"]'
    ),
    UTC_TIMESTAMP(3),
    UTC_TIMESTAMP(3)
FROM sys_tenant t;
