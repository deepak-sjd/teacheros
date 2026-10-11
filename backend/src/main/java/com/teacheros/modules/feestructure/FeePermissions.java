package com.teacheros.modules.feestructure;

/**
 * Single place for this module's access rules.
 *
 * TODO(Dev 11): once the FEES_EDIT permission exists, change EDIT to
 * "hasAuthority('FEES_EDIT')" (or whatever Dev 11's mechanism is). Every
 * write endpoint in this module references this constant, so that is a
 * one-line change. Until then EDIT is a role check that excludes TEACHER and
 * STAFF, who can still VIEW.
 */
public final class FeePermissions {

    public static final String VIEW = "hasAnyRole('OWNER','TEACHER','STAFF','ADMIN','ACCOUNTANT')";
    public static final String EDIT = "hasAnyRole('OWNER','ADMIN','ACCOUNTANT')";

    private FeePermissions() {
    }
}
