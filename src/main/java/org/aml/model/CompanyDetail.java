package org.aml.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "company_details")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String companyName;
    private LocalDate dateOfIncorporation;
    private String countryOfOperation;
    private String countryOfDomicile;

    @Column(unique = true)
    private String registrationNo;

    private LocalDate registrationNoExpiryDate;
    private String product;
    private String industry;

    // Optional: Back-reference link to the parent user
    @OneToOne(mappedBy = "companyDetail")
    private User user;
}
