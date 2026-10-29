package org.aml.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.aml.dto.AuditLogDto;
import org.aml.dto.CustomerDto;
import org.aml.dto.KycDataDto;
import org.aml.dto.RiskRatingsDto;
import org.aml.dto.ScreeningHitDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfPdfService {

    private static final Logger logger = LoggerFactory.getLogger(PdfPdfService.class);
    private static final float PAGE_MARGIN = 48;
    private static final float CONTENT_WIDTH = PDRectangle.A4.getWidth() - (PAGE_MARGIN * 2);
    private static final float BODY_FONT_SIZE = 10;
    private static final float BODY_LINE_HEIGHT = 14;

    /**
     * Extracts plain text from an uploaded PDF and prints the extracted text to the console.
     *
     * @param file uploaded PDF file
     * @return extracted text
     * @throws IOException if the PDF cannot be read
     */
    public String extractTextFromPdf(MultipartFile file) throws IOException {
        logger.debug("Extracting text from uploaded PDF");
        // Load the PDF file into a PDDocument
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            // Instantiate PDFTextStripper to parse text
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String extractedText = pdfStripper.getText(document);
            System.out.println("Extracted PDF text:");
            System.out.println(extractedText);
            logger.info("Extracted {} characters from uploaded PDF", extractedText.length());
            return extractedText;
        }
    }

    /**
     * Creates a formatted PDF report containing all compliance and KYC data.
     *
     * @param report complete compliance report to export
     * @return generated PDF document bytes
     * @throws IOException if PDF generation fails
     */
    public byte[] exportComplianceReport(CustomerDto report) throws IOException {
        return generateReport(report, "Compliance Report", buildComplianceLines(report));
    }

    /**
     * Creates a formatted PDF containing only the KYC information for a compliance profile.
     *
     * @param report complete compliance report containing KYC data
     * @return generated KYC PDF document bytes
     * @throws IOException if PDF generation fails
     */
    public byte[] exportKycReport(CustomerDto report) throws IOException {
        return generateReport(report, "KYC Report", buildKycReportLines(report));
    }

    /**
     * Generates a paginated PDF using the shared colored report-table layout.
     *
     * @param report source compliance record
     * @param title report title displayed on each page
     * @param lines table content for the export
     * @return generated PDF document bytes
     * @throws IOException if PDF generation fails
     */
    private byte[] generateReport(CustomerDto report, String title, List<ReportLine> lines) throws IOException {
        PDFont regularFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDFont boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        List<List<ReportLine>> pages = paginate(lines, regularFont, boldFont);

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                drawPage(document, page, pages.get(pageIndex), title, report.getAmlId(),
                        pageIndex + 1, pages.size(), regularFont, boldFont);
            }
            document.save(output);
            logger.info("Generated {} for AML ID {} with {} page(s)", title, report.getAmlId(), pages.size());
            return output.toByteArray();
        }
    }

    /**
     * Collects compliance, risk, screening, and audit data into report sections, excluding KYC.
     *
     * @param report compliance report to render
     * @return report headings and field lines in document order
     */
    private List<ReportLine> buildComplianceLines(CustomerDto report) {
        List<ReportLine> lines = new ArrayList<>();
        addSection(lines, "Customer Information");
        addField(lines, "AML ID", report.getAmlId());
        addField(lines, "User ID", report.getUserId());
        addField(lines, "External Reference Number", report.getExternalRefNumber());
        addField(lines, "Full Name", report.getFullName());
        addField(lines, "Other Nationalities", report.getOtherNationalities());
        addField(lines, "Delivery Channel", report.getDeliveryChannel());
        addField(lines, "Transaction Type", report.getTransactionType());
        addField(lines, "Name Screening Hit", yesNo(report.getNameScreeningHit()));
        addField(lines, "Documents Verification Hit", yesNo(report.getDocumentsVerificationHit()));
        addField(lines, "Risk Rating Hit", yesNo(report.getRiskRatingHit()));
        addField(lines, "Status", report.getStatus());
        addField(lines, "Onboarded by (user)", report.getOnboardedByUser());
        addField(lines, "Onboarded by (company)", report.getOnboardedByCompany());
        addField(lines, "Registered At", report.getRegisteredAt());
        addField(lines, "Comment", report.getComment());
        addField(lines, "Last Review Date", report.getLastReviewDate());
        addField(lines, "Report Generated On", report.getReportGeneratedOn());
        addField(lines, "Company Name", report.getCompanyName());
        addField(lines, "Position In Company", report.getPositionInCompany());

        addRiskLines(lines, report.getRiskRatingsAndOverrides());
        addScreeningLines(lines, report.getScreeningHits());
        addAuditLines(lines, report.getAuditLogs());
        return lines;
    }

    /**
     * Formats nullable compliance hit flags as YES/NO without hiding missing values.
     *
     * @param value stored hit flag
     * @return YES, NO, or Not provided
     */
    private String yesNo(Boolean value) {
        return value == null ? "Not provided" : value ? "YES" : "NO";
    }

    /**
     * Collects KYC information only into report sections.
     *
     * @param report compliance report containing the KYC payload
     * @return KYC section headings and fields in document order
     */
    private List<ReportLine> buildKycReportLines(CustomerDto report) {
        List<ReportLine> lines = new ArrayList<>();
        addSection(lines, "KYC Record");
        addField(lines, "AML ID", report.getAmlId());
        addField(lines, "Compliance Profile Name", report.getFullName());
        addKycLines(lines, report.getKycData());
        return lines;
    }

    /**
     * Adds all present KYC sections and fields to the report lines.
     *
     * @param lines report lines being built
     * @param kyc KYC details, if present
     */
    private void addKycLines(List<ReportLine> lines, KycDataDto kyc) {
        KycDataDto.CustomerInformation customer = kyc == null ? null : kyc.getCustomerInformation();
        KycDataDto.ProfileInformation profile = kyc == null ? null : kyc.getProfileInformation();
        KycDataDto.ContactInformation contact = kyc == null ? null : kyc.getContactInformation();
        KycDataDto.PepDeclaration pep = kyc == null ? null : kyc.getPepDeclaration();
        KycDataDto.SourceOfWealthFunds source = kyc == null ? null : kyc.getSourceOfWealthFunds();
        KycDataDto.InvestmentRange investment = kyc == null ? null : kyc.getInvestmentRange();
        KycDataDto.OngoingDueDiligence review = kyc == null ? null : kyc.getOngoingDueDiligence();
        KycDataDto.SignaturePanel signature = kyc == null ? null : kyc.getSignaturePanel();

        addSection(lines, "Customer Information");
        addField(lines, "AML ID", customer == null ? null : customer.getAmlReferenceId());
        addField(lines, "Date/Time", customer == null ? null : customer.getAmlDateTime());
        addField(lines, "Name", customer == null ? null : customer.getFullName());
        addField(lines, "Status", customer == null ? null : customer.getAmlStatus());
        addField(lines, "Type", customer == null ? null : customer.getAmlType());
        addField(lines, "Individual Type", customer == null ? null : customer.getIndividualType());
        addField(lines, "Father Name", customer == null ? null : customer.getFatherName());
        addField(lines, "Gender", customer == null ? null : customer.getGender());
        addField(lines, "Date of Birth", customer == null ? null : customer.getDateOfBirth());
        addField(lines, "Nationality", customer == null ? null : customer.getNationality());
        addField(lines, "Country of Residence", customer == null ? null : customer.getCountryOfResidence());
        addField(lines, "Resident Status", customer == null ? null : customer.getResidentStatus());
        addField(lines, "National ID Number", customer == null ? null : customer.getNationalIdNumber());
        addField(lines, "National ID Expiry", customer == null ? null : customer.getNationalIdExpiry());
        addField(lines, "Passport Number", customer == null ? null : customer.getPassportNumber());
        addField(lines, "Passport Expiry", customer == null ? null : customer.getPassportExpiry());
        addField(lines, "Other Nationalities", customer == null ? null : customer.getOtherNationalities());
        addField(lines, "Is CRS", customer == null ? null : customer.getIsCrs());
        addField(lines, "Onboarded By", customer == null ? null : customer.getOnboardedBy());
        addField(lines, "Onboarding Date", customer == null ? null : customer.getOnboardingDate());
        addField(lines, "External Reference", customer == null ? null : customer.getExternalReference());
        addField(lines, "Record Last Updated", customer == null ? null : customer.getRecordLastUpdated());

        addSection(lines, "Profile Information");
        addField(lines, "Work Type", profile == null ? null : profile.getWorkType());
        addField(lines, "Industry", profile == null ? null : profile.getIndustry());
        addField(lines, "Delivery Channel", profile == null ? null : profile.getDeliveryChannel());
        addField(lines, "Relationship Start Date", profile == null ? null : profile.getRelationshipStartDate());
        addField(lines, "Products", profile == null ? null : profile.getProducts());
        addField(lines, "Product Offered", profile == null ? null : profile.getProductOffered());

        addSection(lines, "Contact Information");
        addField(lines, "Address", contact == null ? null : contact.getAddress());
        addField(lines, "Town/City", contact == null ? null : contact.getTownCity());
        addField(lines, "County/State", contact == null ? null : contact.getCountyState());
        addField(lines, "Postal Code", contact == null ? null : contact.getPostalCode());
        addField(lines, "Contact Number", contact == null ? null : contact.getContactNumber());
        addField(lines, "Email Address", contact == null ? null : contact.getEmailAddress());

        addSection(lines, "PEP Declaration");
        addField(lines, "Do you currently hold any public position?",
                pep == null ? null : pep.getCurrentlyHoldPublicPosition());
        addField(lines, "Did you hold public position in the last 12 months?",
                pep == null ? null : pep.getHeldPublicPositionLast12Months());
        addField(lines, "Ever held any public position?",
                pep == null ? null : pep.getEverHeldPublicPosition());
        addField(lines, "Do you have or have you ever had, diplomatic immunity?",
                pep == null ? null : pep.getDiplomaticImmunity());
        addField(lines, "Do you have a relative who has held public position in the last 12 months?",
                pep == null ? null : pep.getRelativeHeldPublicPositionLast12Months());
        addField(lines, "Do you have a close associate who has held public position in the last 12 months?",
                pep == null ? null : pep.getCloseAssociateHeldPublicPositionLast12Months());
        addField(lines, "Has there been a conviction against you by a court of law?",
                pep == null ? null : pep.getCourtConviction());
        addField(lines, "If you answered yes to any of the above, details",
                pep == null ? null : pep.getDetails());

        addSection(lines, "Source of Wealth & Funds");
        addField(lines, "Source of Wealth", source == null ? null : source.getSourceOfWealth());
        addField(lines, "Source of Wealth - Other", source == null ? null : source.getSourceOfWealthOther());
        addField(lines, "Source of Funds", source == null ? null : source.getSourceOfFunds());
        addField(lines, "Source of Funds - Other", source == null ? null : source.getSourceOfFundsOther());

        addSection(lines, "Investment Range");
        addField(lines, "Investment Range", investment == null ? null : investment.getInvestmentRange());

        addSection(lines, "On-Going Due Diligence");
        addField(lines, "Last Review", review == null ? null : review.getLastReview());
        addField(lines, "Next Review", review == null ? null : review.getNextReview());

        addSection(lines, "Signature Panel");
        addField(lines, "Signature", signature == null ? null : signature.getSignature());
        addField(lines, "Name", signature == null ? null : signature.getName());
        addField(lines, "Position", signature == null ? null : signature.getPosition());
        addField(lines, "Date", signature == null ? null : signature.getDate());
        addField(lines, "Footer", customer == null ? null : customer.getFooterText());
    }

    /**
     * Adds risk ratings and override fields to the report.
     *
     * @param lines report lines being built
     * @param risk risk ratings, if present
     */
    private void addRiskLines(List<ReportLine> lines, RiskRatingsDto risk) {
        addSection(lines, "Key Findings");
        if (risk == null) {
            addField(lines, "Total Matches", null);
            addField(lines, "Resolved Matches (Genuine / Not Genuine)", null);
            addField(lines, "Unresolved Matches", null);
            addSection(lines, "Risk Ratings");
            addField(lines, "Country of Residence (Score / Level)", null);
            addField(lines, "Delivery Channel (Score / Level)", null);
            addField(lines, "Industry (Score / Level)", null);
            addField(lines, "Nationality (Score / Level)", null);
            addField(lines, "Product (Score / Level)", null);
            addField(lines, "Anti Spoofing (Score / Level)", null);
            addField(lines, "Base Rating (Score / Level)", null);
            addSection(lines, "Risk Factor Override");
            addOverride(lines, "Suspicious Transaction Report filed", null);
            addOverride(lines, "Non Resident", null);
            addOverride(lines, "Residence Country is Sanctioned", null);
            addOverride(lines, "Nationality Country is Sanctioned", null);
            addOverride(lines, "Contact No. Code Country is Sanctioned", null);
            addOverride(lines, "Sanction Hit", null);
            addOverride(lines, "PEP", null);
            addOverride(lines, "Special Interest Hit", null);
            addOverride(lines, "Document Verification", null);
            addOverride(lines, "Adverse Media Hit", null);
            addOverride(lines, "Transaction", null);
            addOverride(lines, "Overall Rating", null);
            return;
        }
        addField(lines, "Total Matches", risk.getTotalMatches());
        addField(lines, "Resolved Matches (Genuine / Not Genuine)",
                valueOrMissing(risk.getResolvedMatches()) + " (Genuine: " + valueOrMissing(risk.getGenuineMatches())
                        + ", Not Genuine: " + valueOrMissing(risk.getNotGenuineMatches()) + ")");
        addField(lines, "Unresolved Matches", risk.getUnresolvedMatches());

        addSection(lines, "Risk Ratings");
        addField(lines, "Country of Residence (Score / Level)",
                scoreAndLevel(risk.getCountryResidenceScore(), risk.getCountryResidenceLevel()));
        addField(lines, "Delivery Channel (Score / Level)",
                scoreAndLevel(risk.getDeliveryChannelScore(), risk.getDeliveryChannelLevel()));
        addField(lines, "Industry (Score / Level)",
                scoreAndLevel(risk.getIndustryScore(), risk.getIndustryLevel()));
        addField(lines, "Nationality (Score / Level)",
                scoreAndLevel(risk.getNationalityScore(), risk.getNationalityLevel()));
        addField(lines, "Product (Score / Level)",
                scoreAndLevel(risk.getProductScore(), risk.getProductLevel()));
        addField(lines, "Anti Spoofing (Score / Level)",
                scoreAndLevel(risk.getAntiSpoofingScore(), risk.getAntiSpoofingLevel()));
        addField(lines, "Base Rating (Score / Level)",
                scoreAndLevel(risk.getBaseRatingScore(), risk.getBaseRatingLevel()));

        addSection(lines, "Risk Factor Override");
        addOverride(lines, "Suspicious Transaction Report filed", risk.getStrFiledOverride());
        addOverride(lines, "Non Resident", risk.getNonResidentOverride());
        addOverride(lines, "Residence Country is Sanctioned", risk.getSanctionedResidenceOverride());
        addOverride(lines, "Nationality Country is Sanctioned", risk.getSanctionedNationalityOverride());
        addOverride(lines, "Contact No. Code Country is Sanctioned", risk.getSanctionedPhoneCodeOverride());
        addOverride(lines, "Sanction Hit", risk.getSanctionHitOverride());
        addOverride(lines, "PEP", risk.getPepOverride());
        addOverride(lines, "Special Interest Hit", risk.getSpecialInterestOverride());
        addOverride(lines, "Document Verification", risk.getDocVerificationOverride());
        addOverride(lines, "Adverse Media Hit", risk.getAdverseMediaOverride());
        addOverride(lines, "Transaction", risk.getTransactionOverride());
        addOverride(lines, "Overall Rating",
                valueOrMissing(risk.getOverallRatingCalculated()) + " / "
                        + valueOrMissing(risk.getOverallRatingFinal()));

    }

    /**
     * Adds an override row using the fields available in the compliance DTO.
     *
     * @param lines report lines being built
     * @param name override label
     * @param value saved override value
     */
    private void addOverride(List<ReportLine> lines, String name, String value) {
        addField(lines, name + " (Override To / Level)", valueOrMissing(value) + " / Not provided");
    }

    /**
     * Combines a risk score and level for display in a matrix row.
     *
     * @param score risk score
     * @param level risk level
     * @return formatted score and level
     */
    private String scoreAndLevel(Integer score, String level) {
        return valueOrMissing(score) + " / " + valueOrMissing(level);
    }

    /**
     * Returns a display value for a nullable field.
     *
     * @param value field value
     * @return value text or Not provided
     */
    private String valueOrMissing(Object value) {
        return value == null || value.toString().isBlank() ? "Not provided" : value.toString();
    }

    /**
     * Adds screening-hit records to the report.
     *
     * @param lines report lines being built
     * @param hits screening hits, if present
     */
    private void addScreeningLines(List<ReportLine> lines, List<ScreeningHitDto> hits) {
        addSection(lines, "Screening Hits");
        if (hits == null || hits.isEmpty()) {
            addField(lines, "Screening Hits", "None");
            return;
        }
        for (int i = 0; i < hits.size(); i++) {
            ScreeningHitDto hit = hits.get(i);
            addSection(lines, "Hit " + (i + 1));
            addField(lines, "Hit Name", hit.getHitName());
            addField(lines, "Category", hit.getCategory());
            addField(lines, "Source", hit.getSource());
            addField(lines, "Score", hit.getScore());
            addField(lines, "Determination", hit.getHitDetermination());
            addField(lines, "Comments", hit.getComments());
        }
    }

    /**
     * Adds audit-log records to the report.
     *
     * @param lines report lines being built
     * @param logs audit records, if present
     */
    private void addAuditLines(List<ReportLine> lines, List<AuditLogDto> logs) {
        addSection(lines, "Audit Log");
        if (logs == null || logs.isEmpty()) {
            addField(lines, "Audit Entries", "None");
            return;
        }
        for (int i = 0; i < logs.size(); i++) {
            AuditLogDto log = logs.get(i);
            addSection(lines, "Entry " + (i + 1));
            addField(lines, "Action Date", log.getActionDate());
            addField(lines, "Actioned By", log.getActionedBy());
            addField(lines, "Action Taken", log.getActionTaken());
        }
    }

    /**
     * Adds a section heading to the report.
     *
     * @param lines report lines being built
     * @param title section title
     */
    private void addSection(List<ReportLine> lines, String title) {
        lines.add(new ReportLine(sanitize(title), "", true));
    }

    /**
     * Adds a label/value row, representing missing values explicitly.
     *
     * @param lines report lines being built
     * @param label field label
     * @param value field value
     */
    private void addField(List<ReportLine> lines, String label, Object value) {
        String displayValue = value == null || value.toString().isBlank() ? "Not provided" : value.toString();
        lines.add(new ReportLine(sanitize(label), sanitize(displayValue), false));
    }

    /**
     * Splits report lines into pages while reserving space for page headers and footers.
     *
     * @param lines report content to paginate
     * @param regularFont font used for body text
     * @param boldFont font used for section headings
     * @return ordered report pages
     * @throws IOException if font metrics cannot be read
     */
    private List<List<ReportLine>> paginate(List<ReportLine> lines, PDFont regularFont, PDFont boldFont)
            throws IOException {
        List<List<ReportLine>> pages = new ArrayList<>();
        List<ReportLine> page = new ArrayList<>();
        float usedHeight = 0;
        float availableHeight = PDRectangle.A4.getHeight() - 156;
        for (ReportLine line : lines) {
            float lineHeight;
            if (line.section()) {
                lineHeight = 26;
            } else {
                lineHeight = fieldRowHeight(line, regularFont, boldFont);
            }
            if (!page.isEmpty() && usedHeight + lineHeight > availableHeight) {
                pages.add(page);
                page = new ArrayList<>();
                usedHeight = 0;
            }
            page.add(line);
            usedHeight += lineHeight;
        }
        if (!page.isEmpty() || pages.isEmpty()) {
            pages.add(page);
        }
        return pages;
    }

    /**
     * Calculates the height required to render both columns of a field row.
     *
     * @param line field row
     * @param regularFont font used for values
     * @param boldFont font used for labels
     * @return field row height in points
     * @throws IOException if font metrics cannot be read
     */
    private float fieldRowHeight(ReportLine line, PDFont regularFont, PDFont boldFont)
            throws IOException {
        float labelWidth = 170;
        float valueWidth = CONTENT_WIDTH - labelWidth - 16;
        int labelLines = wrapLine(line.label(), boldFont, labelWidth - 16).size();
        int valueLines = wrapLine(line.value(), regularFont, valueWidth - 16).size();
        return Math.max(20, Math.max(labelLines, valueLines) * BODY_LINE_HEIGHT + 8);
    }

    /**
     * Draws a report page with a repeated title, content, and page numbering.
     *
     * @param document owning PDF document
     * @param page page to draw
     * @param lines content assigned to the page
     * @param amlId AML identifier shown in the header
     * @param pageNumber current one-based page number
     * @param pageCount total number of pages
     * @param regularFont font used for body text
     * @param boldFont font used for headings
     * @throws IOException if PDF drawing fails
     */
    private void drawPage(PDDocument document, PDPage page, List<ReportLine> lines, String title, String amlId,
                          int pageNumber, int pageCount, PDFont regularFont, PDFont boldFont)
            throws IOException {
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();

            stream.setNonStrokingColor(0.10f, 0.18f, 0.32f);
            stream.addRect(0, pageHeight - 82, pageWidth, 82);
            stream.fill();
            stream.setNonStrokingColor(0.10f, 0.67f, 0.65f);
            stream.addRect(0, pageHeight - 86, pageWidth, 4);
            stream.fill();

            stream.beginText();
            stream.setNonStrokingColor(1f, 1f, 1f);
            stream.setFont(boldFont, 16);
            stream.newLineAtOffset(PAGE_MARGIN, pageHeight - 39);
            stream.showText(sanitize(title));
            stream.setNonStrokingColor(0.82f, 0.88f, 0.94f);
            stream.setFont(regularFont, 9);
            stream.newLineAtOffset(0, -19);
            stream.showText("AML ID: " + sanitize(amlId) + "  |  Generated: "
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            stream.endText();

            float y = pageHeight - 104;
            int rowIndex = 0;
            for (ReportLine line : lines) {
                if (line.section()) {
                    y -= 4;
                    stream.setNonStrokingColor(0.10f, 0.55f, 0.58f);
                    stream.addRect(PAGE_MARGIN, y - 13, CONTENT_WIDTH, 18);
                    stream.fill();
                    stream.beginText();
                    stream.setNonStrokingColor(1f, 1f, 1f);
                    stream.setFont(boldFont, 10);
                    stream.newLineAtOffset(PAGE_MARGIN + 8, y - 8);
                    stream.showText(line.label());
                    stream.endText();
                    y -= 22;
                    rowIndex = 0;
                } else {
                    float labelColumnWidth = 170;
                    float rowHeight = fieldRowHeight(line, regularFont, boldFont);
                    if (rowIndex % 2 == 0) {
                        stream.setNonStrokingColor(0.94f, 0.97f, 0.99f);
                        stream.addRect(PAGE_MARGIN, y - rowHeight + 3, CONTENT_WIDTH, rowHeight);
                        stream.fill();
                    }
                    stream.setStrokingColor(0.82f, 0.87f, 0.92f);
                    stream.moveTo(PAGE_MARGIN, y - rowHeight + 3);
                    stream.lineTo(PAGE_MARGIN + CONTENT_WIDTH, y - rowHeight + 3);
                    stream.moveTo(PAGE_MARGIN + labelColumnWidth, y - rowHeight + 3);
                    stream.lineTo(PAGE_MARGIN + labelColumnWidth, y + 3);
                    stream.stroke();
                    drawTableCell(stream, line.label(), PAGE_MARGIN + 8, y - 9,
                            boldFont, labelColumnWidth - 16, 0.12f, 0.20f, 0.32f);
                    drawTableCell(stream, line.value(), PAGE_MARGIN + labelColumnWidth + 8, y - 9,
                            regularFont, CONTENT_WIDTH - labelColumnWidth - 16, 0.16f, 0.20f, 0.25f);
                    y -= rowHeight;
                    rowIndex++;
                }
            }

            stream.setStrokingColor(0.76f, 0.82f, 0.89f);
            stream.moveTo(PAGE_MARGIN, 42);
            stream.lineTo(pageWidth - PAGE_MARGIN, 42);
            stream.stroke();
            stream.beginText();
            stream.setNonStrokingColor(0.30f, 0.37f, 0.46f);
            stream.setFont(regularFont, 8);
            stream.newLineAtOffset(PAGE_MARGIN, 28);
            stream.showText("Confidential - Compliance record");
            stream.newLineAtOffset(pageWidth - (PAGE_MARGIN * 2) - 55, 0);
            stream.showText("Page " + pageNumber + " of " + pageCount);
            stream.endText();
        }
    }

    /**
     * Draws wrapped text inside one cell of the report table.
     *
     * @param stream page content stream
     * @param text cell text
     * @param x left position in points
     * @param y baseline position in points
     * @param font cell font
     * @param maxWidth cell width in points
     * @param red red color component from zero to one
     * @param green green color component from zero to one
     * @param blue blue color component from zero to one
     * @throws IOException if text drawing fails
     */
    private void drawTableCell(PDPageContentStream stream, String text, float x, float y,
                               PDFont font, float maxWidth,
                               float red, float green, float blue) throws IOException {
        List<String> wrapped = wrapLine(text, font, maxWidth);
        for (int index = 0; index < wrapped.size(); index++) {
            drawText(stream, font, BODY_FONT_SIZE, wrapped.get(index),
                    x, y - (index * BODY_LINE_HEIGHT), red, green, blue);
        }
    }

    /**
     * Draws a single line of text using the requested font and color.
     *
     * @param stream page content stream
     * @param font text font
     * @param fontSize font size in points
     * @param text content to draw
     * @param x horizontal position
     * @param y baseline position
     * @throws IOException if PDF drawing fails
     */
    private void drawText(PDPageContentStream stream, PDFont font, float fontSize,
                          String text, float x, float y) throws IOException {
        drawText(stream, font, fontSize, text, x, y, 0.16f, 0.20f, 0.25f);
    }

    /**
     * Draws a single line of text using the requested font and RGB color.
     *
     * @param stream page content stream
     * @param font text font
     * @param fontSize font size in points
     * @param text content to draw
     * @param x horizontal position
     * @param y baseline position
     * @param red red color component from zero to one
     * @param green green color component from zero to one
     * @param blue blue color component from zero to one
     * @throws IOException if PDF drawing fails
     */
    private void drawText(PDPageContentStream stream, PDFont font, float fontSize, String text,
                          float x, float y, float red, float green, float blue) throws IOException {
        stream.beginText();
        stream.setNonStrokingColor(red, green, blue);
        stream.setFont(font, fontSize);
        stream.newLineAtOffset(x, y);
        stream.showText(text);
        stream.endText();
    }

    /**
     * Wraps a string to fit within the given PDF text width.
     *
     * @param text text to wrap
     * @param font font used to calculate rendered width
     * @param maxWidth maximum rendered width in points
     * @return wrapped lines
     * @throws IOException if font metrics cannot be read
     */
    private List<String> wrapLine(String text, PDFont font, float maxWidth) throws IOException {
        List<String> wrapped = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && textWidth(font, candidate) > maxWidth) {
                wrapped.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            wrapped.add(current.toString());
        }
        return wrapped.isEmpty() ? List.of("") : wrapped;
    }

    /**
     * Calculates the rendered width of a string in points.
     *
     * @param font font used for rendering
     * @param text text to measure
     * @return rendered width in points at the report body font size
     * @throws IOException if font metrics cannot be read
     */
    private float textWidth(PDFont font, String text) throws IOException {
        return textWidth(font, text, BODY_FONT_SIZE);
    }

    /**
     * Measures text width for a font at a specified size.
     *
     * @param font text font
     * @param text text to measure
     * @param fontSize font size in points
     * @return rendered text width in points
     * @throws IOException if font metrics cannot be read
     */
    private float textWidth(PDFont font, String text, float fontSize) throws IOException {
        return font.getStringWidth(text) * fontSize / 1000;
    }

    /**
     * Normalizes report text to printable ASCII for the standard PDF fonts.
     *
     * @param value source text
     * @return normalized printable text
     */
    private String sanitize(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\x20-\\x7E]", "?");
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private record ReportLine(String label, String value, boolean section) {
    }
}
