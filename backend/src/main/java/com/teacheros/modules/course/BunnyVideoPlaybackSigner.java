package com.teacheros.modules.course;

import com.teacheros.common.exception.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Signed, expiring HLS playback URLs for Bunny Stream, using Bunny CDN token
 * authentication with a DIRECTORY token (token_path = /{videoId}/) placed in
 * the URL path:
 *
 *   https://{cdnHost}/bcdn_token={token}&expires={epoch}&token_path=%2F{id}%2F/{id}/playlist.m3u8
 *
 * Because the token is part of the path, every relative segment URL in the
 * playlist inherits it, so the player needs no special handling. After
 * `expires` the CDN refuses the playlist and every segment: there is no
 * permanent URL. Nothing returned here is ever stored.
 *
 * token = Base64URL(SHA256(securityKey + token_path + expires + "token_path=" + token_path))
 *
 * This is Bunny's token authentication in its default ("Basic") mode - the
 * pull zone must NOT be switched to the "Advanced" (HMAC) mode.
 */
@Component
@ConditionalOnProperty(name = "app.video.provider", havingValue = "bunny")
public class BunnyVideoPlaybackSigner implements VideoPlaybackSigner {

    // Bunny video ids are GUIDs. Anything else could smuggle path characters
    // into the signed directory, so it is refused outright.
    private static final Pattern VIDEO_ID = Pattern.compile("^[0-9a-fA-F-]{8,64}$");

    private final BunnyStreamSettings settings;

    public BunnyVideoPlaybackSigner(BunnyStreamSettings settings) {
        this.settings = settings;
    }

    @Override
    public String signedPlaybackUrl(String provider, String ref, Duration ttl) {
        if (!BunnyVideoHostingClient.PROVIDER.equals(provider) || ref == null || !VIDEO_ID.matcher(ref).matches()) {
            // e.g. a lesson saved under the dev provider before go-live.
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "VIDEO_UNAVAILABLE",
                    "This video isn't available right now. Please tell your teacher.");
        }
        long expires = Instant.now().plus(ttl).getEpochSecond();
        return buildUrl(settings.cdnHost(), settings.tokenKey(), ref, expires);
    }

    static String buildUrl(String cdnHost, String tokenKey, String videoId, long expires) {
        String tokenPath = "/" + videoId + "/";
        String token = token(tokenKey, tokenPath, expires);
        return "https://" + cdnHost + "/bcdn_token=" + token + "&expires=" + expires + "&token_path="
                + URLEncoder.encode(tokenPath, StandardCharsets.UTF_8) + tokenPath + "playlist.m3u8";
    }

    static String token(String tokenKey, String tokenPath, long expires) {
        try {
            String hashable = tokenKey + tokenPath + expires + "token_path=" + tokenPath;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(hashable.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
