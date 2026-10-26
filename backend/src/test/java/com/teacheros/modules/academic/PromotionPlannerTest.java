package com.teacheros.modules.academic;

import com.teacheros.modules.academic.PromotionPlanner.Row;
import com.teacheros.modules.student.Student;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PromotionPlannerTest {

    private final UUID tenant = UUID.randomUUID();

    private SchoolClass cls(String name, int order, boolean active) {
        SchoolClass c = new SchoolClass(tenant, name, order);
        c.setId(UUID.randomUUID());
        c.setActive(active);
        return c;
    }

    private Section sec(SchoolClass c, String name) {
        Section s = new Section(tenant, c.getId(), name);
        s.setId(UUID.randomUUID());
        return s;
    }

    private Student stu(String name, Section s) {
        Student st = new Student(tenant, null, name, "8", "+919222222222");
        st.setId(UUID.randomUUID());
        st.setSectionId(s.getId());
        return st;
    }

    private List<Row> plan(List<Student> students, List<SchoolClass> classes, List<Section> sections,
            Map<UUID, UUID> mappings, Map<UUID, PromotionAction> actions) {
        return PromotionPlanner.plan(students,
                sections.stream().collect(Collectors.toMap(Section::getId, Function.identity())),
                classes.stream().collect(Collectors.toMap(SchoolClass::getId, Function.identity())),
                classes, mappings, actions);
    }

    @Test
    void promotesToTheSameNamedSectionInTheNextClass() {
        SchoolClass c8 = cls("Class 8", 8, true), c9 = cls("Class 9", 9, true);
        Section a8 = sec(c8, "A"), a9 = sec(c9, "A");
        Student asha = stu("Asha", a8);

        Row row = plan(List.of(asha), List.of(c8, c9), List.of(a8, a9), Map.of(), Map.of()).get(0);

        assertEquals(PromotionAction.PROMOTE, row.action());
        assertEquals(a9.getId(), row.toSection().getId());
        assertNull(row.issue());
    }

    @Test
    void aMissingSameNamedSectionIsAnIssueNotASilentGuess() {
        SchoolClass c8 = cls("Class 8", 8, true), c9 = cls("Class 9", 9, true);
        Section b8 = sec(c8, "B"), a9 = sec(c9, "A");

        Row row = plan(List.of(stu("Ravi", b8)), List.of(c8, c9), List.of(b8, a9), Map.of(), Map.of()).get(0);

        assertNull(row.toSection());
        assertTrue(row.issue().contains("no section \"B\""));
    }

    @Test
    void aSectionMappingFixesThatIssue() {
        SchoolClass c8 = cls("Class 8", 8, true), c9 = cls("Class 9", 9, true);
        Section b8 = sec(c8, "B"), a9 = sec(c9, "A");

        Row row = plan(List.of(stu("Ravi", b8)), List.of(c8, c9), List.of(b8, a9),
                Map.of(b8.getId(), a9.getId()), Map.of()).get(0);

        assertEquals(a9.getId(), row.toSection().getId());
        assertNull(row.issue());
    }

    @Test
    void theLastClassLeavesByDefault() {
        SchoolClass c9 = cls("Class 9", 9, true);
        Section a9 = sec(c9, "A");

        Row row = plan(List.of(stu("Meena", a9)), List.of(c9), List.of(a9), Map.of(), Map.of()).get(0);

        assertEquals(PromotionAction.LEAVE, row.action());
        assertNull(row.issue());
    }

    @Test
    void forcingPromoteInTheLastClassIsAnIssueWithoutAMapping() {
        SchoolClass c9 = cls("Class 9", 9, true);
        Section a9 = sec(c9, "A");
        Student meena = stu("Meena", a9);

        Row row = plan(List.of(meena), List.of(c9), List.of(a9), Map.of(),
                Map.of(meena.getId(), PromotionAction.PROMOTE)).get(0);

        assertTrue(row.issue().contains("last class"));
    }

    @Test
    void detainedStudentsStayInTheSameSection() {
        SchoolClass c8 = cls("Class 8", 8, true), c9 = cls("Class 9", 9, true);
        Section a8 = sec(c8, "A"), a9 = sec(c9, "A");
        Student asha = stu("Asha", a8);

        Row row = plan(List.of(asha), List.of(c8, c9), List.of(a8, a9), Map.of(),
                Map.of(asha.getId(), PromotionAction.DETAIN)).get(0);

        assertEquals(PromotionAction.DETAIN, row.action());
        assertEquals(a8.getId(), row.toSection().getId());
    }

    @Test
    void anExplicitLeaveOverridesPromotion() {
        SchoolClass c8 = cls("Class 8", 8, true), c9 = cls("Class 9", 9, true);
        Section a8 = sec(c8, "A"), a9 = sec(c9, "A");
        Student asha = stu("Asha", a8);

        Row row = plan(List.of(asha), List.of(c8, c9), List.of(a8, a9), Map.of(),
                Map.of(asha.getId(), PromotionAction.LEAVE)).get(0);

        assertEquals(PromotionAction.LEAVE, row.action());
        assertNull(row.toSection());
    }

    @Test
    void inactiveClassesAndSectionsAreSkippedAsTargets() {
        SchoolClass c8 = cls("Class 8", 8, true), c9 = cls("Class 9", 9, false), c10 = cls("Class 10", 10, true);
        Section a8 = sec(c8, "A"), a9 = sec(c9, "A"), a10 = sec(c10, "A");

        Row row = plan(List.of(stu("Asha", a8)), List.of(c8, c9, c10), List.of(a8, a9, a10), Map.of(), Map.of())
                .get(0);

        assertEquals(a10.getId(), row.toSection().getId());
    }

    @Test
    void rowsComeBackOrderedByClassThenSectionThenName() {
        SchoolClass c8 = cls("Class 8", 8, true), c9 = cls("Class 9", 9, true);
        Section a8 = sec(c8, "A"), b8 = sec(c8, "B"), a9 = sec(c9, "A");

        List<Row> rows = plan(List.of(stu("Zed", a9), stu("Bina", b8), stu("Asha", a8), stu("Amit", a8)),
                List.of(c8, c9), List.of(a8, b8, a9), Map.of(), Map.of());

        assertEquals(List.of("Amit", "Asha", "Bina", "Zed"),
                rows.stream().map(r -> r.student().getFullName()).toList());
    }
}
