package org.aml.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class PassportReviewForm {

    @NotBlank(message = "Enter the first name shown on the passport.")
    @Size(max = 80, message = "First name must be 80 characters or fewer.")
    @Pattern(regexp = "^[\\p{L}][\\p{L}\\p{M} .'-]*$", message = "First name may contain letters, spaces, apostrophes, periods, and hyphens.")
    private String firstName;

    @NotBlank(message = "Enter the last name shown on the passport.")
    @Size(max = 80, message = "Last name must be 80 characters or fewer.")
    @Pattern(regexp = "^[\\p{L}][\\p{L}\\p{M} .'-]*$", message = "Last name may contain letters, spaces, apostrophes, periods, and hyphens.")
    private String lastName;

    @NotBlank(message = "Enter the name exactly as printed on the passport.")
    @Size(max = 160, message = "Passport name must be 160 characters or fewer.")
    private String passportFullName;

    @NotBlank(message = "Passport number is required.")
    @Size(max = 80, message = "Passport number must be 80 characters or fewer.")
    private String passportNumber;

    @NotBlank(message = "Enter the passport document type shown on the passport.")
    @Size(max = 10, message = "Passport type must be 10 characters or fewer.")
    private String passportType;

    @NotBlank(message = "Enter the three-letter issuing country code shown on the passport.")
    @Pattern(regexp = "^[A-Za-z]{3}$", message = "Issuing country code must contain three letters.")
    private String passportIssuingCountryCode;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateOfBirth;

    @Size(max = 100, message = "Nationality must be 100 characters or fewer.")
    private String nationality;

    @Size(max = 30, message = "Gender must be 30 characters or fewer.")
    private String gender;

    @NotNull(message = "Passport expiry date is required.")
    @Future(message = "Passport must not be expired.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate passportExpiryDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate passportDateOfIssue;

    @Size(max = 120, message = "Issuing authority must be 120 characters or fewer.")
    private String passportIssuingAuthority;

    @AssertTrue(message = "Confirm that you reviewed the passport image and details.")
    private boolean documentReviewed;

    @AssertTrue(message = "Confirm that you checked the passport holder's identity, including any name difference.")
    private boolean identityConfirmed;
}
