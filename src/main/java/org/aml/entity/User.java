package org.aml.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;

    private String lastName;

    @Column(unique = true, nullable = false)
    private String email;

    private String companyName;

    @Column(unique = true, nullable = false)
    private String phoneNumber;

    private String country;

    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;
}