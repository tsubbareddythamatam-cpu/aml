package org.aml.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest implements Serializable {

    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String role;

    // 🆕 Corporate Profile Onboarding Fields
    private String companyName;
    private String country;
    private LocalDate dateOfIncorporation;
    private String countryOfOperation;
    private String countryOfDomicile;
    private String registrationNo;
    private LocalDate registrationNoExpiryDate;
    private String product;
    private String industry;
}
