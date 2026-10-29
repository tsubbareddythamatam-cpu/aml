package org.aml.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.aml.service.OnboardingInvitationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

@Controller
@RequestMapping("/portal/onboarding/invitations")
@RequiredArgsConstructor
public class OnboardingInvitationController {

    private final OnboardingInvitationService invitationService;

    @PostMapping
    @ResponseBody
    public InvitationResponse create(@RequestBody CreateInvitationRequest request,
                                     Authentication authentication) {
        OnboardingInvitationService.CreatedInvitation created =
                invitationService.create(request.onboardingType(), authentication.getName());
        return new InvitationResponse(created.url(), created.expiresAt(), created.onboardingType(),
                qrDataUri(created.url()));
    }

    private String qrDataUri(String url) {
        try {
            var hints = Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 2);
            var matrix = new MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, 300, 300, hints);
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                MatrixToImageWriter.writeToStream(matrix, "PNG", output);
                return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
            }
        } catch (WriterException | IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not create an invitation QR code.", exception);
        }
    }

    public record CreateInvitationRequest(@NotBlank String onboardingType) {
    }

    public record InvitationResponse(String url, LocalDateTime expiresAt,
                                     String onboardingType, String qrCodeDataUri) {
    }
}
