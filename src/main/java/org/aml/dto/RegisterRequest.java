package org.aml.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest implements Serializable {

    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String role;

    private String companyName;
}
