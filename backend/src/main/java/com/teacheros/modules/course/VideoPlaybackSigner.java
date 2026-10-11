package com.teacheros.modules.course;

import java.time.Duration;

/**
 * Turns a stored (provider, reference) pair into a short-lived playable URL.
 * Swappable like SmsSender/AiTutorClient via app.video.provider - "log" is
 * the dev default (a placeholder URL, nothing real is signed). A real
 * provider (Mux, Cloudflare Stream, Vimeo...) is one more implementation
 * class with @ConditionalOnProperty; no caller changes. We never host or
 * proxy video ourselves, and a URL returned here is never stored.
 */
public interface VideoPlaybackSigner {

    String signedPlaybackUrl(String provider, String ref, Duration ttl);
}
