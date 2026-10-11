package com.teacheros.modules.announcement;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnnouncementVisibilityTest {

    final UUID tenant = UUID.randomUUID();
    final UUID batchA = UUID.randomUUID();
    final UUID batchB = UUID.randomUUID();

    private Announcement of(AnnouncementAudience audience, UUID batchId, String grade) {
        return new Announcement(tenant, "t", "b", audience, batchId, grade, UUID.randomUUID(), "Teacher");
    }

    @Test
    void allIsVisibleToEveryone_evenWithNoLinkedStudents() {
        assertThat(AnnouncementService.isVisibleTo(of(AnnouncementAudience.ALL, null, null), Set.of(), Set.of()))
                .isTrue();
    }

    @Test
    void batchIsVisibleOnlyToThatBatch() {
        Announcement a = of(AnnouncementAudience.BATCH, batchA, null);
        assertThat(AnnouncementService.isVisibleTo(a, Set.of(batchA), Set.of())).isTrue();
        assertThat(AnnouncementService.isVisibleTo(a, Set.of(batchB), Set.of())).isFalse();
        assertThat(AnnouncementService.isVisibleTo(a, Set.of(), Set.of())).isFalse();
    }

    @Test
    void classMatchesGradeCaseInsensitively() {
        Announcement a = of(AnnouncementAudience.CLASS, null, " 10th ");
        assertThat(AnnouncementService.isVisibleTo(a, Set.of(), Set.of("10th"))).isTrue();
        assertThat(AnnouncementService.isVisibleTo(a, Set.of(), Set.of("9th"))).isFalse();
    }
}
