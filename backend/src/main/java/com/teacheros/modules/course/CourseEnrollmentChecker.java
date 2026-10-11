package com.teacheros.modules.course;

import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * THE single place that decides "may this student learn from this course".
 * Today's rule: the course is published and linked to a batch (course.batchId)
 * that the student belongs to. If courses later get a real enrollment table
 * (e.g. paid public enrollment), change only this class - every student-side
 * endpoint goes through it.
 */
@Component
public class CourseEnrollmentChecker {

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;

    public CourseEnrollmentChecker(StudentRepository studentRepository, CourseRepository courseRepository) {
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
    }

    public boolean canLearn(UUID tenantId, UUID studentId, Course course) {
        return studentBatch(tenantId, studentId)
                .map(batchId -> matches(course, batchId))
                .orElse(false);
    }

    public List<Course> coursesFor(UUID tenantId, UUID studentId) {
        return studentBatch(tenantId, studentId)
                .map(batchId -> courseRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                        .filter(c -> matches(c, batchId))
                        .toList())
                .orElse(List.of());
    }

    private java.util.Optional<UUID> studentBatch(UUID tenantId, UUID studentId) {
        return studentRepository.findByIdAndTenantId(studentId, tenantId)
                .map(Student::getBatchId);
    }

    private boolean matches(Course course, UUID studentBatchId) {
        return course.isPublished() && course.getBatchId() != null && course.getBatchId().equals(studentBatchId);
    }
}
