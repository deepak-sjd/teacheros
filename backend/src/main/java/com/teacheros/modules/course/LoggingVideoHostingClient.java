package com.teacheros.modules.course;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Dev default (app.video.provider=log, or unset). Lets the whole upload ->
 * ready -> attach flow be exercised without a provider account: every video
 * is "ready" immediately and no file is sent anywhere. ProductionConfigValidator
 * refuses this provider under the prod profile.
 */
@Component
@ConditionalOnProperty(name = "app.video.provider", havingValue = "log", matchIfMissing = true)
public class LoggingVideoHostingClient implements VideoHostingClient {

    private static final Logger log = LoggerFactory.getLogger(LoggingVideoHostingClient.class);

    @Override
    public String providerName() {
        return "log";
    }

    @Override
    public String createVideo(String title) {
        String ref = "dev-" + UUID.randomUUID();
        log.info("[DEV VIDEO] created title={} ref={} (nothing sent to a provider)", title, ref);
        return ref;
    }

    @Override
    public UploadCredentials signUpload(String ref, Duration ttl) {
        return null; // nothing to upload in dev
    }

    @Override
    public VideoInfo getVideo(String ref) {
        return new VideoInfo(State.READY, 0, null, null);
    }

    @Override
    public void deleteVideo(String ref) {
        log.info("[DEV VIDEO] deleted ref={}", ref);
    }
}
