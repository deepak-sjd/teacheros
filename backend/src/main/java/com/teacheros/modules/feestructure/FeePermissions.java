package com.teacheros.modules.feestructure;

/**
 * Single place for this module's access rules, now driven by the per-tenant
 * permission matrix (role_permissions) instead of hard-coded roles.
 *
 * VIEW = FEES_VIEW. Defaults (V100) match the old role list exactly: OWNER,
 * ADMIN, TEACHER, STAFF, ACCOUNTANT.
 *
 * EDIT = FEES_EDIT and FEES_CONFIGURE. Fee-structure writes were never open to
 * TEACHER/STAFF, but those roles hold FEES_EDIT (they record payments through
 * FeeController), so FEES_EDIT alone would have widened access. Requiring
 * FEES_CONFIGURE as well (V101: OWNER, ADMIN, ACCOUNTANT) keeps the default
 * behaviour identical, and removing FEES_EDIT from a role in the matrix still
 * stops its fee writes everywhere.
 *
 * Both are compile-time constants so they can be used in @PreAuthorize.
 */
public final class FeePermissions {

    public static final String VIEW = "@perm.has('FEES_VIEW')";
    public static final String EDIT = "@perm.has('FEES_EDIT') and @perm.has('FEES_CONFIGURE')";

    private FeePermissions() {
    }
}
