package com.teacheros.modules.course;

public enum VideoUploadStatus {
    /** Created at the provider; the browser has not finished sending the file. */
    PENDING_UPLOAD,
    /** The provider is encoding it. Cannot be attached to a lesson yet. */
    PROCESSING,
    /** Encoded and playable. The only status a lesson may attach. */
    READY,
    /** Upload or encoding failed. Does not count towards the storage cap. */
    FAILED
}
