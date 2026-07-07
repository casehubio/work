-- WorkItemTemplate: add type_paths column
ALTER TABLE work_item_template ADD COLUMN type_paths TEXT;

-- WorkItemTemplate: drop category
ALTER TABLE work_item_template DROP COLUMN category;

-- WorkItem: create types join table
CREATE TABLE work_item_type (
    work_item_id UUID NOT NULL REFERENCES work_item(id),
    path         VARCHAR(500) NOT NULL,
    CONSTRAINT uq_work_item_type UNIQUE (work_item_id, path)
);
CREATE INDEX idx_work_item_type_path ON work_item_type(path);

-- WorkItem: drop category
ALTER TABLE work_item DROP COLUMN category;
