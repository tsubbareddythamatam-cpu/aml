package org.aml.exception;

public class UserNotFoundException extends RuntimeException {

    /**
     * Creates an exception describing a user lookup failure.
     *
     * @param message explanation of the lookup failure
     */
    public UserNotFoundException(String message) {
        super(message);
    }
}