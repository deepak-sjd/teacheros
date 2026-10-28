package com.teacheros.modules.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Bunny Stream management API (https://video.bunnycdn.com). Plain
 * java.net.http, same approach as AnthropicAiTutorClient - three small calls
 * don't justify an SDK dependency.
 *
 * Upload: the browser sends the file straight to Bunny over TUS using a
 * signature we compute here: SHA256_HEX(libraryId + apiKey + expire + videoId).
 * The API key itself never leaves the server.
 *
 * Bunny video status codes: 0 Created, 1 Uploaded, 2 Processing,
 * 3 Transcoding, 4 Finished, 5 Error, 6 UploadFailed.
 */
@Component
@ConditionalOnProperty(name = "app.video.provider", havingValue = "bunny")
public class BunnyVideoHostingClient implements VideoHostingClient {

    static final String PROVIDER = "bunny";

    private final BunnyStreamSettings settings;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public BunnyVideoHostingClient(BunnyStreamSettings settings, ObjectMapper objectMapper) {
        this.settings = settings;
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerName() {
        return PROVIDER;
    }

    @Override
    public String createVideo(String title) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("title", title);
        HttpResponse<String> response = send(builder("/videos")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())));
        requireSuccess(response, "create video");
        String guid = parse(response).path("guid").asText("");
        if (guid.isBlank()) {
            throw new VideoProviderException("Bunny did not return a video id", null);
        }
        return guid;
    }

    @Override
    public UploadCredentials signUpload(String ref, Duration ttl) {
        long expire = Instant.now().plus(ttl).getEpochSecond();
        return new UploadCredentials(settings.tusEndpoint(), settings.libraryId(), ref, expire,
                uploadSignature(settings.libraryId(), settings.apiKey(), expire, ref));
    }

    /** SHA256_HEX(libraryId + apiKey + expire + videoId), per Bunny's TUS docs. */
    static String uploadSignature(String libraryId, String apiKey, long expire, String videoId) {
        return sha256Hex(libraryId + apiKey + expire + videoId);
    }

    @Override
    public VideoInfo getVideo(String ref) {
        HttpResponse<String> response = send(builder("/videos/" + encode(ref)).GET());
        if (response.statusCode() == 404) {
            return new VideoInfo(State.FAILED, 0, null, "The video no longer exists at the video provider.");
        }
        requireSuccess(response, "read video");
        JsonNode json = parse(response);
        int status = json.path("status").asInt(-1);
        return new VideoInfo(mapStatus(status), json.path("storageSize").asLong(0),
                json.hasNonNull("length") ? json.path("length").asInt() : null, failureReason(status));
    }

    static State mapStatus(int status) {
        return switch (status) {
            case 0 -> State.WAITING_FOR_FILE;
            case 4 -> State.READY;
            case 5, 6 -> State.FAILED;
            default -> State.PROCESSING; // 1, 2, 3 and any newer in-flight code
        };
    }

    private static String failureReason(int status) {
        return switch (status) {
            case 5 -> "The video provider could not process this file. Try a different file.";
            case 6 -> "The upload did not complete. Please upload it again.";
            default -> null;
        };
    }

    @Override
    public void deleteVideo(String ref) {
        HttpResponse<String> response = send(builder("/videos/" + encode(ref)).DELETE());
        if (response.statusCode() == 404) {
            return;
        }
        requireSuccess(response, "delete video");
    }

    // ---- helpers ----

    private HttpRequest.Builder builder(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(settings.apiBase() + "/library/" + settings.libraryId() + path))
                .timeout(Duration.ofSeconds(20))
                .header("AccessKey", settings.apiKey())
                .header("Accept", "application/json");
    }

    private HttpResponse<String> send(HttpRequest.Builder request) {
        try {
            return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VideoProviderException("Interrupted while calling the video provider", e);
        } catch (Exception e) {
            throw new VideoProviderException("Could not reach the video provider", e);
        }
    }

    // The response body is deliberately not put in the message: it is logged by
    // the caller's exception handler only as a status, never echoed to a client.
    private void requireSuccess(HttpResponse<String> response, String action) {
        if (response.statusCode() / 100 != 2) {
            throw new VideoProviderException(
                    "Bunny failed to " + action + " (HTTP " + response.statusCode() + ")", null);
        }
    }

    private JsonNode parse(HttpResponse<String> response) {
        try {
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            throw new VideoProviderException("Unreadable response from the video provider", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
