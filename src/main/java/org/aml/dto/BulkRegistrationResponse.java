package org.aml.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.aml.exception.ErrorResponse;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BulkRegistrationResponse {

    private int totalRecords;
    private int successRecords;
    private int failedRecords;
    private List<String> failedEmails;
    private List<ErrorResponse> errors;
}