package com.teacheros.modules.user;

/**
 * OWNER/TEACHER: created via explicit signup (displayName + tenantName
 * required on first login).
 *
 * PARENT/STUDENT: auto-provisioned on first login, based on whether the
 * phone number matches a student's guardian_phone or phone_number - see
 * AuthService.provisionAccountForPhone. No separate signup form.
 *
 * STAFF, ADMIN, ACCOUNTANT, COUNSELLOR: staff roles. No signup flow yet -
 * pre-created by an owner/admin (Dev 11 Task 2). What each may do is decided
 * by the role_permissions table (see modules.permission), not by this enum.
 * Only OWNER/TEACHER/STAFF/ADMIN count as tenant-wide staff for the access
 * guards - see StaffRoles for why ACCOUNTANT/COUNSELLOR do not.
 *
 * users.role is VARCHAR(30), so adding a value here needs no schema change.
 */
public enum Role {
    OWNER,
    TEACHER,
    STAFF,
    STUDENT,
    PARENT,
    ADMIN,
    ACCOUNTANT,
    COUNSELLOR
}
