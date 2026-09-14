# TeacherOS — Web

A production-style web UI for the TeacherOS backend, covering four roles from one codebase: **Owner/Teacher/Admin/Staff**, **Parent**, and **Student**. Built with React + TypeScript + Vite + Tailwind v4, talking directly to the Spring Boot API you already have running.

## Setup

```bash
npm install
npm run dev
```

Runs at `http://localhost:5173`. Make sure your backend is running at `http://localhost:8080` (or update `VITE_API_URL` in `.env`).

## Roles and what each one sees

The backend currently only issues **OWNER** and **TEACHER** accounts at signup (see `Role.java` comment in the backend) — STAFF, STUDENT, PARENT, and ADMIN exist in the data model but have no signup flow yet. This UI is built to support all six roles once the backend adds signup for them, with two practical workarounds for now:

- **Owner / Teacher / Admin / Staff** — full operational UI: batches, students, attendance marking, fee ledger and payments. This is the primary, fully-wired experience today.
- **Parent** — a "link my child" flow: enter the student ID given by the school once (stored locally in the browser), then view that child's attendance % and fee ledger. Supports linking more than one child. This is a front-end workaround — the backend doesn't yet have a real parent↔student relationship, so there's no access control preventing a parent from entering someone else's student ID. **Before shipping this to real parents, add a backend endpoint that verifies the parent is actually linked to that student** (e.g. a `parent_student_links` table + an authorization check), and this UI's `MyChildren.tsx` should be updated to call it instead of a raw `GET /students/{id}`.
- **Student** — same pattern, single self-link, view own attendance and fees.

## Structure

```
src/
  lib/          API client (JWT + refresh handling), auth context, types, formatters
  components/   Layout/sidebar shell, ledger table, form primitives, StudentProfile (shared view)
  pages/
    auth/       Phone + OTP login
    teacher/    Overview, Batches, Students, StudentDetail, Attendance, Fees
    parent/     MyChildren
    student/    MyAttendance, MyFees
```

## What's next (flagged for future work)

- Backend: real parent↔student and (optionally) student↔user linking, so Parent/Student roles don't rely on entering a raw student ID.
- Backend: role-based authorization on endpoints (currently any authenticated user can call any endpoint — fine for MVP, not for production with real Parent/Student accounts).
- Frontend: batch detail page (roster + per-batch attendance history), student edit/deactivate, fee reminders/export, pagination on long lists (currently pages fetch up to 100 rows).
- Design: dark mode is not implemented; only one density is designed (comfortable, not compact).
