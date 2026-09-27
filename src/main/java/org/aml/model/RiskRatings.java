package org.aml.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "risk_ratings_and_overrides")
@Getter
@Setter
public class RiskRatings {
    @Id
    @Column(name = "aml_id")
    private String amlId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "aml_id")
    private Customer customer;

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
    private String SanctionHitOverride;
    private String nonResidentOverride;
    private String sanctionedResidenceOverride;
    private String sanctionedNationalityOverride;
    private String sanctionedPhoneCodeOverride;
    private String pepOverride;
    private String specialInterestOverride;
    private String docVerificationOverride;
    private String adverseMediaOverride;
    private String transactionOverride;

    private String overallRatingCalculated;
    private String overallRatingFinal;
}
