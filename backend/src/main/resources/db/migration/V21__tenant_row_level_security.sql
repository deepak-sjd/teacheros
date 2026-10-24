-- Grants for the non-superuser app role (skipped if the role doesn't exist, e.g. a plain dev DB).
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'teacheros_app') THEN
    EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO teacheros_app';
    EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO teacheros_app';
  END IF;
END $$;

-- app.tenant_id is set per connection from the JWT by TenantAwareDataSource.
-- app.bypass_rls = 'on' is set only inside SystemContext (schedulers, webhooks, first-login lookup).
-- Unset or empty tenant => NULL comparison => zero rows.
CREATE OR REPLACE FUNCTION apply_tenant_rls(tbl TEXT) RETURNS VOID
LANGUAGE plpgsql AS $fn$
BEGIN
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
  EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tbl);
  EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', tbl);
  EXECUTE format(
    'CREATE POLICY tenant_isolation ON %I USING ('
    || 'tenant_id = NULLIF(current_setting(''app.tenant_id'', true), '''')::uuid '
    || 'OR current_setting(''app.bypass_rls'', true) = ''on'')', tbl);
END $fn$;

SELECT apply_tenant_rls(t)
FROM unnest(ARRAY['students', 'fees', 'fee_payments', 'marks', 'student_documents']) AS t
WHERE to_regclass('public.' || t) IS NOT NULL;
