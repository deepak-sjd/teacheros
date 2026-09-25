-- Optional guardian email, alongside the existing guardian_phone. Needed
-- so low-attendance/fee alerts can be emailed, not just recorded in-app.
-- Nullable - most schools will only have the phone number on file.
ALTER TABLE students ADD COLUMN guardian_email VARCHAR(255);
