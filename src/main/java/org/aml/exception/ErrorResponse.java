package org.aml.exception;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ErrorResponse {

    private String errorCode;
    private String message;
    private int status;
    private LocalDateTime timestamp;
}