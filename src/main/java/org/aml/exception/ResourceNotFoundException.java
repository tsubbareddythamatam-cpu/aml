package org.aml.exception;

public class ResourceNotFoundException extends RuntimeException {
    /**
     * Creates an exception describing a missing resource.
     *
     * @param message explanation of the missing resource
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
