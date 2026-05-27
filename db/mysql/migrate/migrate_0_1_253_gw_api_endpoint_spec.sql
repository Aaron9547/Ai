-- 接口目录入参/出参说明 JSON（对接文档导出）；0.1.253-SNAPSHOT；已建库须手工执行

ALTER TABLE gw_api_endpoint
  ADD COLUMN request_spec_json LONGTEXT NULL COMMENT '入参 JSON 数组 [{name,in,type,required,description}]' AFTER interface_kind,
  ADD COLUMN response_spec_json LONGTEXT NULL COMMENT '出参 JSON 数组 [{name,type,description}]' AFTER request_spec_json;

UPDATE gw_api_endpoint SET request_spec_json = '[]', response_spec_json = '[{"name":"status","type":"string","description":"ok 表示服务可用"}]'
WHERE path_pattern = '/partner/v1/health' AND http_method = 'GET' AND request_spec_json IS NULL;

UPDATE gw_api_endpoint SET request_spec_json = '[]', response_spec_json = '[{"name":"status","type":"string","description":"ok 表示对话模块可达"}]'
WHERE path_pattern = '/partner/v1/chat/probe' AND http_method = 'GET' AND request_spec_json IS NULL;
