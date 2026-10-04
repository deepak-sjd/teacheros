-- V17: Weak Topic Detection + Study Plan (Dev6 task).
--
-- One row per student - a plan is "the student's current plan", not a
-- growing history (unlike doubt_messages, which is a conversation log by
-- nature). Regenerating overwrites the existing row for that student
-- rather than inserting a new one - see StudyPlanService.
--
-- Weak-subject detection itself is NOT stored here - same "never store a
-- derived number" rule ReportService/AnalyticsService already follow.
-- Only the generated plan TEXT is persisted, since that's the one part
-- worth paying an AI-provider call for; the weak-subject list it was
-- based on is cheap to recompute from `marks` on every read and is
-- returned alongside the stored plan text rather than cached with it, so
-- it never goes stale against marks entered after the plan was generated.
CREATE TABLE study_plans (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    student_id      UUID NOT NULL REFERENCES students(id),
    plan_text       TEXT NOT NULL,
    generated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_study_plans_student UNIQUE (tenant_id, student_id)
);
