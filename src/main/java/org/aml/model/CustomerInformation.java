package org.aml.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "customer_information")
@Getter
@Setter
public class CustomerInformation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "aml_reference_id")
    private String amlReferenceId;
    @Column(name = "aml_date_time")
    private String amlDateTime;
    @Column(name = "aml_status")
    private String amlStatus;
    @Column(name = "aml_type")
    private String amlType;
    private String fullName;
    private String individualType;
    private String fatherName;
    private String gender;
    private String dateOfBirth;
    private String nationality;
    private String countryOfResidence;
    private String residentStatus;
    private String nationalIdNumber;
    private String nationalIdExpiry;
    private String passportNumber;
    private String passportExpiry;
    private String otherNationalities;
    private String isCrs;
    private String onboardedBy;
    private String onboardingDate;
    private String externalReference;
    private String recordLastUpdated;
    @Column(columnDefinition = "TEXT")
    private String footerText;
}
