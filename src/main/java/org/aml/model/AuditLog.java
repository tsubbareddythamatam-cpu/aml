package org.aml.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aml_id", nullable = false)
    private Customer customer;

    private LocalDate actionDate;
    private String actionedBy;

    @Column(columnDefinition = "TEXT")
    private String actionTaken;
}
