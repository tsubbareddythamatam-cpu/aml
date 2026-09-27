package org.aml.dto;

import lombok.Data;

@Data
public class PasswordSetupRequest {
    private String token;
    private String password;
}
