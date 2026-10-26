package com.teacheros.modules.demo;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemoSlotRepository extends JpaRepository<DemoSlot, UUID> {

    Optional<DemoSlot> findByIdAndTenantId(UUID id, UUID tenantId);

    // Row lock for the booking path: seats are counted while no one else can book
    // this slot.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DemoSlot s WHERE s.id = :id AND s.tenantId = :tenantId")
    Optional<DemoSlot> lockByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    // Staff view: every slot in a window, cancelled ones included.
    List<DemoSlot> findByTenantIdAndStartsAtBetweenOrderByStartsAtAsc(UUID tenantId, Instant from, Instant to);

    // Public view: future, not cancelled.
    List<DemoSlot> findByTenantIdAndCancelledFalseAndStartsAtGreaterThanOrderByStartsAtAsc(UUID tenantId,
            Instant after);
}
