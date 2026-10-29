package org.aml.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class ComplianceForm {

    @NotBlank(message = "Choose individual or company onboarding.")
    private String onboardingType = "INDIVIDUAL";

    private Long userId;

    @Size(max = 160, message = "Customer name must be 160 characters or fewer.")
    private String fullName;

    @Size(max = 160, message = "Company name must be 160 characters or fewer.")
    private String companyName;

    @Size(max = 120, message = "Position must be 120 characters or fewer.")
    private String positionInCompany;

    @Size(max = 100, message = "External reference must be 100 characters or fewer.")
    private String externalRefNumber;

    @Pattern(regexp = "IN_REVIEW|ACTIVE|PENDING|REJECTED|APPROVED", message = "Choose a valid application status.")
    private String status = "PENDING";

    @Size(max = 100, message = "Delivery channel must be 100 characters or fewer.")
    private String deliveryChannel;

    @Size(max = 100, message = "Transaction type must be 100 characters or fewer.")
    private String transactionType;

    @Size(max = 160, message = "Nationality details must be 160 characters or fewer.")
    private String otherNationalities;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate lastReviewDate;

    @Size(max = 2000, message = "Notes must be 2000 characters or fewer.")
    private String comment;

    private String dateOfBirth;
    private String fatherName;
    private String gender;
    private String individualType;
    @Size(max = 100, message = "Country must be 100 characters or fewer.")
    private String country;
    private String nationality;
    private String countryOfResidence;
    private String residentStatus;
    private String nationalIdNumber;
    private String nationalIdExpiry;
    @Size(max = 160, message = "Passport name must be 160 characters or fewer.")
    private String passportFullName;
    @Size(max = 80, message = "Passport first name must be 80 characters or fewer.")
    private String passportFirstName;
    @Size(max = 80, message = "Passport last name must be 80 characters or fewer.")
    private String passportLastName;
    @Size(max = 100, message = "Passport number must be 100 characters or fewer.")
    private String passportNumber;
    @Size(max = 10, message = "Passport type must be 10 characters or fewer.")
    private String passportType;
    @Pattern(regexp = "^$|^[A-Za-z]{3}$", message = "Issuing country code must contain three letters.")
    private String passportIssuingCountryCode;
    @Size(max = 10, message = "Enter a valid passport expiry date.")
    private String passportExpiry;
    @Size(max = 10, message = "Enter a valid passport issue date.")
    private String passportDateOfIssue;
    @Size(max = 120, message = "Issuing authority must be 120 characters or fewer.")
    private String passportIssuingAuthority;
    private String hasOtherNationalities;
    private String workType;
    private String industry;
    private String products;
    private String productOffered;
    private String address;
    private String townCity;
    private String countyState;
    private String postalCode;
    private String confirmEmailAddress;
    private String currentlyHoldPublicPosition;
    private String heldPublicPositionLast12Months;
    private String everHeldPublicPosition;
    private String diplomaticImmunity;
    private String relativeHeldPublicPositionLast12Months;
    private String closeAssociateHeldPublicPositionLast12Months;
    private String courtConviction;
    private String pepDetails;
    private String sourceOfWealth;
    private String sourceOfWealthOther;
    private String sourceOfFunds;
    private String sourceOfFundsOther;
    private List<String> sourceOfWealthSelections;
    private List<String> sourceOfFundsSelections;
    private boolean reviewConfirmed;

    @Email(message = "Enter a valid contact email address.")
    @Size(max = 254, message = "Contact email must be 254 characters or fewer.")
    private String emailAddress;

    @Pattern(regexp = "^$|^(?=(?:\\D*\\d){7,15}\\D*$)\\+?[0-9\\s().-]+$", message = "Enter a valid phone number with 7–15 digits.")
    private String contactNumber;

    @Pattern(regexp = "^$|^\\+[1-9][0-9]{0,3}$", message = "Enter a valid country dialing code, such as +1.")
    private String phoneCountryCode;
}
