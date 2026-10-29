package org.aml.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "screening_hit_details")
@Getter
@Setter
public class ScreeningHit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aml_id", nullable = false)
    private Customer customer;

    private String hitName;
    private String category;
    private String source;
    private Integer score;
    private String hitDetermination;

    @Column(columnDefinition = "TEXT")
    private String comments;
}
