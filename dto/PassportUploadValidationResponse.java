package org.aml.dto;

public record PassportUploadValidationResponse(
        boolean valid,
        String message,
        PassportDetailsExtraction extractedDetails) {
}
