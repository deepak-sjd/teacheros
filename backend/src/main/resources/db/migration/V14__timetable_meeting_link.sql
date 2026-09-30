-- Optional video-call link for a recurring weekly slot (Zoom/Google Meet/
-- Microsoft Teams/other). Most coaching batches reuse the same link every
-- week, so this lives on the slot itself rather than on each individual
-- class_session - nullable, since plenty of batches are in-person only.
ALTER TABLE timetable_slots ADD COLUMN meeting_link VARCHAR(500);
ALTER TABLE timetable_slots ADD COLUMN meeting_platform VARCHAR(20);
