package org.aml.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.aml.annotation.AuthenticatedToken;
import org.aml.dto.CustomerDto;
import org.aml.repository.UserRepository;
import org.aml.service.ComplianceService;
import org.aml.service.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/compliance")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Compliance Controller", description = "Handles compliance report management and database reset operations.")
public class ComplianceController {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceController.class);

    private final ComplianceService complianceService;
    private final JwtService jwtService;

    /**
     * Creates the controller with compliance and token services.
     *
     * @param complianceService service for compliance profile operations
     * @param jwtService service for reading the authenticated user identifier
     */
    public ComplianceController(ComplianceService complianceService, JwtService jwtService) {
        this.complianceService = complianceService;
        this.jwtService = jwtService;
    }

    /**
     * Creates a compliance profile for the authenticated user.
     *
     * @param token authenticated administrator token
     * @param dto profile fields and related KYC data to save
     * @return the created profile and its related data
     */
    @Operation(summary = "Create a new compliance report", description = "Creates a new compliance report for a user. Requires admin privileges.")
    @PostMapping("/reports")
    public ResponseEntity<CustomerDto> createComplianceReport(
            @Parameter(hidden = true) @AuthenticatedToken String token,
            @RequestBody CustomerDto dto) {
        Long userId = jwtService.extractUserId(token);
        dto.setOnboardedByUser(jwtService.extractEmail(token));
        if (dto.getUserId() == null) {
            dto.setUserId(userId);
        }
        ResponseEntity<CustomerDto> response =
                new ResponseEntity<>(complianceService.processReport(dto), HttpStatus.CREATED);
        logger.info("Compliance report created by authenticated userId={}", userId);
        return response;
    }

    /**
     * Retrieves every compliance profile, including associated KYC, risk, screening, and audit data.
     *
     * @param token authenticated administrator token
     * @return all compliance profiles
     */
    @Operation(summary = "Fetch all compliance reports", description = "Retrieves a list of all compliance reports in the system. Requires admin privileges.")
    @GetMapping("/reports")
    public ResponseEntity<List<CustomerDto>> getAllComplianceReports(
            @Parameter(hidden = true) @AuthenticatedToken String token) {
        List<CustomerDto> reports = complianceService.fetchAll();
        logger.debug("Returning {} compliance reports", reports.size());
        return ResponseEntity.ok(reports);
    }

    /**
     * Retrieves a compliance profile and its associated data by AML identifier.
     *
     * @param token authenticated administrator token
     * @param amlId AML identifier to look up
     * @return the matching compliance profile
     */
    @Operation(summary = "Fetch compliance report by AML ID", description = "Retrieves the details of a specific compliance report by its AML ID. Requires admin privileges.")
    @GetMapping("/reports/{amlId}")
    public ResponseEntity<CustomerDto> getComplianceReportByAMLId(
            @Parameter(hidden = true) @AuthenticatedToken String token,
            @PathVariable String amlId) {
        return ResponseEntity.ok(complianceService.fetchByAmlId(amlId));
    }

    /**
     * Retrieves a compliance profile and its associated data by user identifier.
     *
     * @param token authenticated administrator token
     * @param userId user identifier to look up
     * @return the matching compliance profile
     */
    @Operation(summary = "Fetch compliance report by user ID", description = "Retrieves the details of a specific compliance report by the associated user ID. Requires admin privileges.")
    @GetMapping("/reports/user/{userId}")
    public ResponseEntity<CustomerDto> getComplianceReportByUserId(
            @Parameter(hidden = true) @AuthenticatedToken String token,
            @PathVariable Long userId) {
        logger.debug("Fetching compliance report for userId={}", userId);
        return ResponseEntity.ok(complianceService.fetchByUserId(userId));
    }

    /**
     * Deletes all compliance profiles and their related records.
     *
     * @param token authenticated administrator token
     * @return operation status and completion timestamp
     */
    @Operation(summary = "Reset compliance database", description = "Resets the compliance database, removing all reports and related data. Requires admin privileges.")
    @DeleteMapping("/reset")
    public ResponseEntity<Map<String, Object>> purgeComplianceDatabaseTables(
            @Parameter(hidden = true) @AuthenticatedToken String token) {
        logger.warn("Received request to reset compliance database");
        complianceService.resetComplianceDatabase();

        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Database successfully reset. All profiles and cascaded child entries have been wiped clean.");
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    /**
     * Deletes one compliance profile and its related records by AML ID.
     *
     * @param token authenticated administrator token
     * @param amlId AML identifier of the profile to delete
     * @return no content after successful deletion
     */
    @Operation(summary = "Delete compliance report by AML ID",
            description = "Deletes one compliance report and its related KYC, risk, screening, and audit records. Requires admin privileges.")
    @DeleteMapping("/reports/{amlId}")
    public ResponseEntity<Void> deleteComplianceReport(
            @Parameter(hidden = true) @AuthenticatedToken String token,
            @PathVariable String amlId) {
        complianceService.deleteByAmlId(amlId);
        logger.info("Compliance report deleted by AML ID");
        return ResponseEntity.noContent().build();
    }

    /**
     * Updates supplied compliance fields and returns the complete updated record.
     *
     * @param token authenticated administrator token
     * @param amlId AML identifier of the profile to update
     * @param dto profile fields and related data to update
     * @return the updated profile and its related data
     */
    @Operation(summary = "Update compliance report", description = "Updates the supplied fields of a specific compliance report by AML ID. AML ID remains unchanged. Requires admin privileges.")
    @PutMapping("/reports/{amlId}")
    public ResponseEntity<CustomerDto> updateComplianceReport(
            @Parameter(hidden = true) @AuthenticatedToken String token,
            @PathVariable String amlId,
            @RequestBody CustomerDto dto) {

        Long userId = jwtService.extractUserId(token);
        dto.setOnboardedByUser(jwtService.extractEmail(token));
        CustomerDto updatedReport = complianceService.updateReport(amlId, dto);
        logger.info("Compliance report updated by authenticated userId={}", userId);
        return ResponseEntity.ok(updatedReport);
    }
}
