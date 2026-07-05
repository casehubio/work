-- Template versioning (#180): version on template, templateVersion on work_item

ALTER TABLE work_item_template ADD COLUMN version BIGINT NOT NULL DEFAULT 1;

ALTER TABLE work_item ADD COLUMN template_version BIGINT;
