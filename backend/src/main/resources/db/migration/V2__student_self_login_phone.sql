-- V2: Student self-login phone number.
--
-- Parents already log in via their guardian_phone on the students table -
-- no schema change needed there, since "which students belong to this
-- parent" is derived at query time (students.guardian_phone = current
-- user's phone), the same philosophy already used for attendance
-- percentage (never stored, always computed).
--
-- Students need an equivalent column of their own for student self-login,
-- since guardian_phone is explicitly the *guardian's* number, not the
-- student's.

ALTER TABLE students ADD COLUMN phone_number VARCHAR(20);

-- A phone number can only self-login as one student per tenant. Partial
-- index (not a NOT NULL column) because most students won't have their own
-- phone in early adoption - this stays optional.
CREATE UNIQUE INDEX uq_students_tenant_phone ON students(tenant_id, phone_number)
    WHERE phone_number IS NOT NULL;
