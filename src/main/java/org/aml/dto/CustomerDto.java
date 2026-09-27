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

    private KycDataDto kycData;
    private RiskRatingsDto riskRatingsAndOverrides;
    private List<ScreeningHitDto> screeningHits;
    private List<AuditLogDto> auditLogs;
}
