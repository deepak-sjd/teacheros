package com.teacheros.modules.permission;

/**
 * Every permission a controller may reference
 * via @PreAuthorize("@perm.has('X')").
 * Names must match role_permission_defaults.permission (V100) exactly - see
 * PreAuthorizePermissionNamesTest and RolePermissionRepositoryTest, which fail
 * if a controller or a seed row uses a name that isn't here.
 *
 * The whole set is defined up front even though only some modules are migrated
 * in the first PR, so the remaining module PRs are annotation-only changes and
 * need no further seed migration.
 */
public enum Permission {
    FEES_VIEW,
    FEES_EDIT,
    MARKS_VIEW_ALL,
    MARKS_EDIT,
    ATTENDANCE_MARK,
    REPORTS_VIEW,
    ANALYTICS_VIEW,
    STUDENTS_VIEW,
    STUDENTS_EDIT,
    BATCHES_VIEW,
    BATCHES_EDIT,
    HOMEWORK_EDIT,
    TIMETABLE_EDIT,
    STUDY_MATERIAL_EDIT,
    ALERTS_SEND,
    CONTENT_GENERATE,
    LEADS_MANAGE
}
