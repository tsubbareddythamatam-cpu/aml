package org.aml.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class) // 1. Enables framework-level auditing tracking hooks
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;

    private String lastName;

    @Column(unique = true, nullable = false)
    private String email;

    private String companyName;

    private String phoneNumber;

    private String country;

    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    @CreatedDate // 2. Automatically populates when the record is created
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate // 3. Automatically populates whenever the record is updated
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private String resetToken;

    private LocalDateTime resetTokenExpiry;

    // 🆕 Link to the new CompanyDetail table structure
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "company_detail_id", referencedColumnName = "id")
    private CompanyDetail companyDetail;
}
