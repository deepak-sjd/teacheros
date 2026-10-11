package com.teacheros.modules.tenant;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The contract for module keys stored in tenant_config.enabled_modules.
 * A dev adding a new toggleable module appends its key to ALL in the same
 * PR as the feature. Unknown keys are rejected on PATCH so a typo can't
 * silently disable something.
 */
public final class TenantModules {

    private TenantModules() {
    }

    public static final Set<String> ALL = Set.copyOf(new LinkedHashSet<>(List.of(
            "ATTENDANCE", "FEES", "HOMEWORK", "TIMETABLE", "STUDY_MATERIAL", "TESTS",
            "ALERTS", "MESSAGES", "DOUBTS", "REPORTS", "ANALYTICS")));

    // Same set for both types today; SCHOOL-only modules get added by
    // whoever builds them.
    public static List<String> defaultsFor(TenantType type) {
        return List.of("ATTENDANCE", "FEES", "HOMEWORK", "TIMETABLE", "STUDY_MATERIAL", "TESTS",
                "ALERTS", "MESSAGES", "DOUBTS", "REPORTS", "ANALYTICS");
    }
}
