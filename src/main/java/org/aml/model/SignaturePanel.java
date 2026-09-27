package org.aml.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "signature_panel")
@Getter
@Setter
public class SignaturePanel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(columnDefinition = "TEXT")
    private String signature;
    private String name;
    private String position;
    private String date;
}
