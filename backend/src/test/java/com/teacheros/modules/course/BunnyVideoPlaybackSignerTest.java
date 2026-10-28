package com.teacheros.modules.course;

import com.teacheros.common.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BunnyVideoPlaybackSignerTest {

    private static final String HOST = "vz-abc.b-cdn.net";
    private static final String KEY = "super-secret-token-key";
    private static final String VIDEO = "0b7c1f3e-5a2d-4e8b-9c11-6a2f7d3e9b10";

    private final BunnyVideoPlaybackSigner signer = new BunnyVideoPlaybackSigner(
            new BunnyStreamSettings("123", "api-key", HOST, KEY, "https://video.bunnycdn.com",
                    "https://video.bunnycdn.com/tusupload"));

    /** Independent re-computation of Bunny's documented token, not the production code path. */
    private static String expectedToken(String key, String path, long expires) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((key + path + expires + "token_path=" + path).getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    @Test
    void url_hasDirectoryTokenInThePath_andTheDocumentedHash() throws Exception {
        String url = BunnyVideoPlaybackSigner.buildUrl(HOST, KEY, VIDEO, 1_700_000_000L);

        String token = expectedToken(KEY, "/" + VIDEO + "/", 1_700_000_000L);
        assertEquals("https://" + HOST + "/bcdn_token=" + token + "&expires=1700000000&token_path=%2F" + VIDEO
                + "%2F/" + VIDEO + "/playlist.m3u8", url);
    }

    @Test
    void url_neverContainsTheSecretKey() {
        assertFalse(BunnyVideoPlaybackSigner.buildUrl(HOST, KEY, VIDEO, 1_700_000_000L).contains(KEY));
    }

    @Test
    void differentVideoOrExpiryGivesADifferentToken() {
        String base = BunnyVideoPlaybackSigner.token(KEY, "/" + VIDEO + "/", 1_700_000_000L);
        assertNotEquals(base, BunnyVideoPlaybackSigner.token(KEY, "/other-video-id/", 1_700_000_000L));
        assertNotEquals(base, BunnyVideoPlaybackSigner.token(KEY, "/" + VIDEO + "/", 1_700_000_001L));
        assertNotEquals(base, BunnyVideoPlaybackSigner.token("another-key", "/" + VIDEO + "/", 1_700_000_000L));
    }

    @Test
    void signedUrl_expiresAfterTheRequestedLifetime() {
        long before = Instant.now().plus(Duration.ofMinutes(15)).getEpochSecond();
        String url = signer.signedPlaybackUrl("bunny", VIDEO, Duration.ofMinutes(15));
        long after = Instant.now().plus(Duration.ofMinutes(15)).getEpochSecond();

        Matcher m = Pattern.compile("expires=(\\d+)").matcher(url);
        assertTrue(m.find());
        long expires = Long.parseLong(m.group(1));
        assertTrue(expires >= before && expires <= after, "expires should be now + ttl");
    }

    @Test
    void otherProvider_isRefusedWithAFriendlyError() {
        ApiException ex = assertThrows(ApiException.class,
                () -> signer.signedPlaybackUrl("mux", VIDEO, Duration.ofMinutes(15)));
        assertEquals("VIDEO_UNAVAILABLE", ex.getCode());
    }

    @Test
    void referenceWithPathCharacters_isRefused() {
        for (String bad : new String[] { "../etc", VIDEO + "/../x", "abc def", "", null }) {
            assertThrows(ApiException.class, () -> signer.signedPlaybackUrl("bunny", bad, Duration.ofMinutes(15)),
                    "ref should be refused: " + bad);
        }
    }

    @Test
    void settings_failFastWhenAnythingRequiredIsBlank() {
        assertThrows(IllegalStateException.class, () -> new BunnyStreamSettings("123", "k", "", KEY,
                "https://video.bunnycdn.com", "https://video.bunnycdn.com/tusupload"));
        assertThrows(IllegalStateException.class, () -> new BunnyStreamSettings("123", "k", HOST, " ",
                "https://video.bunnycdn.com", "https://video.bunnycdn.com/tusupload"));
    }

    @Test
    void settings_tolerateAPastedSchemeAndTrailingSlash() {
        BunnyStreamSettings s = new BunnyStreamSettings("123", "k", "https://" + HOST + "/", KEY,
                "https://video.bunnycdn.com/", "https://video.bunnycdn.com/tusupload");
        assertEquals(HOST, s.cdnHost());
        assertEquals("https://video.bunnycdn.com", s.apiBase());
    }
}
