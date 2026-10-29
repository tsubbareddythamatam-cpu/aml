package org.aml.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.aml.service.ComplianceService;
import org.aml.service.PdfPdfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequestMapping("/api/pdf")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "PDF Controller", description = "Handles PDF file reading and text extraction.")
public class PdfController {

    private static final Logger logger = LoggerFactory.getLogger(PdfController.class);

    private final PdfPdfService pdfService;
    private final ComplianceService complianceService;

    /**
     * Creates the PDF controller with the PDF text extraction service.
     *
     * @param pdfService service used to extract PDF text
     */
    public PdfController(PdfPdfService pdfService, ComplianceService complianceService) {
        this.pdfService = pdfService;
        this.complianceService = complianceService;
    }

    /**
     * Validates an uploaded PDF and returns its extracted text.
     *
     * @param file uploaded PDF file
     * @return extracted text or an error response
     */
    @PostMapping("/read")
    public ResponseEntity<String> readPdf(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || !"application/pdf".equals(file.getContentType())) {
            logger.warn("Rejected empty upload or upload with a non-PDF content type");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Please upload a valid PDF file.");
        }

        try {
            String text = pdfService.extractTextFromPdf(file);
            return ResponseEntity.ok(text);
        } catch (IOException e) {
            logger.error("Unable to extract text from uploaded PDF", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error reading the PDF file: " + e.getMessage());
        }
    }

    /**
     * Exports compliance profile, risk, screening, and audit sections without KYC data.
     *
     * @param amlId AML identifier of the compliance profile
     * @return downloadable compliance PDF
     * @throws IOException if the PDF document cannot be generated
     */
    @Operation(summary = "Export compliance report as PDF", description = "Downloads the compliance, risk, screening, and audit sections by AML ID. Requires admin privileges.")
    @GetMapping(value = "/export/{amlId}/compliance", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportComplianceReport(@PathVariable String amlId) throws IOException {
        byte[] pdf = pdfService.exportComplianceReport(complianceService.fetchByAmlId(amlId));
        String safeFilename = amlId.replaceAll("[^A-Za-z0-9._-]", "_");
        logger.info("Exported compliance PDF for AML ID {}", amlId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"compliance-" + safeFilename + ".pdf\"")
                .body(pdf);
    }

    /**
     * Exports only the KYC sections for a compliance profile as a downloadable PDF.
     *
     * @param amlId AML identifier of the compliance profile
     * @return downloadable KYC PDF
     * @throws IOException if the PDF document cannot be generated
     */
    @Operation(summary = "Export KYC report as PDF", description = "Downloads the KYC information by AML ID. Requires admin privileges.")
    @GetMapping(value = "/export/{amlId}/kyc", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportKycReport(@PathVariable String amlId) throws IOException {
        byte[] pdf = pdfService.exportKycReport(complianceService.fetchByAmlId(amlId));
        String safeFilename = amlId.replaceAll("[^A-Za-z0-9._-]", "_");
        logger.info("Exported KYC PDF for AML ID {}", amlId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"kyc-" + safeFilename + ".pdf\"")
                .body(pdf);
    }
}
