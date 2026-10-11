package com.teacheros.modules.course;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lessons")
@Getter
@Setter
@NoArgsConstructor
public class Lesson {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    // Denormalised from the chapter so "all lessons of a course" and the
    // enrollment check don't need a join.
    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "chapter_id", nullable = false)
    private UUID chapterId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LessonType type;

    // Provider name + the provider's own reference (e.g. a Mux playback id).
    // Never a playable URL - see VideoPlaybackSigner. Staff-only fields.
    @Column(name = "video_provider", length = 30)
    private String videoProvider;

    @Column(name = "video_ref")
    private String videoRef;

    @Column(name = "notes_text", columnDefinition = "TEXT")
    private String notesText;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Lesson(UUID tenantId, UUID courseId, UUID chapterId, String title, LessonType type, int position) {
        this.tenantId = tenantId;
        this.courseId = courseId;
        this.chapterId = chapterId;
        this.title = title;
        this.type = type;
        this.position = position;
    }
}
