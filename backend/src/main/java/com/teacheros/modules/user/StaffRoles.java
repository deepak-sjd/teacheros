package com.teacheros.modules.user;

import java.util.EnumSet;
import java.util.Set;

/**
 * The ONE definition of "staff who can see any student/batch in their tenant"
 * for the access guards (StudentAccessGuard, BatchAccessGuard) and
 * MessageService. Replaces three copy-pasted isStaffRole switches.
 *
 * DECISION: ACCOUNTANT and COUNSELLOR are deliberately NOT tenant-wide staff.
 * Student-scoped endpoints guarded by StudentAccessGuard also serve marks,
 * rank, messages, doubts and the report card; counting an accountant as
 * tenant-wide staff would expose all of that. Those roles reach data only
 * through endpoints gated by an explicit permission (@perm.has(...)).
 * Anything that is not tenant-wide staff falls through to the guards'
 * PARENT/STUDENT checks and gets a 404.
 */
public final class StaffRoles {

    private static final Set<Role> TENANT_WIDE_STAFF = EnumSet.of(Role.OWNER, Role.TEACHER, Role.STAFF, Role.ADMIN);

    private StaffRoles() {
    }

    public static boolean isTenantWideStaff(String role) {
        if (role == null) {
            return false;
        }
        try {
            return TENANT_WIDE_STAFF.contains(Role.valueOf(role));
        } catch (IllegalArgumentException e) {
            return false; // unknown role string in a token: not staff
        }
    }
}
