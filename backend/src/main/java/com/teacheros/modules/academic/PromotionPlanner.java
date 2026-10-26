package com.teacheros.modules.academic;

import com.teacheros.modules.student.Student;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Works out where each student goes at year end. No database access:
 * everything it needs is passed in, so the rules are unit-testable on their
 * own and preview and apply are guaranteed to use identical logic.
 */
final class PromotionPlanner {

    /** toSection/toClass are null for LEAVE and for rows with an issue. */
    record Row(Student student, Section fromSection, SchoolClass fromClass, Section toSection, SchoolClass toClass,
            PromotionAction action, String note, String issue) {
    }

    private static final Comparator<SchoolClass> CLASS_ORDER = Comparator
            .comparingInt(SchoolClass::getDisplayOrder).thenComparing(SchoolClass::getName);

    private PromotionPlanner() {
    }

    /**
     * @param sectionsById    every section of the tenant (a student's current
     *                        section may be inactive)
     * @param classes         every class of the tenant
     * @param sectionMappings fromSectionId -> toSectionId, already validated as
     *                        active and in-tenant
     * @param actionOverrides studentId -> action chosen by staff
     */
    static List<Row> plan(List<Student> students, Map<UUID, Section> sectionsById, Map<UUID, SchoolClass> classesById,
            List<SchoolClass> classes, Map<UUID, UUID> sectionMappings, Map<UUID, PromotionAction> actionOverrides) {

        List<SchoolClass> activeInOrder = classes.stream().filter(SchoolClass::isActive).sorted(CLASS_ORDER).toList();

        Map<UUID, Map<String, Section>> activeSectionsByClass = new HashMap<>();
        for (Section s : sectionsById.values()) {
            if (s.isActive()) {
                activeSectionsByClass.computeIfAbsent(s.getClassId(), k -> new HashMap<>()).put(key(s.getName()), s);
            }
        }

        List<Row> rows = new ArrayList<>();
        for (Student student : students) {
            Section from = sectionsById.get(student.getSectionId());
            SchoolClass fromClass = classesById.get(from.getClassId());
            SchoolClass next = activeInOrder.stream().filter(c -> CLASS_ORDER.compare(c, fromClass) > 0)
                    .findFirst().orElse(null);
            UUID mapped = sectionMappings.get(from.getId());

            PromotionAction action = actionOverrides.getOrDefault(student.getId(),
                    next != null || mapped != null ? PromotionAction.PROMOTE : PromotionAction.LEAVE);

            rows.add(switch (action) {
                case LEAVE -> new Row(student, from, fromClass, null, null, action, "Leaving the school", null);
                case DETAIN -> detain(student, from, fromClass);
                case PROMOTE -> promote(student, from, fromClass, next, mapped, sectionsById, classesById,
                        activeSectionsByClass);
            });
        }

        rows.sort(Comparator
                .comparing((Row r) -> r.fromClass(), CLASS_ORDER)
                .thenComparing(r -> r.fromSection().getName())
                .thenComparing(r -> r.student().getFullName(), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private static Row detain(Student student, Section from, SchoolClass fromClass) {
        if (!from.isActive()) {
            return new Row(student, from, fromClass, null, null, PromotionAction.DETAIN, null,
                    "Section " + label(fromClass, from) + " is inactive, so the student cannot stay in it.");
        }
        return new Row(student, from, fromClass, from, fromClass, PromotionAction.DETAIN,
                "Detained - stays in " + label(fromClass, from), null);
    }

    private static Row promote(Student student, Section from, SchoolClass fromClass, SchoolClass next, UUID mapped,
            Map<UUID, Section> sectionsById, Map<UUID, SchoolClass> classesById,
            Map<UUID, Map<String, Section>> activeSectionsByClass) {

        Section target = null;
        if (mapped != null) {
            target = sectionsById.get(mapped);
        } else if (next != null) {
            target = activeSectionsByClass.getOrDefault(next.getId(), Map.of()).get(key(from.getName()));
        }

        if (target == null) {
            String issue = next == null && mapped == null
                    ? fromClass.getName() + " is the last class, so there is nowhere to promote to."
                    : next.getName() + " has no section \"" + from.getName() + "\". Choose a section for "
                            + label(fromClass, from) + ".";
            return new Row(student, from, fromClass, null, null, PromotionAction.PROMOTE, null, issue);
        }
        SchoolClass toClass = classesById.get(target.getClassId());
        return new Row(student, from, fromClass, target, toClass, PromotionAction.PROMOTE,
                label(fromClass, from) + " to " + label(toClass, target), null);
    }

    private static String label(SchoolClass schoolClass, Section section) {
        return schoolClass.getName() + " " + section.getName();
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
