package org.aml.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // 👈 Automatically hides any field or detail if it is null
public class LoginResponse {
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String companyName;
    private String country;
    private String role;
    private String token;

    // 🆕 Expose full corporate metadata structure matching UserResponse layout
    private CompanyDetailDto companyDetail;
}
