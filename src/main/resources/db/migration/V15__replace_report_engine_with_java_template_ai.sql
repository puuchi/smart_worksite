ALTER TABLE report_config
  MODIFY COLUMN template_id BIGINT NULL COMMENT 'Selected DOCX report template ID used by Java template report engine';

ALTER TABLE report
  MODIFY COLUMN engine_type VARCHAR(64) NOT NULL DEFAULT 'JAVA_TEMPLATE_AI' COMMENT 'Report generation engine type';
