package org.aml.exception;

public class UserAlreadyExistsException extends RuntimeException {

    /**
     * Creates an exception describing a duplicate user registration.
     *
     * @param message explanation of the duplicate registration
     */
    public UserAlreadyExistsException(String message) {
        super(message);
    }
}