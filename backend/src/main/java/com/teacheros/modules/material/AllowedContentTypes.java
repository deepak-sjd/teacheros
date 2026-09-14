package com.teacheros.modules.material;

import java.util.Set;

/**
 * Whitelist of content types accepted for study material. Rejects anything
 * else outright rather than trying to accept "most things" - matches the
 * blueprint's file-upload rule: never trust the client-declared MIME type
 * as authorization to store arbitrary content. This is a declared-type
 * check (not deep content sniffing/magic-byte validation) - adequate for
 * MVP scope; revisit with a library like Apache Tika if this needs to
 * harden further before a wider public launch.
 */
final class AllowedContentTypes {

    private AllowedContentTypes() {
    }

    static final Set<String> WHITELIST = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/webp",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    static boolean isAllowed(String contentType) {
        return contentType != null && WHITELIST.contains(contentType);
    }
}
