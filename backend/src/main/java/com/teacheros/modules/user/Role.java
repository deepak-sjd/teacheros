package com.teacheros.modules.user;

/**
 * OWNER/TEACHER: created via explicit signup (displayName + tenantName
 * required on first login).
 *
 * PARENT/STUDENT: auto-provisioned on first login, based on whether the
 * phone number matches a student's guardian_phone or phone_number - see
 * AuthService.provisionAccountForPhone. No separate signup form.
 *
 * STAFF, ADMIN: exist in the model, no signup flow yet - reserved for a
 * later phase (multi-staff tenants, platform administration).
 */
public enum Role {
    OWNER,
    TEACHER,
    STAFF,
    STUDENT,
    PARENT,
    ADMIN
}
