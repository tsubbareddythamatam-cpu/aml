package org.aml.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycDataDto {
    private CustomerInformation customerInformation;
    private ProfileInformation profileInformation;
    private ContactInformation contactInformation;
    private PepDeclaration pepDeclaration;
    private SourceOfWealthFunds sourceOfWealthFunds;
    private InvestmentRange investmentRange;
    private OngoingDueDiligence ongoingDueDiligence;
    private SignaturePanel signaturePanel;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CustomerInformation {
        private String amlReferenceId, amlDateTime, amlStatus, amlType, fullName, individualType, fatherName, gender, dateOfBirth,
                nationality, countryOfResidence, residentStatus, nationalIdNumber, nationalIdExpiry,
                passportNumber, passportExpiry, otherNationalities, isCrs, onboardedBy,
                onboardingDate, externalReference, recordLastUpdated, footerText;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ProfileInformation {
        private String workType, industry, deliveryChannel, relationshipStartDate, products, productOffered;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ContactInformation {
        private String address, townCity, countyState, postalCode, contactNumber, emailAddress;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PepDeclaration {
        private String currentlyHoldPublicPosition, heldPublicPositionLast12Months, everHeldPublicPosition,
                diplomaticImmunity, relativeHeldPublicPositionLast12Months,
                closeAssociateHeldPublicPositionLast12Months, courtConviction, details;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SourceOfWealthFunds {
        private String sourceOfWealth, sourceOfWealthOther, sourceOfFunds, sourceOfFundsOther;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class InvestmentRange {
        private String investmentRange;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OngoingDueDiligence {
        private String lastReview, nextReview;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SignaturePanel {
        private String signature, name, position, date;
    }
}
