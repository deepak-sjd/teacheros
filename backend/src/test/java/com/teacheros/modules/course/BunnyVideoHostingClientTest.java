package com.teacheros.modules.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Talks to a throwaway local HTTP server, so no network or Bunny account is needed. */
class BunnyVideoHostingClientTest {

    private HttpServer server;
    private BunnyVideoHostingClient client;
    private final AtomicReference<String> lastAccessKey = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastDeletePath = new AtomicReference<>();

    private void reply(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            ex.getResponseBody().write(bytes);
        }
        ex.close();
    }

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/library/123/videos", ex -> {
            lastAccessKey.set(ex.getRequestHeaders().getFirst("AccessKey"));
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            if (method.equals("POST") && path.equals("/library/123/videos")) {
                lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                reply(ex, 200, "{\"guid\":\"new-guid\"}");
            } else if (method.equals("GET") && path.endsWith("/v-ready")) {
                reply(ex, 200, "{\"status\":4,\"storageSize\":999,\"length\":120}");
            } else if (method.equals("GET") && path.endsWith("/v-proc")) {
                reply(ex, 200, "{\"status\":3,\"storageSize\":0,\"length\":0}");
            } else if (method.equals("GET") && path.endsWith("/v-created")) {
                reply(ex, 200, "{\"status\":0,\"storageSize\":0,\"length\":0}");
            } else if (method.equals("GET") && path.endsWith("/v-fail")) {
                reply(ex, 200, "{\"status\":5,\"storageSize\":0,\"length\":0}");
            } else if (method.equals("GET") && path.endsWith("/v-uploadfail")) {
                reply(ex, 200, "{\"status\":6,\"storageSize\":0,\"length\":0}");
            } else if (method.equals("DELETE") && path.endsWith("/gone")) {
                lastDeletePath.set(path);
                reply(ex, 404, "");
            } else if (method.equals("DELETE")) {
                lastDeletePath.set(path);
                reply(ex, 200, "{\"success\":true}");
            } else {
                reply(ex, 404, "");
            }
        });
        server.createContext("/library/500/videos", ex -> reply(ex, 500, "boom"));
        server.start();

        client = clientFor("123");
    }

    private BunnyVideoHostingClient clientFor(String libraryId) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new BunnyVideoHostingClient(new BunnyStreamSettings(libraryId, "the-api-key", "vz-abc.b-cdn.net",
                "token-key", base, "https://video.bunnycdn.com/tusupload"), new ObjectMapper());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void createVideo_sendsTheApiKeyAndTitle_andReturnsTheGuid() {
        assertEquals("new-guid", client.createVideo("Lecture 1"));
        assertEquals("the-api-key", lastAccessKey.get());
        assertTrue(lastBody.get().contains("Lecture 1"));
    }

    @Test
    void getVideo_mapsEveryBunnyStatus() {
        assertEquals(VideoHostingClient.State.READY, client.getVideo("v-ready").state());
        assertEquals(VideoHostingClient.State.PROCESSING, client.getVideo("v-proc").state());
        assertEquals(VideoHostingClient.State.WAITING_FOR_FILE, client.getVideo("v-created").state());
        assertEquals(VideoHostingClient.State.FAILED, client.getVideo("v-fail").state());
        assertEquals(VideoHostingClient.State.FAILED, client.getVideo("v-uploadfail").state());
    }

    @Test
    void getVideo_ready_carriesSizeAndDuration() {
        VideoHostingClient.VideoInfo info = client.getVideo("v-ready");
        assertEquals(999L, info.sizeBytes());
        assertEquals(120, info.durationSeconds());
        assertNull(info.failureReason());
    }

    @Test
    void getVideo_unknownVideo_isReportedAsFailedNotAnError() {
        VideoHostingClient.VideoInfo info = client.getVideo("never-existed");
        assertEquals(VideoHostingClient.State.FAILED, info.state());
        assertTrue(info.failureReason().contains("no longer exists"));
    }

    @Test
    void deleteVideo_isIdempotent_a404IsNotAnError() {
        client.deleteVideo("v-ready");
        client.deleteVideo("gone");
        assertEquals("/library/123/videos/gone", lastDeletePath.get());
    }

    @Test
    void providerServerError_becomesAVideoProviderException() {
        assertThrows(VideoProviderException.class, () -> clientFor("500").createVideo("x"));
    }

    @Test
    void unreachableProvider_becomesAVideoProviderException() {
        BunnyVideoHostingClient dead = new BunnyVideoHostingClient(new BunnyStreamSettings("123", "k", "h", "t",
                "http://127.0.0.1:1", "https://video.bunnycdn.com/tusupload"), new ObjectMapper());
        assertThrows(VideoProviderException.class, () -> dead.createVideo("x"));
    }

    @Test
    void signUpload_followsBunnysDocumentedSignature_andNeverExposesTheApiKey() {
        VideoHostingClient.UploadCredentials c = client.signUpload("vid-1", Duration.ofHours(1));

        assertEquals("123", c.libraryId());
        assertEquals("vid-1", c.videoId());
        assertEquals(BunnyVideoHostingClient.uploadSignature("123", "the-api-key", c.authorizationExpire(), "vid-1"),
                c.authorizationSignature());
        assertEquals(64, c.authorizationSignature().length());
        assertFalse(c.toString().contains("the-api-key"));
    }

    @Test
    void uploadSignature_isSha256HexOfLibraryKeyExpireAndVideo() throws Exception {
        String expected = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest("123the-api-key1700000000vid-1".getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, BunnyVideoHostingClient.uploadSignature("123", "the-api-key", 1_700_000_000L, "vid-1"));
    }
}
