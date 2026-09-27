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
