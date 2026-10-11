-- V100: Roles and permissions (Dev 11, Task 1).
--
-- role_permission_defaults is global (not tenant-owned): the template copied
-- into role_permissions for every tenant. role_permissions is tenant-owned
-- and is what PermissionService actually checks. No row = denied.
--
-- OWNER is short-circuited to "always allowed" in PermissionService, so an
-- edited matrix can never lock the owner out; OWNER rows exist only so the
-- (later) permissions matrix view has something to show.

CREATE TABLE role_permission_defaults (
    role        VARCHAR(30) NOT NULL,
    permission  VARCHAR(50) NOT NULL,
    PRIMARY KEY (role, permission)
);

-- The four existing staff roles keep today's behaviour: everything their
-- hard-coded hasAnyRole('OWNER','TEACHER','STAFF','ADMIN') lists allowed.
INSERT INTO role_permission_defaults (role, permission)
SELECT r.role, p.permission
FROM (VALUES ('OWNER'), ('ADMIN'), ('TEACHER'), ('STAFF')) AS r(role)
CROSS JOIN (VALUES
    ('FEES_VIEW'), ('FEES_EDIT'),
    ('MARKS_VIEW_ALL'), ('MARKS_EDIT'),
    ('ATTENDANCE_MARK'),
    ('REPORTS_VIEW'), ('ANALYTICS_VIEW'),
    ('STUDENTS_VIEW'), ('STUDENTS_EDIT'),
    ('BATCHES_VIEW'), ('BATCHES_EDIT'),
    ('HOMEWORK_EDIT'), ('TIMETABLE_EDIT'), ('STUDY_MATERIAL_EDIT'),
    ('ALERTS_SEND'), ('CONTENT_GENERATE')
) AS p(permission);

-- New capability, new roles only. TEACHER deliberately does not get it.
INSERT INTO role_permission_defaults (role, permission) VALUES
    ('OWNER', 'LEADS_MANAGE'),
    ('ADMIN', 'LEADS_MANAGE'),
    ('STAFF', 'LEADS_MANAGE'),
    ('COUNSELLOR', 'LEADS_MANAGE'),
    ('COUNSELLOR', 'BATCHES_VIEW'),
    -- Accountant: fees + reports, plus the student/batch lookups the Fees and
    -- Reports pages call. No marks, homework, attendance or content.
    ('ACCOUNTANT', 'FEES_VIEW'),
    ('ACCOUNTANT', 'FEES_EDIT'),
    ('ACCOUNTANT', 'REPORTS_VIEW'),
    ('ACCOUNTANT', 'STUDENTS_VIEW'),
    ('ACCOUNTANT', 'BATCHES_VIEW');

CREATE TABLE role_permissions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    role        VARCHAR(30) NOT NULL,
    permission  VARCHAR(50) NOT NULL,
    CONSTRAINT uq_role_permissions UNIQUE (tenant_id, role, permission)
);

-- Seed every tenant that already exists. New tenants are seeded by
-- AuthService.provisionNewTeacherAccount via PermissionService#seedDefaults.
INSERT INTO role_permissions (tenant_id, role, permission)
SELECT t.id, d.role, d.permission
FROM tenants t
CROSS JOIN role_permission_defaults d;
