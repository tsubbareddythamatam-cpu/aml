package org.aml.constants;

public final class AMLConstants {

    private AMLConstants() {
        throw new IllegalStateException("Utility class");
    }

    // Success Messages
    public static final String USER_REGISTERED_SUCCESS =
            "User registered successfully";

    public static final String LOGIN_SUCCESS =
            "Login successful";

    // Error Messages
    public static final String USER_NOT_FOUND =
            "User not found";

    public static final String USER_ALREADY_EXISTS =
            "User already exists with email: ";

    public static final String USER_ALREADY_EXISTS_PHONE_NUMBER =
            "User already exists with phone number: ";

    public static final String INVALID_CREDENTIALS =
            "Invalid email or password";

    public static final String INVALID_TOKEN =
            "Invalid token";

    public static final String ACCESS_DENIED =
            "Access denied";

    public static final String INTERNAL_SERVER_ERROR =
            "Internal server error";

    // Error Codes
    public static final String USER_NOT_FOUND_CODE =
            "USR_001";

    public static final String USER_ALREADY_EXISTS_CODE =
            "USR_002";

    public static final String INVALID_CREDENTIALS_CODE =
            "AUTH_001";

    public static final String INVALID_TOKEN_CODE =
            "AUTH_002";

    public static final String ACCESS_DENIED_CODE =
            "AUTH_003";

    public static final String INTERNAL_SERVER_ERROR_CODE =
            "SYS_001";

    // Roles
    public static final String ROLE_ADMIN =
            "ROLE_ADMIN";

    public static final String ROLE_USER =
            "ROLE_USER";

    // JWT
    public static final String TOKEN_PREFIX =
            "Bearer ";

    public static final String AUTHORIZATION =
            "Authorization";

    public static final long JWT_EXPIRATION =
            86400000L;

    // API Paths
    public static final String AUTH_API =
            "/api/auth/**";

    public static final String ADMIN_API =
            "/api/admin/**";

    public static final String USER_API =
            "/api/user/**";

    public static final String ADMIN =  "ADMIN";
    public static final String USER = "USER";
    public static final String REQUEST_PATH = "/api/auth";

    public static final String REGISTRATION_PATH = "/api/registraion";

    public static final String REGISTER = "/register";

    public static final String LOGIN= "/login";

    public static  final String USER_DERAILS_CANNOT_BE_NULL = "User details cannot be null";


}