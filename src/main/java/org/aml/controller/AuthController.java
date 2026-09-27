package org.aml.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.aml.dto.ForgotPasswordRequest;
import org.aml.dto.LoginRequest;
import org.aml.dto.LoginResponse;
import org.aml.dto.RegisterRequest;
import org.aml.dto.ResetPasswordRequest;
import org.aml.dto.PasswordSetupRequest; // Ensure you create this DTO
import org.aml.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.aml.constants.AMLConstants.*;

@RestController
@RequestMapping(REQUEST_PATH)
@RequiredArgsConstructor
@Tag(name = "Authentication Controller", description = "Handles user authentication, registration, and password management.")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    /**
     * Registers a user account and returns the setup token confirmation.
     *
     * @param request registration payload
     * @return registration response
     */
    @PostMapping(REGISTER)
    @Operation(summary = "Register a new user", description = "Registers a new user with the provided details.")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {
        logger.info("REST endpoint hit: Registration process initiated");
        String response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Completes initial password setup using the registration token.
     *
     * @param request payload containing the setup token and password
     * @return setup result or a bad-request message
     */
    @PostMapping(SET_PASSWORD)
    @Operation(summary = "Setup initial password for new user", description = "Allows a newly registered user to set their initial password using a token.")
    public ResponseEntity<String> setupInitialPassword(@RequestBody PasswordSetupRequest request) {
        logger.info("REST endpoint hit: New registration initial password configuration processing started");

        if (request == null || request.getToken() == null || request.getToken().isBlank()) {
            logger.warn("Password configuration validation drop: Missing initialization token context");
            return ResponseEntity.badRequest().body("Registration token parameter value must be specified.");
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            logger.warn("Password configuration validation drop: User target credential payload was blank");
            return ResponseEntity.badRequest().body("Password value cannot be empty.");
        }

        // Delegates to service layer for validation and credential persistence
        String response = authService.setupNewUserPassword(request.getToken().trim(), request.getPassword());
        return ResponseEntity.ok(response);
    }

    /**
     * Authenticates a user and returns the login response.
     *
     * @param request login credentials
     * @return login response containing account details and token
     */
    @Operation(summary = "Login a user", description = "Authenticates a user and returns a login response.")
    @PostMapping(LOGIN)
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        logger.info("REST endpoint hit: User login authentication processing started");
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Starts the password reset process for the supplied email address.
     *
     * @param request payload containing the account email
     * @return reset request result
     */
    @Operation(summary = "Request password reset", description = "Initiates the password reset workflow for a user.")
    @PostMapping(FORGOT_PASSWORD)
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        logger.info("REST endpoint hit: Password reset workflow token request initiated for email");

        if (request == null || request.getEmail() == null || request.getEmail().isBlank()) {
            logger.warn("Forgot password request execution dropped: Email request metadata structure missing");
            return ResponseEntity.badRequest().body("Email field cannot be empty.");
        }

        String response = authService.forgotPassword(request.getEmail().trim());
        return ResponseEntity.ok(response);
    }

    /**
     * Resets a user's password using a valid reset token.
     *
     * @param request payload containing the reset token and new password
     * @return password reset result
     */
    @Operation(summary = "Reset user password", description = "Resets the password for a user using a secure token.")
    @PostMapping(RESET_PASSWORD)
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequest request) {
        logger.info("REST endpoint hit: Secure token verify and credential replacement processing started");

        if (request == null || request.getToken() == null || request.getToken().isBlank()) {
            logger.warn("Password reset request validation drop: Missing token parameter");
            return ResponseEntity.badRequest().body("Reset token parameter value must be specified.");
        }

        if (request.getNewPassword() == null || request.getNewPassword().isBlank()) {
            logger.warn("Password reset request validation drop: New target credential input payload was blank");
            return ResponseEntity.badRequest().body("New password value cannot be empty.");
        }

        String response = authService.resetPassword(request.getToken().trim(), request.getNewPassword());
        return ResponseEntity.ok(response);
    }
}
