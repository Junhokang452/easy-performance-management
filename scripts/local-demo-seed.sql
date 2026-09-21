-- Synthetic accounts and Core Master read-model fixtures for the isolated local demo only.
BEGIN;
DO $$ BEGIN
  IF current_database() <> 'performance_demo' THEN
    RAISE EXCEPTION 'Demo fixtures require the isolated performance_demo database';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM user_account WHERE email='dev-hr-admin@performance.dev') THEN
    RAISE EXCEPTION 'Start the local demo application and wait for dev accounts first';
  END IF;
END $$;

INSERT INTO rm_org_unit (id,tenant_id,code,name,org_type,source_version,synced_at)
VALUES ('019ed001-0000-7000-8000-000000000001','00000000-0000-0000-0000-000000000001','PRODUCT','제품개발팀','TEAM',1,now()),
       ('019ed001-0000-7000-8000-000000000002','00000000-0000-0000-0000-000000000001','PEOPLE','피플팀','TEAM',1,now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO rm_employee (id,tenant_id,employee_no,name,status,org_unit_id,employment_type,source_version,synced_at)
VALUES
 ('019ed002-0000-7000-8000-000000000001','00000000-0000-0000-0000-000000000001','DEMO-001','김나리','ACTIVE','019ed001-0000-7000-8000-000000000001','REGULAR',1,now()),
 ('019ed002-0000-7000-8000-000000000002','00000000-0000-0000-0000-000000000001','DEMO-002','박민서','ACTIVE','019ed001-0000-7000-8000-000000000001','REGULAR',1,now()),
 ('019ed002-0000-7000-8000-000000000003','00000000-0000-0000-0000-000000000001','DEMO-003','이서준','ACTIVE','019ed001-0000-7000-8000-000000000001','REGULAR',1,now()),
 ('019ed002-0000-7000-8000-000000000004','00000000-0000-0000-0000-000000000001','DEMO-004','정하린','ACTIVE','019ed001-0000-7000-8000-000000000002','REGULAR',1,now()),
 ('019ed002-0000-7000-8000-000000000005','00000000-0000-0000-0000-000000000001','DEMO-005','운영담당','ACTIVE','019ed001-0000-7000-8000-000000000002','REGULAR',1,now()),
 ('019ed002-0000-7000-8000-000000000006','00000000-0000-0000-0000-000000000001','DEMO-006','오지우','ACTIVE','019ed001-0000-7000-8000-000000000001','REGULAR',1,now())
ON CONFLICT (id) DO NOTHING;

UPDATE user_account SET employee_id='019ed002-0000-7000-8000-000000000001',display_name='김나리' WHERE email='dev-employee@performance.dev' AND tenant_id='00000000-0000-0000-0000-000000000001';
UPDATE user_account SET employee_id='019ed002-0000-7000-8000-000000000002',display_name='박민서' WHERE email='dev-manager@performance.dev' AND tenant_id='00000000-0000-0000-0000-000000000001';
UPDATE user_account SET employee_id='019ed002-0000-7000-8000-000000000003',display_name='이서준' WHERE email='dev-director@performance.dev' AND tenant_id='00000000-0000-0000-0000-000000000001';
UPDATE user_account SET employee_id='019ed002-0000-7000-8000-000000000004',display_name='정하린' WHERE email='dev-hr-admin@performance.dev' AND tenant_id='00000000-0000-0000-0000-000000000001';
UPDATE user_account SET employee_id='019ed002-0000-7000-8000-000000000005',display_name='운영담당' WHERE email='dev-super-admin@performance.dev' AND tenant_id='00000000-0000-0000-0000-000000000001';
INSERT INTO user_account(id,tenant_id,email,password_hash,display_name,role,employee_id,active)
SELECT '019ed003-0000-7000-8000-000000000006',tenant_id,'dev-colleague@performance.dev',password_hash,'오지우','EMPLOYEE','019ed002-0000-7000-8000-000000000006',true
FROM user_account WHERE email='dev-employee@performance.dev' AND tenant_id='00000000-0000-0000-0000-000000000001'
ON CONFLICT(id) DO NOTHING;
-- A second synthetic tenant enables negative isolation tests without touching any external plane.
INSERT INTO rm_employee (id,tenant_id,employee_no,name,status,employment_type,source_version,synced_at)
VALUES ('019ed004-0000-7000-8000-000000000001','00000000-0000-0000-0000-000000000002','OTHER-001','다른회사 담당','ACTIVE','REGULAR',1,now())
ON CONFLICT(id) DO NOTHING;
INSERT INTO user_account(id,tenant_id,email,password_hash,display_name,role,employee_id,active)
SELECT '019ed004-0000-7000-8000-000000000002','00000000-0000-0000-0000-000000000002','dev-isolation@performance.dev',password_hash,'다른회사 담당','HR_ADMIN','019ed004-0000-7000-8000-000000000001',true
FROM user_account WHERE email='dev-hr-admin@performance.dev' AND tenant_id='00000000-0000-0000-0000-000000000001'
ON CONFLICT(id) DO NOTHING;
-- Synthetic assignment history exercises group lookups and weighted transfer participation.
INSERT INTO rm_assignment(id,tenant_id,employee_id,org_unit_id,position_code,grade_code,job_code,effective_from,effective_to,source_version,synced_at)
VALUES
('019ed005-0000-7000-8000-000000000001','00000000-0000-0000-0000-000000000001','019ed002-0000-7000-8000-000000000001','019ed001-0000-7000-8000-000000000001','MEMBER','G3','SERVICE','2026-01-01','2026-06-30',1,now()),
('019ed005-0000-7000-8000-000000000002','00000000-0000-0000-0000-000000000001','019ed002-0000-7000-8000-000000000001','019ed001-0000-7000-8000-000000000002','MEMBER','G3','PEOPLE_OPS','2026-07-01',NULL,1,now()),
('019ed005-0000-7000-8000-000000000003','00000000-0000-0000-0000-000000000001','019ed002-0000-7000-8000-000000000002','019ed001-0000-7000-8000-000000000001','TEAM_LEAD','G5','SERVICE','2026-01-01',NULL,1,now()),
('019ed005-0000-7000-8000-000000000004','00000000-0000-0000-0000-000000000001','019ed002-0000-7000-8000-000000000003','019ed001-0000-7000-8000-000000000001','DIRECTOR','G7','SERVICE','2026-01-01',NULL,1,now()),
('019ed005-0000-7000-8000-000000000005','00000000-0000-0000-0000-000000000001','019ed002-0000-7000-8000-000000000006','019ed001-0000-7000-8000-000000000001','MEMBER','G2','SERVICE','2026-01-01',NULL,1,now())
ON CONFLICT(id) DO NOTHING;
COMMIT;
