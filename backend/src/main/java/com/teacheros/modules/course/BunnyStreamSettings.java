package com.teacheros.modules.course;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Bunny Stream configuration, read once. Only exists when
 * app.video.provider=bunny, and fails at startup (not on the first student
 * request) if anything required is blank.
 */
@Component
@ConditionalOnProperty(name = "app.video.provider", havingValue = "bunny")
public class BunnyStreamSettings {

    private final String libraryId;
    private final String apiKey;
    private final String cdnHost;
    private final String tokenKey;
    private final String apiBase;
    private final String tusEndpoint;

    public BunnyStreamSettings(
            @Value("${app.video.bunny.library-id:}") String libraryId,
            @Value("${app.video.bunny.api-key:}") String apiKey,
            @Value("${app.video.bunny.cdn-host:}") String cdnHost,
            @Value("${app.video.bunny.token-key:}") String tokenKey,
            @Value("${app.video.bunny.api-base:https://video.bunnycdn.com}") String apiBase,
            @Value("${app.video.bunny.tus-endpoint:https://video.bunnycdn.com/tusupload}") String tusEndpoint) {
        require(libraryId, "BUNNY_LIBRARY_ID");
        require(apiKey, "BUNNY_API_KEY");
        require(cdnHost, "BUNNY_CDN_HOST");
        require(tokenKey, "BUNNY_TOKEN_KEY");
        this.libraryId = libraryId.trim();
        this.apiKey = apiKey.trim();
        // Tolerate a pasted scheme or trailing slash.
        this.cdnHost = cdnHost.trim().replaceFirst("^https?://", "").replaceAll("/+$", "");
        this.tokenKey = tokenKey.trim();
        this.apiBase = apiBase.trim().replaceAll("/+$", "");
        this.tusEndpoint = tusEndpoint.trim();
    }

    private static void require(String value, String envName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "app.video.provider=bunny but " + envName + " is not set. Set it, or use VIDEO_PROVIDER=log for local dev.");
        }
    }

    public String libraryId() {
        return libraryId;
    }

    public String apiKey() {
        return apiKey;
    }

    public String cdnHost() {
        return cdnHost;
    }

    public String tokenKey() {
        return tokenKey;
    }

    public String apiBase() {
        return apiBase;
    }

    public String tusEndpoint() {
        return tusEndpoint;
    }
}
