package com.teacheros.modules.course;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VideoPlaybackPolicyTest {

    private final VideoPlaybackPolicy policy = new VideoPlaybackPolicy(30, 480);

    @Test
    void unknownLength_usesTheBase() {
        assertEquals(Duration.ofMinutes(30), policy.ttlFor(null));
        assertEquals(Duration.ofMinutes(30), policy.ttlFor(0));
    }

    @Test
    void shortLecture_getsLengthPlusBuffer_butNeverLessThanBase() {
        assertEquals(Duration.ofMinutes(40), policy.ttlFor(600)); // 10 + 30
        assertEquals(Duration.ofMinutes(31), policy.ttlFor(30)); // ceil(0.5)=1 minute + 30
    }

    @Test
    void longLecture_outlastsTheWholeViewing() {
        assertEquals(Duration.ofMinutes(90), policy.ttlFor(3600)); // 60 + 30
    }

    @Test
    void neverExceedsTheMax() {
        assertEquals(Duration.ofMinutes(480), policy.ttlFor(100_000));
    }

    @Test
    void aMaxBelowTheBase_isRaisedToTheBase() {
        assertEquals(Duration.ofMinutes(30), new VideoPlaybackPolicy(30, 10).ttlFor(null));
    }
}
