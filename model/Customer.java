package org.aml.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customers")
@Getter
@Setter
public class Customer {

    @Id
    @Column(name = "aml_id", length = 50)
    private String amlId;

    @Column(name = "user_id")
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

    @Column(columnDefinition = "TEXT")
    private String comment;

    private LocalDateTime registeredAt;
    private LocalDate lastReviewDate;
    private LocalDateTime reportGeneratedOn;

    private String companyName;
    private String positionInCompany;
    @Column(length = 20)
    private String category;
    @Column(length = 100)
    private String country;

    @Column(length = 80)
    private String passportDocumentKey;

    @Column(length = 255)
    private String passportDocumentName;

    @Column(length = 100)
    private String passportImageType;

    @Column(length = 30)
    private String passportImageQualityStatus;

    @Column(length = 30)
    private String passportVerificationStatus;

    @Column(length = 80)
    private String sourceOfWealthEvidenceKey;
    @Column(length = 255)
    private String sourceOfWealthEvidenceName;
    @Column(length = 100)
    private String sourceOfWealthEvidenceType;
    @Column(length = 80)
    private String sourceOfFundsEvidenceKey;
    @Column(length = 255)
    private String sourceOfFundsEvidenceName;
    @Column(length = 100)
    private String sourceOfFundsEvidenceType;

    @Column(length = 160)
    private String passportFullName;

    @Column(name = "passport_first_name", length = 80)
    private String passportFirstName;

    @Column(name = "passport_last_name", length = 80)
    private String passportLastName;

    @Column(length = 80)
    private String verifiedPassportNumber;

    @Column(name = "passport_type", length = 10)
    private String passportType;

    @Column(name = "passport_issuing_country_code", length = 3)
    private String passportIssuingCountryCode;

    private LocalDate passportExpiryDate;

    @Column(name = "passport_date_of_issue")
    private LocalDate passportDateOfIssue;

    @Column(name = "passport_issuing_authority", length = 120)
    private String passportIssuingAuthority;

    private String passportVerifiedBy;
    private LocalDateTime passportVerifiedAt;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "customer_information_id")
    private CustomerInformation customerInformation;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "profile_information_id")
    private ProfileInformation profileInformation;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "contact_information_id")
    private ContactInformation contactInformation;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "pep_declaration_id")
    private PepDeclaration pepDeclaration;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "source_of_wealth_funds_id")
    private SourceOfWealthFunds sourceOfWealthFunds;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "investment_range_id")
    private InvestmentRange investmentRange;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "ongoing_due_diligence_id")
    private OngoingDueDiligence ongoingDueDiligence;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "signature_panel_id")
    private SignaturePanel signaturePanel;

    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private RiskRatings riskRatingsAndOverrides;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScreeningHit> screeningHits = new ArrayList<>();

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AuditLog> auditLogs = new ArrayList<>();
}
