package com.teacheros.modules.permission;

/**
 * Every permission a controller may reference via
 * {@code @PreAuthorize("@perm.has('X')")}. Names must match
 * role_permission_defaults.permission exactly - PreAuthorizePermissionNamesTest
 * and RolePermissionRepositoryTest fail if a controller or a seed row uses a
 * name that isn't here.
 *
 * group/description are shown in the Settings -> Permissions matrix; they are
 * display text only and never used for an access decision.
 */
public enum Permission {
    FEES_VIEW("Fees", "View fee ledgers, pending fees, receipts and finance reports"),
    FEES_EDIT("Fees", "Create fees and record payments"),
    FEES_CONFIGURE("Fees", "Manage fee heads, structures, instalment plans, discounts and rules (also needs Edit)"),
    MARKS_VIEW_ALL("Marks", "See every student's marks and the class ranking"),
    MARKS_EDIT("Marks", "Enter and change marks"),
    ATTENDANCE_MARK("Attendance", "Mark attendance and show the check-in QR"),
    REPORTS_VIEW("Reports", "Open the reports page and download report PDFs"),
    ANALYTICS_VIEW("Reports", "See the analytics dashboards"),
    STUDENTS_VIEW("Students and batches", "See the student and batch lists"),
    STUDENTS_EDIT("Students and batches", "Add, edit and import students"),
    BATCHES_VIEW("Students and batches", "See the batch list"),
    BATCHES_EDIT("Students and batches", "Create and edit batches"),
    HOMEWORK_EDIT("Classroom", "Create and edit homework and grade submissions"),
    TIMETABLE_EDIT("Classroom", "Edit the timetable and meeting links"),
    STUDY_MATERIAL_EDIT("Classroom", "Upload and delete study material"),
    CONTENT_GENERATE("Classroom", "Use the AI content generator"),
    ALERTS_SEND("Communication", "Send attendance and fee alerts to parents"),
    LEADS_MANAGE("Admissions", "Manage enquiries and leads");

    private final String group;
    private final String description;

    Permission(String group, String description) {
        this.group = group;
        this.description = description;
    }

    public String group() {
        return group;
    }

    public String description() {
        return description;
    }
}
