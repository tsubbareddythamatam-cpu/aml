package org.aml.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyUpdateDto {
    private String companyName;
    private LocalDate dateOfIncorporation;
    private String countryOfOperation;
    private String countryOfDomicile;
    private String registrationNo;
    private LocalDate registrationNoExpiryDate;
    private String product;
    private String industry;
}