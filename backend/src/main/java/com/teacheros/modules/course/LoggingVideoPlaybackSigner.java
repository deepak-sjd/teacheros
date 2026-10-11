package com.teacheros.modules.course;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Dev default - returns an obviously fake URL instead of signing a real one.
 */
@Component
@ConditionalOnProperty(name = "app.video.provider", havingValue = "log", matchIfMissing = true)
public class LoggingVideoPlaybackSigner implements VideoPlaybackSigner {

    private static final Logger log = LoggerFactory.getLogger(LoggingVideoPlaybackSigner.class);

    @Override
    public String signedPlaybackUrl(String provider, String ref, Duration ttl) {
        log.info("[DEV VIDEO] provider={} ref={} (no real signing - dev provider)", provider, ref);
        return "https://video.invalid/dev/" + provider + "/" + ref + "?expires="
                + Instant.now().plus(ttl).getEpochSecond();
    }
}
