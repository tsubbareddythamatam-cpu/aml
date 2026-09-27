package org.aml.service;

import lombok.RequiredArgsConstructor;
import org.aml.constants.AMLConstants;
import org.aml.dto.CompanyDetailDto;
import org.aml.dto.LoginRequest;
import org.aml.dto.LoginResponse;
import org.aml.dto.RegisterRequest;
import org.aml.model.CompanyDetail;
import org.aml.model.Role;
import org.aml.model.User;
import org.aml.exception.InvalidCredentialsException;
import org.aml.exception.UserAlreadyExistsException;
import org.aml.exception.UserNotFoundException;
import org.aml.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final EmailService emailService;

    /**
     * Registers a user, stores company details, and sends the initial password setup link.
     *
     * @param request registration details
     * @return confirmation that the setup link was emailed
     * @throws IllegalArgumentException if the request is null
     * @throws UserAlreadyExistsException if the email or phone number is already registered
     */
    @CacheEvict(value = "usersDirectory", allEntries = true)
    public String register(RegisterRequest request) {
        if (request == null) {
            logger.error("Registration failed: Request payload is null");
            throw new IllegalArgumentException(AMLConstants.USER_DERAILS_CANNOT_BE_NULL);
        }

        logger.info("Received user registration request");

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            logger.warn("Registration rejected because the email is already registered");
            throw new UserAlreadyExistsException(AMLConstants.USER_ALREADY_EXISTS + request.getEmail());
        }

        if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
            logger.warn("Registration rejected because the phone number is already registered");
            throw new UserAlreadyExistsException(AMLConstants.USER_ALREADY_EXISTS_PHONE_NUMBER + request.getPhoneNumber());
        }

        if (request.getRole() != null && !request.getRole().isBlank()) {
            logger.warn("Ignoring client-supplied role during public registration");
        }
        Role targetRole = Role.USER;

        String token = UUID.randomUUID().toString();
        LocalDateTime expiryTime = LocalDateTime.now().plusHours(24);

        // 🆕 1. Build the CompanyDetail sub-entity instance using the request parameters
        CompanyDetail companyDetail = CompanyDetail.builder()
                .companyName(request.getCompanyName())
                .dateOfIncorporation(request.getDateOfIncorporation()) // Extracted from expanded RegisterRequest
                .countryOfOperation(request.getCountryOfOperation())
                .countryOfDomicile(request.getCountryOfDomicile())
                .registrationNo(request.getRegistrationNo())
                .registrationNoExpiryDate(request.getRegistrationNoExpiryDate())
                .product(request.getProduct())
                .industry(request.getIndustry())
                .build();

        // 🆕 2. Attach the mapped sub-entity block directly to the parent User builder structure
        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .companyDetail(companyDetail) // Linked mapping setup
                .role(targetRole)
                .resetToken(token)
                .resetTokenExpiry(expiryTime)
                .build();

        // Optional: Establish bidirectional entity sync reference if needed by your JPA configuration rules
        companyDetail.setUser(user);

        userRepository.save(user);
        logger.info("Successfully persisted new user and company metadata");

        emailService.mailSend(
                request.getFirstName(),
                request.getLastName(),
                request.getEmail(),
                token,
                expiryTime
        );

        return "Registration successful. Check your email to set your password.";
    }

    /**
     * Authenticates credentials and returns a token with the user's account details.
     *
     * @param request login credentials
     * @return login response containing the generated token
     * @throws InvalidCredentialsException if credentials are missing or invalid
     * @throws UserNotFoundException if the authenticated account cannot be found
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        logger.info("Authentication attempt initiated");

        if (request == null) {
            logger.error("Login failed: Request payload is null");
            throw new InvalidCredentialsException(AMLConstants.INVALID_CREDENTIALS);
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()));
            logger.debug("Spring Security authentication successful");

        } catch (BadCredentialsException ex) {
            logger.warn("Authentication failed due to invalid credentials");
            throw new InvalidCredentialsException(AMLConstants.INVALID_CREDENTIALS);
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    logger.error("Authenticated principal has no matching user record");
                    return new UserNotFoundException(AMLConstants.USER_NOT_FOUND);
                });

        String token = jwtService.generateToken(user);
        logger.info("JWT session token successfully generated");

        // 🆕 3. Safely resolve corporate metadata properties out of the embedded child object layer
        // 1. Initialize the base authentication login payload response
        LoginResponse.LoginResponseBuilder loginResponseBuilder = LoginResponse.builder()
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole() != null ? user.getRole().name() : Role.USER.name())
                .token(token);

        // 2. 🆕 Safely convert and map the full child table profile details if present in the database record
        if (user.getCompanyDetail() != null) {
            org.aml.model.CompanyDetail cd = user.getCompanyDetail();

            CompanyDetailDto companyDto = CompanyDetailDto.builder()
                    .companyName(cd.getCompanyName())
                    .dateOfIncorporation(cd.getDateOfIncorporation())
                    .countryOfOperation(cd.getCountryOfOperation())
                    .countryOfDomicile(cd.getCountryOfDomicile())
                    .registrationNo(cd.getRegistrationNo())
                    .registrationNoExpiryDate(cd.getRegistrationNoExpiryDate())
                    .product(cd.getProduct())
                    .industry(cd.getIndustry())
                    .build();

            loginResponseBuilder.companyDetail(companyDto);
        }

        return loginResponseBuilder.build();

    }

    /**
     * Creates and emails a time-limited password reset token.
     *
     * @param email email address of the account
     * @return confirmation message
     * @throws UserNotFoundException if no account matches the email
     */
    @CacheEvict(value = "usersDirectory", allEntries = true)
    public String forgotPassword(String email) {
        logger.info("Received request to generate password reset token");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    logger.warn("Forgot password execution rejected because account was not found");
                    return new UserNotFoundException(AMLConstants.USER_NOT_FOUND);
                });

        String token = UUID.randomUUID().toString();
        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(15));

        userRepository.save(user);
        logger.info("Secure reset token generated and saved");

        emailService.sendForgotPasswordEmail(user.getEmail(), user.getFirstName(), user.getLastName(), token);

        return "Password reset link sent to your registered email address.";
    }

    /**
     * Validates a reset token and saves the replacement password.
     *
     * @param token password reset token
     * @param newPassword replacement password
     * @return confirmation message
     * @throws InvalidCredentialsException if the token is invalid or expired
     */
    @CacheEvict(value = "usersDirectory", allEntries = true)
    public String resetPassword(String token, String newPassword) {
        logger.info("Received request to reset password utilizing a token challenge verification");

        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> {
                    logger.warn("Password reset execution rejected: Provided token does not match any records");
                    return new InvalidCredentialsException("Invalid or expired password reset token.");
                });

        if (user.getResetTokenExpiry() == null
                || user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            logger.warn("Password reset execution rejected because the token has expired");
            throw new InvalidCredentialsException("Reset token has expired. Please request a new link.");
        }

        user.setPassword(encoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);

        userRepository.save(user);
        logger.info("Password successfully updated and temporary tokens invalidated");

        emailService.sendPasswordChangedNotification(user.getEmail(), user.getFirstName(), user.getLastName());

        return "Password has been reset successfully.";
    }

    /**
     * Validates a registration token and saves the new user's initial password.
     *
     * @param token registration verification token
     * @param clearPassword initial password in clear text
     * @return confirmation message
     * @throws IllegalArgumentException if the token is unknown
     * @throws IllegalStateException if the token has expired
     */
    @CacheEvict(value = "usersDirectory", allEntries = true)
    public String setupNewUserPassword(String token, String clearPassword) {
        logger.info("Processing password configuration workflow execution for token sequence context");

        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid registration verification link token."));
        if (user.getResetTokenExpiry() == null
                || user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            logger.warn("Password configuration initialization aborted because the registration token expired");
            throw new IllegalStateException("Registration verification timeline window expired. Please contact support.");
        }
        user.setPassword(encoder.encode(clearPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
        logger.info("Successfully completed initial account password setup");
        emailService.sendPasswordChangedNotification(user.getEmail(), user.getFirstName(), user.getLastName());
        return "Account verified and password saved successfully.";
    }
}