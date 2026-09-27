package org.aml.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskRatingsDto {
    private Integer totalMatches;
    private Integer resolvedMatches;
    private Integer genuineMatches;
    private Integer notGenuineMatches;
    private Integer unresolvedMatches;
    private Integer countryResidenceScore;
    private String countryResidenceLevel;
    private Integer deliveryChannelScore;
    private String deliveryChannelLevel;
    private Integer industryScore;
    private String industryLevel;
    private Integer nationalityScore;
    private String nationalityLevel;
    private Integer productScore;
    private String productLevel;
    private Integer antiSpoofingScore;
    private String antiSpoofingLevel;
    private Integer baseRatingScore;
    private String baseRatingLevel;

    private String strFiledOverride;
    private String nonResidentOverride;
    private String sanctionedResidenceOverride;
    private String sanctionedNationalityOverride;
    private String sanctionedPhoneCodeOverride;
    private String sanctionHitOverride;
    private String pepOverride;
    private String specialInterestOverride;
    private String docVerificationOverride;
    private String adverseMediaOverride;
    private String transactionOverride;

    private String overallRatingCalculated;
    private String overallRatingFinal;
}
