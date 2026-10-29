package org.aml.utility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class HeaderUtils {

    private static final Logger logger = LoggerFactory.getLogger(HeaderUtils.class);
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Extracts the JWT from a Bearer authorization header.
     *
     * @param authorizationHeader raw Authorization header
     * @return token without the scheme prefix
     * @throws ResponseStatusException if the header is missing or malformed
     */
    public static String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            logger.warn("Security check failed: Authorization header context is completely missing or empty.");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authorization credentials.");
        }

        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            logger.warn("Security check failed: Header scheme is malformed. Expected Bearer prefix.");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authorization header scheme format.");
        }

        // Isolate the clean raw token key character string from the prefix value structure
        String rawToken = authorizationHeader.substring(BEARER_PREFIX.length()).trim();

        if (rawToken.isEmpty()) {
            logger.warn("Security check failed: Authorization header contains the Bearer prefix but the token payload is empty.");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Empty authentication token payload provided.");
        }

        return rawToken;
    }
}
