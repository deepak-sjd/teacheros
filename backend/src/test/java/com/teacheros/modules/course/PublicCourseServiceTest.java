package com.teacheros.modules.course;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.storage.StorageService;
import com.teacheros.modules.tenant.PublicTenantResolver;
import com.teacheros.modules.tenant.Tenant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicCourseServiceTest {

    @Mock
    PublicTenantResolver tenantResolver;
    @Mock
    CourseRepository courseRepository;
    @Mock
    StorageService storageService;
    @InjectMocks
    PublicCourseService service;

    final UUID tenantId = UUID.randomUUID();

    private Tenant tenant() {
        Tenant t = new Tenant("School A");
        t.setId(tenantId);
        return t;
    }

    @Test
    void list_onlyAsksForPublishedCourses() {
        when(tenantResolver.resolve("school-a")).thenReturn(tenant());
        Course published = new Course(tenantId, "Maths", new BigDecimal("100"));
        published.setPublished(true);
        when(courseRepository.findByTenantIdAndPublishedTrueOrderByCreatedAtDesc(tenantId))
                .thenReturn(List.of(published));

        assertEquals(1, service.list("school-a").size());
        verify(courseRepository, never()).findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Test
    void get_unpublishedCourse_isNotFound() {
        UUID id = UUID.randomUUID();
        when(tenantResolver.resolve("school-a")).thenReturn(tenant());
        when(courseRepository.findByIdAndTenantIdAndPublishedTrue(id, tenantId)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.get("school-a", id));
        assertEquals("COURSE_NOT_FOUND", ex.getCode());
        verify(courseRepository, never()).findByIdAndTenantId(id, tenantId);
    }
}
