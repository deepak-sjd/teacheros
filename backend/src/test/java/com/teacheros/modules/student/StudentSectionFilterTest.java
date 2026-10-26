package com.teacheros.modules.student;

import com.teacheros.modules.academic.SchoolClass;
import com.teacheros.modules.academic.SchoolClassRepository;
import com.teacheros.modules.academic.Section;
import com.teacheros.modules.academic.SectionRepository;
import com.teacheros.modules.batch.Batch;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.tenant.Tenant;
import com.teacheros.modules.tenant.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The optional sectionId filter narrows student lists to one section, never
 * crosses tenants, and leaves the original queries alone when it is absent.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class StudentSectionFilterTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    TenantRepository tenants;
    @Autowired
    StudentRepository students;
    @Autowired
    SchoolClassRepository classes;
    @Autowired
    SectionRepository sections;
    @Autowired
    BatchRepository batches;

    private static final PageRequest BY_NAME = PageRequest.of(0, 50, Sort.by("fullName", "id"));

    private Student student(UUID tenantId, String name, UUID batchId, UUID sectionId) {
        Student s = new Student(tenantId, batchId, name, "8", "+919222222222");
        s.setSectionId(sectionId);
        return students.save(s);
    }

    private List<String> names(org.springframework.data.domain.Page<Student> page) {
        return page.getContent().stream().map(Student::getFullName).toList();
    }

    @Test
    void listInSectionReturnsOnlyThatSectionInNameOrder() {
        UUID a = tenants.save(new Tenant("A")).getId();
        SchoolClass c = classes.save(new SchoolClass(a, "Class 8", 8));
        Section secA = sections.save(new Section(a, c.getId(), "A"));
        Section secB = sections.save(new Section(a, c.getId(), "B"));
        student(a, "Zed", null, secA.getId());
        student(a, "Asha", null, secA.getId());
        student(a, "Bina", null, secB.getId());
        student(a, "Unplaced", null, null);

        assertThat(names(students.listInSection(a, null, secA.getId(), BY_NAME))).containsExactly("Asha", "Zed");
    }

    @Test
    void listInSectionCanAlsoNarrowByBatch() {
        UUID a = tenants.save(new Tenant("A")).getId();
        SchoolClass c = classes.save(new SchoolClass(a, "Class 8", 8));
        Section secA = sections.save(new Section(a, c.getId(), "A"));
        Batch b1 = batches.save(new Batch(a, "Batch 1", null, null));
        Batch b2 = batches.save(new Batch(a, "Batch 2", null, null));
        student(a, "Asha", b1.getId(), secA.getId());
        student(a, "Ravi", b2.getId(), secA.getId());

        assertThat(names(students.listInSection(a, b1.getId(), secA.getId(), BY_NAME))).containsExactly("Asha");
    }

    @Test
    void searchInSectionOnlyMatchesInsideTheSection() {
        UUID a = tenants.save(new Tenant("A")).getId();
        SchoolClass c = classes.save(new SchoolClass(a, "Class 8", 8));
        Section secA = sections.save(new Section(a, c.getId(), "A"));
        Section secB = sections.save(new Section(a, c.getId(), "B"));
        student(a, "Asha Rao", null, secA.getId());
        student(a, "Asha Kumar", null, secB.getId());

        assertThat(names(students.searchInSection(a, null, secA.getId(), "%asha%", PageRequest.of(0, 50))))
                .containsExactly("Asha Rao");
    }

    @Test
    void anotherTenantGetsNothingEvenWithTheRightSectionId() {
        UUID a = tenants.save(new Tenant("A")).getId();
        UUID b = tenants.save(new Tenant("B")).getId();
        SchoolClass c = classes.save(new SchoolClass(a, "Class 8", 8));
        Section secA = sections.save(new Section(a, c.getId(), "A"));
        student(a, "Asha", null, secA.getId());

        assertThat(students.listInSection(b, null, secA.getId(), BY_NAME).getContent()).isEmpty();
        assertThat(students.searchInSection(b, null, secA.getId(), "%asha%", PageRequest.of(0, 50)).getContent())
                .isEmpty();
    }

    @Test
    void withoutASectionTheOriginalListingIsUnchanged() {
        UUID a = tenants.save(new Tenant("A")).getId();
        SchoolClass c = classes.save(new SchoolClass(a, "Class 8", 8));
        Section secA = sections.save(new Section(a, c.getId(), "A"));
        student(a, "Placed", null, secA.getId());
        student(a, "Unplaced", null, null);

        assertThat(names(students.findByTenantIdAndActiveTrue(a, BY_NAME))).containsExactly("Placed", "Unplaced");
    }
}
