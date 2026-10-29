package org.aml.exception;

public class InvalidCredentialsException extends RuntimeException {

    /**
     * Creates an exception describing invalid authentication credentials.
     *
     * @param message explanation of the authentication failure
     */
    public InvalidCredentialsException(String message) {
        super(message);
    }
}