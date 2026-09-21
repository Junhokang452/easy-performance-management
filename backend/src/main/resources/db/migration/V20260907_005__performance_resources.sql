CREATE TABLE IF NOT EXISTS evaluation_resource_catalog (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('PERFORMANCE', 'COMPETENCY')),
    category VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    definition TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    copied_from_id UUID REFERENCES evaluation_resource_catalog(id),
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID
);
CREATE INDEX IF NOT EXISTS ix_resource_catalog_tenant_kind_order ON evaluation_resource_catalog(tenant_id, kind, active, display_order);

CREATE TABLE IF NOT EXISTS evaluation_resource_catalog_level (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    catalog_id UUID NOT NULL REFERENCES evaluation_resource_catalog(id) ON DELETE CASCADE,
    code VARCHAR(40) NOT NULL,
    label VARCHAR(100) NOT NULL,
    min_value NUMERIC(12,4),
    max_value NUMERIC(12,4),
    description TEXT,
    display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_resource_catalog_level UNIQUE (tenant_id, catalog_id, code),
    CONSTRAINT ck_resource_catalog_level_range CHECK (min_value IS NULL OR max_value IS NULL OR min_value <= max_value)
);
CREATE INDEX IF NOT EXISTS ix_resource_catalog_level_tenant_catalog ON evaluation_resource_catalog_level(tenant_id, catalog_id, display_order);

CREATE TABLE IF NOT EXISTS evaluation_resource_catalog_assignment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    catalog_id UUID NOT NULL REFERENCES evaluation_resource_catalog(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL CHECK (type IN ('JOB', 'DEPARTMENT')),
    reference_key VARCHAR(120) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_resource_catalog_assignment UNIQUE (tenant_id, catalog_id, type, reference_key)
);
CREATE INDEX IF NOT EXISTS ix_resource_catalog_assignment_tenant_ref ON evaluation_resource_catalog_assignment(tenant_id, type, reference_key);

CREATE TABLE IF NOT EXISTS department_goal (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    goal_year INTEGER NOT NULL CHECK (goal_year BETWEEN 2000 AND 2200),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    department_id UUID NOT NULL,
    catalog_id UUID REFERENCES evaluation_resource_catalog(id),
    title VARCHAR(200) NOT NULL,
    definition TEXT NOT NULL,
    weight NUMERIC(7,4) NOT NULL CHECK (weight BETWEEN 0 AND 100),
    target_level VARCHAR(40) NOT NULL,
    unit VARCHAR(40) NOT NULL,
    actual_value NUMERIC(18,4),
    achievement_rate NUMERIC(8,4) CHECK (achievement_rate IS NULL OR achievement_rate >= 0),
    actual_note TEXT,
    copied_from_id UUID REFERENCES department_goal(id),
    transferred_from_id UUID REFERENCES department_goal(id),
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT ck_department_goal_period CHECK (period_end >= period_start)
);
CREATE INDEX IF NOT EXISTS ix_department_goal_tenant_dept_year ON department_goal(tenant_id, department_id, goal_year DESC);
CREATE INDEX IF NOT EXISTS ix_department_goal_tenant_catalog ON department_goal(tenant_id, catalog_id);

CREATE TABLE IF NOT EXISTS performance_task (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    owner_employee_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'DISCARDED')),
    progress_mode VARCHAR(20) NOT NULL CHECK (progress_mode IN ('CHECKLIST', 'ACTUAL')),
    actual_progress NUMERIC(7,4) NOT NULL DEFAULT 0 CHECK (actual_progress BETWEEN 0 AND 100),
    department_goal_id UUID REFERENCES department_goal(id),
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT ck_performance_task_period CHECK (period_end >= period_start)
);
CREATE INDEX IF NOT EXISTS ix_performance_task_tenant_owner_status ON performance_task(tenant_id, owner_employee_id, status);
CREATE INDEX IF NOT EXISTS ix_performance_task_tenant_goal ON performance_task(tenant_id, department_goal_id);

CREATE TABLE IF NOT EXISTS performance_task_stakeholder (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    task_id UUID NOT NULL REFERENCES performance_task(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'MANAGER', 'COLLABORATOR')),
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_performance_task_stakeholder UNIQUE (tenant_id, task_id, employee_id)
);
CREATE INDEX IF NOT EXISTS ix_task_stakeholder_tenant_employee ON performance_task_stakeholder(tenant_id, employee_id, task_id);

CREATE TABLE IF NOT EXISTS performance_task_checklist (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    task_id UUID NOT NULL REFERENCES performance_task(id) ON DELETE CASCADE,
    text VARCHAR(500) NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    completed_at TIMESTAMP,
    completed_by UUID,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID
);
CREATE INDEX IF NOT EXISTS ix_task_checklist_tenant_task ON performance_task_checklist(tenant_id, task_id, display_order);

CREATE TABLE IF NOT EXISTS performance_task_activity (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    task_id UUID NOT NULL REFERENCES performance_task(id) ON DELETE CASCADE,
    author_employee_id UUID NOT NULL,
    type VARCHAR(40) NOT NULL,
    message TEXT NOT NULL,
    progress_percent NUMERIC(7,4),
    from_status VARCHAR(20) CHECK (from_status IS NULL OR from_status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'DISCARDED')),
    to_status VARCHAR(20) CHECK (to_status IS NULL OR to_status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'DISCARDED')),
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID
);
CREATE INDEX IF NOT EXISTS ix_task_activity_tenant_task_created ON performance_task_activity(tenant_id, task_id, created_at);

CREATE TABLE IF NOT EXISTS performance_task_label (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    owner_employee_id UUID NOT NULL,
    name VARCHAR(80) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_task_label_owner_name UNIQUE (tenant_id, owner_employee_id, name)
);
CREATE INDEX IF NOT EXISTS ix_task_label_tenant_owner ON performance_task_label(tenant_id, owner_employee_id, name);

CREATE TABLE IF NOT EXISTS performance_task_label_link (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    task_id UUID NOT NULL REFERENCES performance_task(id) ON DELETE CASCADE,
    label_id UUID NOT NULL REFERENCES performance_task_label(id) ON DELETE CASCADE,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_task_label_link UNIQUE (tenant_id, task_id, label_id)
);
CREATE INDEX IF NOT EXISTS ix_task_label_link_tenant_task ON performance_task_label_link(tenant_id, task_id);

CREATE TABLE IF NOT EXISTS performance_task_feedback (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    task_id UUID NOT NULL REFERENCES performance_task(id) ON DELETE CASCADE,
    from_employee_id UUID NOT NULL,
    to_employee_id UUID NOT NULL,
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    message TEXT NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID
);
CREATE INDEX IF NOT EXISTS ix_task_feedback_tenant_task ON performance_task_feedback(tenant_id, task_id, created_at);

CREATE TABLE IF NOT EXISTS performance_task_attachment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    task_id UUID NOT NULL REFERENCES performance_task(id) ON DELETE CASCADE,
    uploaded_by UUID NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size BETWEEN 1 AND 10485760),
    file_content BYTEA NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID
);
CREATE INDEX IF NOT EXISTS ix_task_attachment_tenant_task ON performance_task_attachment(tenant_id, task_id, created_at);

CREATE TABLE IF NOT EXISTS interview_record (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    author_employee_id UUID NOT NULL,
    subject_employee_id UUID NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    summary TEXT NOT NULL,
    key_issues TEXT,
    requests TEXT,
    follow_up TEXT,
    subject_visible BOOLEAN NOT NULL DEFAULT FALSE,
    references_visible BOOLEAN NOT NULL DEFAULT FALSE,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID
);
CREATE INDEX IF NOT EXISTS ix_interview_tenant_author_occurred ON interview_record(tenant_id, author_employee_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS ix_interview_tenant_subject_occurred ON interview_record(tenant_id, subject_employee_id, occurred_at DESC);

CREATE TABLE IF NOT EXISTS interview_reference (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    interview_id UUID NOT NULL REFERENCES interview_record(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_interview_reference UNIQUE (tenant_id, interview_id, employee_id)
);
CREATE INDEX IF NOT EXISTS ix_interview_reference_tenant_employee ON interview_reference(tenant_id, employee_id, interview_id);

CREATE TABLE IF NOT EXISTS interview_audit_event (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    interview_id UUID NOT NULL REFERENCES interview_record(id) ON DELETE CASCADE,
    actor_employee_id UUID NOT NULL,
    action VARCHAR(30) NOT NULL CHECK (action IN ('CREATED', 'UPDATED', 'VISIBILITY_CHANGED')),
    subject_visible BOOLEAN NOT NULL,
    references_visible BOOLEAN NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID
);
CREATE INDEX IF NOT EXISTS ix_interview_audit_tenant_interview ON interview_audit_event(tenant_id, interview_id, created_at);
