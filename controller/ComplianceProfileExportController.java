package org.aml.controller;

import org.aml.dto.CustomerDto;
import org.aml.dto.ReviewExportField;
import org.aml.service.ComplianceProfileExportService;
import org.aml.service.ComplianceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/portal/compliance/export")
public class ComplianceProfileExportController {

    private final ComplianceService complianceService;
    private final ComplianceProfileExportService exportService;

    public ComplianceProfileExportController(ComplianceService complianceService,
                                             ComplianceProfileExportService exportService) {
        this.complianceService = complianceService;
        this.exportService = exportService;
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> exportExcel(@RequestParam(defaultValue = "ALL") String filter,
                                              @RequestParam(defaultValue = "") String search) throws IOException {
        byte[] workbook = exportService.exportExcel(filteredReports(filter, search));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"aml-customer-profiles.xlsx\"")
                .body(workbook);
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> exportPdf(@RequestParam(defaultValue = "ALL") String filter,
                                            @RequestParam(defaultValue = "") String search) throws IOException {
        byte[] document = exportService.exportPdf(filteredReports(filter, search));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"aml-customer-profiles.pdf\"")
                .body(document);
    }

    @PostMapping("/review/{format}")
    public ResponseEntity<byte[]> exportReview(@PathVariable String format,
                                               @Valid @RequestBody @Size(min = 1, max = 100)
                                               List<@Valid ReviewExportField> fields) throws IOException {
        if ("pdf".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"aml-onboarding-review.pdf\"")
                    .body(exportService.exportReviewPdf(fields));
        }
        if ("excel".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"aml-onboarding-review.xlsx\"")
                    .body(exportService.exportReviewExcel(fields));
        }
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Choose PDF or Excel export format.");
    }

    @GetMapping("/{amlId}/{format}")
    public ResponseEntity<byte[]> exportSingleProfile(@PathVariable String amlId,
                                                      @PathVariable String format) throws IOException {
        CustomerDto report = complianceService.fetchByAmlId(amlId);
        if ("pdf".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"aml-profile-" + safeFilename(amlId) + ".pdf\"")
                    .body(exportService.exportPdf(List.of(report)));
        }
        if ("excel".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"aml-profile-" + safeFilename(amlId) + ".xlsx\"")
                    .body(exportService.exportExcel(List.of(report)));
        }
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Choose PDF or Excel export format.");
    }

    private List<CustomerDto> filteredReports(String filter, String search) {
        String normalizedFilter = filter.trim().toUpperCase(Locale.ROOT);
        String normalizedSearch = search.trim().toLowerCase(Locale.ROOT);
        return complianceService.fetchAll().stream()
                .filter(report -> matchesFilter(report, normalizedFilter))
                .filter(report -> searchableText(report).contains(normalizedSearch))
                .toList();
    }

    private boolean matchesFilter(CustomerDto report, String filter) {
        return switch (filter) {
            case "ALL" -> true;
            case "INDIVIDUAL", "COMPANY" -> filter.equalsIgnoreCase(report.getCategory());
            case "PENDING" -> hasStatus(report, "PENDING");
            case "IN_REVIEW" -> "IN_REVIEW".equals(report.getStatus());
            case "APPROVED" -> hasStatus(report, "APPROVED", "ACTIVE");
            case "REJECTED" -> "REJECTED".equals(report.getStatus());
            default -> false;
        };
    }

    private boolean hasStatus(CustomerDto report, String... statuses) {
        return report.getStatus() != null && List.of(statuses).contains(report.getStatus());
    }

    private String searchableText(CustomerDto report) {
        return String.join(" ", safe(report.getAmlId()), safe(report.getFullName()),
                safe(report.getCountry()), safe(report.getCompanyName()), safe(report.getOnboardedByUser()))
                .toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safeFilename(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
