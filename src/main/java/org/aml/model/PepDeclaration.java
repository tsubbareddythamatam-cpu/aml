package org.aml.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "pep_declaration")
@Getter
@Setter
public class PepDeclaration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String currentlyHoldPublicPosition;
    private String heldPublicPositionLast12Months;
    private String everHeldPublicPosition;
    private String diplomaticImmunity;
    private String relativeHeldPublicPositionLast12Months;
    private String closeAssociateHeldPublicPositionLast12Months;
    private String courtConviction;
    @Column(columnDefinition = "TEXT")
    private String details;
}
