package org.aml.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aml.constants.AMLConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);

    @Value("${app.rate-limit.window-seconds:60}")
    private int windowSeconds;

    @Value("${app.rate-limit.default-limit:20}")
    private int defaultLimit;

    @Value("${app.rate-limit.sensitive-limit:5}")
    private int sensitiveLimit;

    @Value("${app.rate-limit.retry-after-seconds:60}")
    private int retryAfterSeconds;

    private final Map<String, RateWindow> requestCounts = new ConcurrentHashMap<>();

    /**
     * Skips rate limiting for requests outside the authentication API path.
     *
     * @param request current HTTP request
     * @return {@code true} when the filter should be skipped
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith(AMLConstants.REQUEST_PATH);
    }

    /**
     * Enforces the configured request limit for the client's IP and request path.
     *
     * @param request current HTTP request
     * @param response current HTTP response
     * @param filterChain remaining servlet filters
     * @throws ServletException if request filtering fails
     * @throws IOException if request or response I/O fails
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String clientIp = resolveClientIp(request);
        String requestPath = request.getServletPath();
        int limit = isSensitiveEndpoint(requestPath) ? sensitiveLimit : defaultLimit;

        if (!isAllowed(clientIp, requestPath, limit)) {
            logger.warn("Rate limit exceeded for authentication endpoint");
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
            response.getWriter().write("""
                    {
                      "errorCode": "AUTH_004",
                      "message": "Too many authentication requests. Please try again later.",
                      "status": 429
                    }
                    """);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Counts requests for a client and endpoint within the active rate-limit window.
     *
     * @param clientIp client IP address
     * @param requestPath requested servlet path
     * @param limit maximum requests allowed in the window
     * @return {@code true} when the request is within the configured limit
     */
    private boolean isAllowed(String clientIp, String requestPath, int limit) {
        String key = clientIp + ":" + requestPath;
        RateWindow window = requestCounts.computeIfAbsent(key, k -> new RateWindow(Instant.now(), new AtomicInteger(0)));

        synchronized (window) {
            if (Duration.between(window.startTime, Instant.now()).compareTo(Duration.ofSeconds(windowSeconds)) > 0) {
                window.startTime = Instant.now();
                window.count.set(0);
            }

            int currentCount = window.count.incrementAndGet();
            return currentCount <= limit;
        }
    }

    /**
     * Resolves the client address from the servlet connection, avoiding spoofable forwarding headers.
     *
     * @param request current HTTP request
     * @return resolved client IP address
     */
    private String resolveClientIp(HttpServletRequest request) {
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    /**
     * Determines whether a request path uses the stricter sensitive-endpoint limit.
     *
     * @param path servlet request path
     * @return {@code true} for login and password-management endpoints
     */
    private boolean isSensitiveEndpoint(String path) {
        return path.contains("/login")
                || path.contains("/forgot-password")
                || path.contains("/reset-password")
                || path.contains("/set-password");
    }

    private static class RateWindow {
        private Instant startTime;
        private final AtomicInteger count;

        /**
         * Creates a rate-limit window with its initial request count.
         *
         * @param startTime start time of the window
         * @param count number of requests recorded in the window
         */
        RateWindow(Instant startTime, AtomicInteger count) {
            this.startTime = startTime;
            this.count = count;
        }
    }
}
