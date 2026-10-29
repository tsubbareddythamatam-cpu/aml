package org.aml.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewExportField(
        @NotBlank @Size(max = 120) String label,
        @NotBlank @Size(max = 2000) String value) {
}
