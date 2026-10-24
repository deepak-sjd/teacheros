SELECT apply_tenant_rls(t)
FROM unnest(ARRAY['students', 'fees', 'fee_payments', 'marks', 'student_documents']) AS t
WHERE to_regclass('public.' || t) IS NOT NULL;
