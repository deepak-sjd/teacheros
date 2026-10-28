package com.teacheros.modules.course;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * How long a signed playback URL lives. It has to outlast the viewing
 * session, because the CDN checks the same expiry on every HLS segment: a
 * flat 15 minutes would cut a 60-minute lecture off part-way through.
 *
 * ttl = max(base, lecture length + 30 min), never more than the max.
 * Unknown length falls back to base.
 */
@Component
public class VideoPlaybackPolicy {

    private static final int BUFFER_MINUTES = 30;

    private final int baseMinutes;
    private final int maxMinutes;

    public VideoPlaybackPolicy(
            @Value("${app.video.playback-ttl-minutes:30}") int baseMinutes,
            @Value("${app.video.playback-max-ttl-minutes:480}") int maxMinutes) {
        this.baseMinutes = Math.max(1, baseMinutes);
        this.maxMinutes = Math.max(this.baseMinutes, maxMinutes);
    }

    public Duration ttlFor(Integer durationSeconds) {
        int lectureMinutes = durationSeconds == null ? 0 : (int) Math.ceil(durationSeconds / 60.0);
        int wanted = lectureMinutes == 0 ? baseMinutes : Math.max(baseMinutes, lectureMinutes + BUFFER_MINUTES);
        return Duration.ofMinutes(Math.min(wanted, maxMinutes));
    }
}
