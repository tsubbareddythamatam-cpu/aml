package org.aml.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.aml.annotation.AuthenticatedToken;
import org.aml.constants.AMLConstants;
import org.aml.dto.BulkRegistrationResponse;
import org.aml.dto.UpdateUserRequest;
import org.aml.dto.UserResponse;
import org.aml.exception.ErrorResponse;
import org.aml.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping(AMLConstants.USER_PATH)
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User Management Controller", description = "Handles user profile management, retrieval, and bulk registration operations.")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final UserService userService;

    /**
     * Retrieves all user profiles.
     *
     * @param token authenticated administrator token
     * @return all user profiles
     */
    @Operation(summary = "Fetch all registered users", description = "Retrieves a list of all users in the system. Requires admin privileges.")
    @GetMapping("/getUsers")
    public ResponseEntity<List<UserResponse>> getAllUsers(
            @Parameter(hidden = true) @AuthenticatedToken String token) {
        logger.info("REST endpoint hit: Fetching user registry directory via auto-resolved token context");
        List<UserResponse> users = userService.retrieveAllUsers(token);
        return ResponseEntity.ok(users);
    }

    /**
     * Registers users from an uploaded workbook.
     *
     * @param token authenticated administrator token
     * @param file spreadsheet file containing user records
     * @return bulk registration results
     */
    @Operation(summary = "Perform bulk user registration", description = "Registers multiple users in a single operation. Requires admin privileges.")
    @PostMapping(value = AMLConstants.BULK_REGISTRATION,
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BulkRegistrationResponse> bulkRegistration(
            @Parameter(hidden = true) @AuthenticatedToken String token,
            @RequestParam("file") MultipartFile file) {

        BulkRegistrationResponse response =
                userService.bulkRegistration(file);

        if (response.getFailedRecords() > 0) {
            return ResponseEntity.status(HttpStatus.MULTI_STATUS)
                    .body(response); // 207
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a user profile by its database identifier.
     *
     * @param id user identifier
     * @param token authenticated administrator token
     * @return matching profile or an error response
     */
    @Operation(summary = "Fetch user by ID", description = "Retrieves the details of a specific user by their ID. Requires admin privileges.")
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(
            @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticatedToken String token) {

        logger.info("REST endpoint hit: Fetching user details for ID: {}", id);

        // 1. Handle Invalid ID Input (Null or Negative/Zero)
        if (id == null || id <= 0) {
            logger.warn("User lookup aborted: Invalid target ID parameter provided");

            ErrorResponse error = ErrorResponse.builder()
                    .errorCode("USR_400")
                    .message("Invalid user ID profile parameter provided. ID must be a positive number.")
                    .status(400)
                    .timestamp(LocalDateTime.now())
                    .build();

            return ResponseEntity.badRequest().body(error);
        }

        try {
            // 2. Attempt to retrieve user profile
            UserResponse response = userService.retrieveUserById(id);
            return ResponseEntity.ok(response);

        } catch (org.springframework.web.server.ResponseStatusException ex) {
            // 3. Handle User Not Found Case
            logger.warn("User lookup failed: {}", ex.getReason());

            ErrorResponse error = ErrorResponse.builder()
                    .errorCode("USR_404")
                    .message(ex.getReason()) // Pulls "Requested user record profile does not exist." from your service layer
                    .status(404)
                    .timestamp(LocalDateTime.now())
                    .build();

            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).body(error);
        }
    }

    /**
     * Searches for a user profile by email address.
     *
     * @param email email address to search for
     * @param token authenticated administrator token
     * @return matching profile or an error response
     */
    @Operation(summary = "Fetch user by email", description = "Retrieves the details of a specific user by their email address. Requires admin privileges.")
    @GetMapping("/search")
    public ResponseEntity<?> getUserByEmail(
            @RequestParam("email") String email,
            @Parameter(hidden = true) @AuthenticatedToken String token) {

        logger.info("REST endpoint hit: Fetching user details for email parameter search context");

        // 1. Handle Invalid Email Input (Null or Empty)
        if (email == null || email.isBlank()) {
            logger.warn("User lookup aborted: Target search email parameter value missing");

            ErrorResponse error = ErrorResponse.builder()
                    .errorCode("USR_400")
                    .message("Required query parameter 'email' is missing or empty.")
                    .status(400)
                    .timestamp(LocalDateTime.now())
                    .build();

            return ResponseEntity.badRequest().body(error);
        }

        try {
            // 2. Attempt to retrieve user profile
            UserResponse response = userService.retrieveUserByEmail(email.trim());
            return ResponseEntity.ok(response);

        } catch (org.springframework.web.server.ResponseStatusException ex) {
            // 3. Handle Email Not Found Case
            logger.warn("User lookup failed: {}", ex.getReason());

            ErrorResponse error = ErrorResponse.builder()
                    .errorCode("USR_404")
                    .message(ex.getReason()) // Pulls "No registered account matches that email address." from your service layer
                    .status(404)
                    .timestamp(LocalDateTime.now())
                    .build();

            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).body(error);
        }
    }
    /**
     * Updates a user profile.
     *
     * @param id user identifier
     * @param request fields to update
     * @param token authenticated administrator token
     * @return updated profile or an error response
     */
    @Operation(summary = "Update user profile", description = "Updates the details of a specific user by their ID. Requires admin privileges.")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUserProfile(
            @PathVariable Long id,
            @RequestBody UpdateUserRequest request,
            @Parameter(hidden = true) @AuthenticatedToken String token) {

        logger.info("REST endpoint hit: Processing modification request for identification key: {}", id);

        // 1. Guard Validation check for structural payload boundaries
        if (id == null || id <= 0) {
            return ResponseEntity.badRequest().body(ErrorResponse.builder()
                    .errorCode("USR_400")
                    .message("Invalid target ID configuration profile parameter provided.")
                    .status(400)
                    .timestamp(LocalDateTime.now())
                    .build());
        }

        try {
            // 2. Process modification workflow lines
            UserResponse updatedProfile = userService.updateUserDetails(id, request);
            return ResponseEntity.ok(updatedProfile);

        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(ErrorResponse.builder()
                    .errorCode("USR_404")
                    .message(ex.getReason())
                    .status(ex.getStatusCode().value())
                    .timestamp(LocalDateTime.now())
                    .build());
        }
    }

}
