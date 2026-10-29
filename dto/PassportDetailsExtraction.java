package org.aml.dto;

import java.time.LocalDate;

public record PassportDetailsExtraction(
        String firstName,
        String lastName,
        String fullName,
        String passportNumber,
        LocalDate passportExpiryDate,
        LocalDate dateOfBirth,
        String nationality,
        String gender,
        String passportType,
        String passportIssuingCountryCode,
        LocalDate passportDateOfIssue,
        String passportIssuingAuthority) {
}
