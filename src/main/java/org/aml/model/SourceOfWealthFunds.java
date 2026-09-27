package org.aml.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "source_of_wealth_funds")
@Getter
@Setter
public class SourceOfWealthFunds {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(columnDefinition = "TEXT")
    private String sourceOfWealth;
    @Column(columnDefinition = "TEXT")
    private String sourceOfWealthOther;
    @Column(columnDefinition = "TEXT")
    private String sourceOfFunds;
    @Column(columnDefinition = "TEXT")
    private String sourceOfFundsOther;
}
