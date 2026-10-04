-- Optional email on a user's own account (separate from Student.guardian_email,
-- which is the guardian contact used for alerts - this is the staff/owner's
-- own login-account profile field). Nullable: login stays phone+OTP only,
-- this is contact info, not a second login method.
ALTER TABLE users ADD COLUMN email VARCHAR(255);

-- Tenant branding logo, mirroring how StudyMaterial stores an S3 object key
-- rather than a URL (see StorageService's javadoc on why - signed URLs are
-- generated on demand, never stored as a permanent link).
ALTER TABLE tenants ADD COLUMN logo_object_key VARCHAR(500);
