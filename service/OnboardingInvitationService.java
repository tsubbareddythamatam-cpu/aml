package org.aml.service;

import org.aml.model.OnboardingInvitation;
import org.aml.repository.OnboardingInvitationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class OnboardingInvitationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    private static final int VALIDITY_DAYS = 7;

    private final OnboardingInvitationRepository repository;
    private final String publicBaseUrl;

    public OnboardingInvitationService(OnboardingInvitationRepository repository,
                                       @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.repository = repository;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public CreatedInvitation create(String requestedType, String createdBy) {
        String type = requestedType == null ? "" : requestedType.trim().toUpperCase(Locale.ROOT);
        if (!"INDIVIDUAL".equals(type) && !"COMPANY".equals(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose individual or company onboarding.");
        }

        byte[] randomToken = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(randomToken);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomToken);
        LocalDateTime now = LocalDateTime.now();

        OnboardingInvitation invitation = new OnboardingInvitation();
        invitation.setTokenHash(hashToken(token));
        invitation.setOnboardingType(type);
        invitation.setCreatedBy(createdBy);
        invitation.setCreatedAt(now);
        invitation.setExpiresAt(now.plusDays(VALIDITY_DAYS));
        repository.save(invitation);

        return new CreatedInvitation(
                publicBaseUrl + "/onboarding/invite/" + token,
                invitation.getExpiresAt(),
                type);
    }

    @Transactional(readOnly = true)
    public OnboardingInvitation findValid(String token) {
        OnboardingInvitation invitation = find(token, false);
        validateAvailable(invitation);
        return invitation;
    }

    @Transactional
    public OnboardingInvitation findValidForCompletion(String token) {
        OnboardingInvitation invitation = find(token, true);
        validateAvailable(invitation);
        return invitation;
    }

    @Transactional
    public void markCompleted(OnboardingInvitation invitation) {
        invitation.setCompletedAt(LocalDateTime.now());
        repository.save(invitation);
    }

    private OnboardingInvitation find(String token, boolean lock) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{40,50}")) {
            throw invalidInvitation();
        }
        String tokenHash = hashToken(token);
        return (lock ? repository.findByTokenHashForUpdate(tokenHash) : repository.findByTokenHash(tokenHash))
                .orElseThrow(OnboardingInvitationService::invalidInvitation);
    }

    private void validateAvailable(OnboardingInvitation invitation) {
        if (invitation.getCompletedAt() != null || !invitation.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw invalidInvitation();
        }
    }

    private static ResponseStatusException invalidInvitation() {
        return new ResponseStatusException(HttpStatus.GONE,
                "This onboarding link has expired or has already been completed. Ask the sender for a new link.");
    }

    private static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public record CreatedInvitation(String url, LocalDateTime expiresAt, String onboardingType) {
    }
}
