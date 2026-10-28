package com.teacheros.modules.course;

/** The video provider could not be reached or rejected a request. */
public class VideoProviderException extends RuntimeException {

    public VideoProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
