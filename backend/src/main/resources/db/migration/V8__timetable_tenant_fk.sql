-- V7: Add the missing tenant_id foreign key on timetable_slots.
--
-- Every other tenant-owned table references tenants(id) - this one was
-- missed. Flyway migrations are immutable once applied, so this is a
-- follow-up ALTER rather than an edit to V5. If your next available
-- migration number isn't 7 (e.g. you've already added a V7 for something
-- else), just rename this file to match - the number only needs to be
-- higher than whatever's already applied.

ALTER TABLE timetable_slots
    ADD CONSTRAINT fk_timetable_slots_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenants(id);
