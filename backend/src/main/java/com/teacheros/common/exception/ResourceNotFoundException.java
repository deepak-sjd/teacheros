package com.teacheros.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resourceType, Object id) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                resourceType + " with id " + id + " was not found");
    }
}
