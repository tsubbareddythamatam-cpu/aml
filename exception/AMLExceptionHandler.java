package org.aml.exception;

import org.aml.constants.AMLConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class AMLExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(AMLExceptionHandler.class);

    /**
     * Converts a missing-user exception to an HTTP 404 response.
     *
     * @param ex exception describing the missing user
     * @return standardized not-found response
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        logger.warn("Request failed because a user was not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.builder()
                        .errorCode(ErrorCodes.USER_NOT_FOUND)
                        .message(ex.getMessage())
                        .status(HttpStatus.NOT_FOUND.value())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    /**
     * Converts a duplicate-user exception to an HTTP 409 response.
     *
     * @param ex exception describing the duplicate account
     * @return standardized conflict response
     */
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        logger.warn("Request rejected because a user already exists");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.builder()
                        .errorCode(ErrorCodes.USER_ALREADY_EXISTS)
                        .message(ex.getMessage())
                        .status(HttpStatus.CONFLICT.value())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    /**
     * Converts an authentication failure to an HTTP 401 response.
     *
     * @param ex authentication exception
     * @return standardized unauthorized response
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        logger.warn("Authentication failed due to invalid credentials");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.builder()
                        .errorCode(ErrorCodes.INVALID_CREDENTIALS)
                        .message(AMLConstants.INVALID_CREDENTIALS)
                        .status(HttpStatus.UNAUTHORIZED.value())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    /**
     * Converts invalid application credentials to an HTTP 401 response.
     *
     * @param ex exception describing the invalid credentials
     * @return standardized unauthorized response
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        logger.warn("Authentication request rejected due to invalid credentials");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.builder()
                        .errorCode(ErrorCodes.INVALID_CREDENTIALS)
                        .message(ex.getMessage())
                        .status(HttpStatus.UNAUTHORIZED.value())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    // 1. Handles missing compliance resources (HTTP 404)
    /**
     * Converts a missing-resource exception to an HTTP 404 response.
     *
     * @param ex exception describing the missing resource
     * @return standardized not-found response
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        logger.warn("Request failed because a resource was not found");
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("ERR_RESOURCE_NOT_FOUND")
                .message(ex.getMessage())
                .status(HttpStatus.NOT_FOUND.value())
                .timestamp(LocalDateTime.now())
                .build();

        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    // 2. Handles SQL constraint checks like duplicate amlId fields (HTTP 409)
    /**
     * Converts a database integrity violation to an HTTP 409 response.
     *
     * @param ex database integrity exception
     * @return standardized conflict response
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        logger.warn("Database operation failed due to an integrity constraint");
        String rootMsg = ex.getRootCause() != null ? ex.getRootCause().getMessage() : ex.getMessage();
        String simpleMessage = "Database operation conflict occurred.";

        if (rootMsg != null && rootMsg.contains("Duplicate entry")) {
            simpleMessage = "Data conflict: A compliance record with this amlId already exists.";
        }

        ErrorResponse error = ErrorResponse.builder()
                .errorCode("ERR_DATA_CONFLICT")
                .message(simpleMessage)
                .status(HttpStatus.CONFLICT.value())
                .timestamp(LocalDateTime.now())
                .build();

        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    // 3. Handles request body parameter validations (HTTP 400)
    /**
     * Converts request validation failures to an HTTP 400 response.
     *
     * @param ex exception containing field validation errors
     * @return standardized bad-request response
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        logger.warn("Request rejected because validation failed");
        String validationDetails = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> String.format("Field '%s': %s", error.getField(), error.getDefaultMessage()))
                .collect(Collectors.joining(" | "));

        ErrorResponse error = ErrorResponse.builder()
                .errorCode("ERR_INVALID_REQUEST_PAYLOAD")
                .message("Validation failed: " + validationDetails)
                .status(HttpStatus.BAD_REQUEST.value())
                .timestamp(LocalDateTime.now())
                .build();

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // 4. Cleaned and Unified single fallback handler for unpredicted internal runtime errors (HTTP 500)
    /**
     * Converts otherwise unhandled exceptions to an HTTP 500 response.
     *
     * @param ex unhandled exception
     * @return standardized internal-server-error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex) {
        logger.error("Unhandled application exception", ex);
        ErrorResponse error = ErrorResponse.builder()
                .errorCode(ErrorCodes.INTERNAL_SERVER_ERROR)
                .message("An unexpected platform error occurred.")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .timestamp(LocalDateTime.now())
                .build();

        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    /**
     * Converts an access-denied exception to an HTTP 403 response.
     *
     * @param ex exception describing the denied operation
     * @return standardized forbidden response
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(org.springframework.security.access.AccessDeniedException ex) {
        logger.warn("Request rejected because access was denied");
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("ERR_ACCESS_DENIED")
                .message(ex.getMessage())
                .status(HttpStatus.FORBIDDEN.value())
                .timestamp(LocalDateTime.now())
                .build();

        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }
}
