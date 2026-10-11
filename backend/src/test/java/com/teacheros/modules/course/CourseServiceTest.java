package com.teacheros.modules.course;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.course.dto.CourseDtos.SaveCourseRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    CourseRepository courseRepository;
    @Mock
    BatchRepository batchRepository;
    @Mock
    CurrentTenantProvider currentTenantProvider;
    @InjectMocks
    CourseService service;

    final UUID tenantA = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(currentTenantProvider.getCurrentTenantId()).thenReturn(tenantA);
    }

    private SaveCourseRequest request(UUID batchId, String video) {
        return new SaveCourseRequest("Maths 10", "desc", new BigDecimal("4999"), "Algebra\nGeometry", video, batchId,
                true);
    }

    @Test
    void create_withBatchFromAnotherTenant_isRejected() {
        UUID foreignBatch = UUID.randomUUID();
        when(batchRepository.findByIdAndTenantId(foreignBatch, tenantA)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.create(request(foreignBatch, null)));
        verify(courseRepository, never()).save(any());
    }

    @Test
    void create_withNonHttpVideoLink_isRejected() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.create(request(null, "javascript:alert(1)")));
        assertEquals("INVALID_VIDEO_URL", ex.getCode());
    }

    @Test
    void get_courseOfAnotherTenant_isNotFound() {
        UUID id = UUID.randomUUID();
        when(courseRepository.findByIdAndTenantId(id, tenantA)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.get(id));
        verify(courseRepository, never()).findById(any());
    }
}
