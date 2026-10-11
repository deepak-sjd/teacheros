package com.teacheros.modules.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the explicit decision: ACCOUNTANT/COUNSELLOR are NOT tenant-wide staff.
 */
class StaffRolesTest {

    @Test
    void legacyStaffRolesAreTenantWideStaff() {
        assertTrue(StaffRoles.isTenantWideStaff("OWNER"));
        assertTrue(StaffRoles.isTenantWideStaff("TEACHER"));
        assertTrue(StaffRoles.isTenantWideStaff("STAFF"));
        assertTrue(StaffRoles.isTenantWideStaff("ADMIN"));
    }

    @Test
    void newRolesParentStudentAndGarbageAreNot() {
        assertFalse(StaffRoles.isTenantWideStaff("ACCOUNTANT"));
        assertFalse(StaffRoles.isTenantWideStaff("COUNSELLOR"));
        assertFalse(StaffRoles.isTenantWideStaff("PARENT"));
        assertFalse(StaffRoles.isTenantWideStaff("STUDENT"));
        assertFalse(StaffRoles.isTenantWideStaff("SUPERUSER"));
        assertFalse(StaffRoles.isTenantWideStaff(null));
    }
}
