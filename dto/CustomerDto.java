package org.aml.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDto {

    private String amlId;
    private Long userId;
    private String externalRefNumber;
    private String fullName;
    private String otherNationalities;
    private String deliveryChannel;
    private String transactionType;
    private Boolean nameScreeningHit;
    private Boolean documentsVerificationHit;
    private Boolean riskRatingHit;
    private String status;
    private String onboardedByUser;
    private String onboardedByCompany;
    private String comment;
    private LocalDateTime registeredAt;
    private LocalDate lastReviewDate;
    private LocalDateTime reportGeneratedOn;
    private String companyName;
    private String positionInCompany;
    private String category;
    private String country;
    private String passportDocumentName;
    private String passportImageType;
    private String passportImageQualityStatus;
    private String passportVerificationStatus;
    private String passportFullName;
    private String passportFirstName;
    private String passportLastName;
    private String verifiedPassportNumber;
    private String passportType;
    private String passportIssuingCountryCode;
    private LocalDate passportExpiryDate;
    private LocalDate passportDateOfIssue;
    private String passportIssuingAuthority;
    private String passportVerifiedBy;
    private LocalDateTime passportVerifiedAt;
    private String sourceOfWealthEvidenceName;
    private String sourceOfFundsEvidenceName;

    private KycDataDto kycData;
    private RiskRatingsDto riskRatingsAndOverrides;
    private List<ScreeningHitDto> screeningHits;
    private List<AuditLogDto> auditLogs;
}
