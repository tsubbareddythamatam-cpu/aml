package org.aml.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "profile_information")
@Getter
@Setter
public class ProfileInformation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String workType;
    private String industry;
    private String deliveryChannel;
    private String relationshipStartDate;
    private String products;
    private String productOffered;
}
