package com.teacheros.modules.tenant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    // S3 object key, not a URL - same pattern as StudyMaterial.objectKey
    // (see StorageService's javadoc). A display URL is signed on demand by
    // TenantService, never stored.
    @Column(name = "logo_object_key")
    private String logoObjectKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Tenant(String name) {
        this.name = name;
    }
}
