package org.aml.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ongoing_due_diligence")
@Getter
@Setter
public class OngoingDueDiligence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String lastReview;
    private String nextReview;
}
