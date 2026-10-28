-- V101: FEES_CONFIGURE (Dev 11, staff and permission administration).
--
-- The fee-structure endpoints (heads, structures, instalment plans, discounts,
-- rules) have always been writable by OWNER/ADMIN/ACCOUNTANT only, while the
-- fee-ledger endpoints (create fee, record payment) are writable by every
-- staff role. One permission cannot express both, so structure writes now
-- need BOTH FEES_EDIT and FEES_CONFIGURE (see FeePermissions.EDIT).
--
-- Defaults reproduce today's behaviour exactly: OWNER, ADMIN and ACCOUNTANT
-- get FEES_CONFIGURE, TEACHER and STAFF do not. Nothing could edit
-- role_permissions before this release, so seeding existing tenants from the
-- defaults cannot overwrite anyone's customisation.
INSERT INTO role_permission_defaults (role, permission) VALUES
    ('OWNER', 'FEES_CONFIGURE'),
    ('ADMIN', 'FEES_CONFIGURE'),
    ('ACCOUNTANT', 'FEES_CONFIGURE');

INSERT INTO role_permissions (tenant_id, role, permission)
SELECT t.id, d.role, d.permission
FROM tenants t
CROSS JOIN role_permission_defaults d
WHERE d.permission = 'FEES_CONFIGURE'
ON CONFLICT DO NOTHING;
