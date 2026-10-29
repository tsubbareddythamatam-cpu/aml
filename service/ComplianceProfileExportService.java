package org.aml.service;

import org.aml.dto.CustomerDto;
import org.aml.dto.ReviewExportField;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ComplianceProfileExportService {

    private static final String[] HEADERS = {
            "AML ID", "Name", "Country", "Category", "Associated company",
            "Registration date", "Onboarded by", "Passport", "Process status"
    };
    private static final float[] COLUMN_WIDTHS = {78, 112, 66, 65, 112, 92, 102, 70, 89};
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    public byte[] exportExcel(List<CustomerDto> reports) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Customer profiles");
            Row header = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);
            for (int column = 0; column < HEADERS.length; column++) {
                Cell cell = header.createCell(column);
                cell.setCellValue(HEADERS[column]);
                cell.setCellStyle(headerStyle);
            }
            for (int index = 0; index < reports.size(); index++) {
                CustomerDto report = reports.get(index);
                Row row = sheet.createRow(index + 1);
                List<String> values = profileValues(report);
                for (int column = 0; column < values.size(); column++) {
                    row.createCell(column).setCellValue(values.get(column));
                }
            }
            for (int column = 0; column < HEADERS.length; column++) {
                sheet.autoSizeColumn(column);
                sheet.setColumnWidth(column, Math.min(sheet.getColumnWidth(column) + 512, 12000));
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    public byte[] exportPdf(List<CustomerDto> reports) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            int rowsPerPage = 22;
            int pageCount = Math.max(1, (reports.size() + rowsPerPage - 1) / rowsPerPage);
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                PDPage page = new PDPage(new PDRectangle(
                        PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    drawPdfPage(stream, page, reports, pageIndex, pageCount, regular, bold, rowsPerPage);
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    public byte[] exportReviewExcel(List<ReviewExportField> fields) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Onboarding review");
            Row header = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);
            header.createCell(0).setCellValue("Field");
            header.createCell(1).setCellValue("Value");
            header.getCell(0).setCellStyle(headerStyle);
            header.getCell(1).setCellStyle(headerStyle);
            for (int index = 0; index < fields.size(); index++) {
                ReviewExportField field = fields.get(index);
                Row row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue(field.label());
                row.createCell(1).setCellValue(field.value());
            }
            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            sheet.setColumnWidth(0, Math.min(sheet.getColumnWidth(0) + 512, 12000));
            sheet.setColumnWidth(1, Math.min(sheet.getColumnWidth(1) + 512, 20000));
            workbook.write(output);
            return output.toByteArray();
        }
    }

    public byte[] exportReviewPdf(List<ReviewExportField> fields) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            float margin = 38;
            PDRectangle pageSize = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
            float tableWidth = pageSize.getWidth() - 2 * margin;
            float labelWidth = 190;
            float valueWidth = tableWidth - labelWidth;
            PDPage page = new PDPage(pageSize);
            document.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(document, page);
            try {
                float y = drawReviewTableHeading(stream, page, margin, tableWidth, labelWidth, bold);
                for (int index = 0; index < fields.size(); index++) {
                    ReviewExportField field = fields.get(index);
                    List<String> labelLines = wrapReviewText(toPdfText(field.label()), bold,
                            10, labelWidth - 20);
                    List<String> valueLines = wrapReviewText(toPdfText(field.value()), regular,
                            10, valueWidth - 20);
                    float rowHeight = Math.max(36, 18 + Math.max(labelLines.size(), valueLines.size()) * 14);
                    if (y - rowHeight < margin) {
                        stream.close();
                        page = new PDPage(pageSize);
                        document.addPage(page);
                        stream = new PDPageContentStream(document, page);
                        y = drawReviewTableHeading(stream, page, margin, tableWidth, labelWidth, bold);
                    }
                    boolean alternate = index % 2 == 0;
                    drawReviewTableCell(stream, margin, y, labelWidth, rowHeight, labelLines,
                            bold, alternate);
                    drawReviewTableCell(stream, margin + labelWidth, y, valueWidth, rowHeight,
                            valueLines, regular, alternate);
                    y -= rowHeight;
                }
            } finally {
                stream.close();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private float drawReviewTableHeading(PDPageContentStream stream, PDPage page, float margin,
                                         float tableWidth, float labelWidth,
                                         PDType1Font bold) throws IOException {
        float y = page.getMediaBox().getHeight() - margin;
        writeReviewTableText(stream, "Individual Onboarding Review", margin, y, bold, 18);
        y -= 32;
        float headerHeight = 30;
        float valueWidth = tableWidth - labelWidth;
        stream.setNonStrokingColor(218 / 255f, 232 / 255f, 223 / 255f);
        stream.addRect(margin, y - headerHeight, labelWidth, headerHeight);
        stream.addRect(margin + labelWidth, y - headerHeight, valueWidth, headerHeight);
        stream.fill();
        stream.setStrokingColor(196 / 255f, 211 / 255f, 203 / 255f);
        stream.addRect(margin, y - headerHeight, labelWidth, headerHeight);
        stream.addRect(margin + labelWidth, y - headerHeight, valueWidth, headerHeight);
        stream.stroke();
        writeReviewTableText(stream, "Field", margin + 10, y - 20, bold, 11);
        writeReviewTableText(stream, "Value", margin + labelWidth + 10, y - 20, bold, 11);
        return y - headerHeight;
    }

    private void drawReviewTableCell(PDPageContentStream stream, float x, float top, float width,
                                     float height, List<String> lines, PDType1Font font,
                                     boolean shaded) throws IOException {
        stream.setNonStrokingColor(shaded ? 246 / 255f : 1, shaded ? 249 / 255f : 1,
                shaded ? 247 / 255f : 1);
        stream.addRect(x, top - height, width, height);
        stream.fill();
        stream.setStrokingColor(211 / 255f, 220 / 255f, 215 / 255f);
        stream.addRect(x, top - height, width, height);
        stream.stroke();
        float textY = top - 16;
        for (String line : lines) {
            writeReviewTableText(stream, line, x + 10, textY, font, 10);
            textY -= 14;
        }
    }

    private void writeReviewTableText(PDPageContentStream stream, String value, float x, float y,
                                      PDType1Font font, float fontSize) throws IOException {
        stream.setNonStrokingColor(35 / 255f, 45 / 255f, 42 / 255f);
        stream.beginText();
        stream.setFont(font, fontSize);
        stream.newLineAtOffset(x, y);
        stream.showText(value);
        stream.endText();
    }

    private List<String> wrapReviewText(String value, PDType1Font font, float fontSize,
                                        float maxWidth) throws IOException {
        if (value.isBlank()) {
            return List.of("-");
        }
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        for (String paragraph : value.split("\\R", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split("\\s+")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && font.getStringWidth(candidate) * fontSize / 1000 > maxWidth) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                if (font.getStringWidth(word) * fontSize / 1000 <= maxWidth) {
                    if (!line.isEmpty()) {
                        line.append(' ');
                    }
                    line.append(word);
                } else {
                    for (int index = 0; index < word.length(); index++) {
                        String character = word.substring(index, index + 1);
                        if (!line.isEmpty()
                                && font.getStringWidth(line + character) * fontSize / 1000 > maxWidth) {
                            lines.add(line.toString());
                            line.setLength(0);
                        }
                        line.append(character);
                    }
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private void drawPdfPage(PDPageContentStream stream, PDPage page, List<CustomerDto> reports,
                             int pageIndex, int pageCount, PDType1Font regular,
                             PDType1Font bold, int rowsPerPage) throws IOException {
        float margin = 28;
        float y = page.getMediaBox().getHeight() - margin;
        stream.beginText();
        stream.setFont(bold, 15);
        stream.newLineAtOffset(margin, y);
        stream.showText("AML Customer Profiles");
        stream.endText();
        y -= 24;

        float x = margin;
        float headerHeight = 25;
        for (int column = 0; column < HEADERS.length; column++) {
            stream.addRect(x, y - headerHeight, COLUMN_WIDTHS[column], headerHeight);
            stream.setNonStrokingColor(235 / 255f, 230 / 255f, 241 / 255f);
            stream.fill();
            stream.setNonStrokingColor(45 / 255f, 37 / 255f, 53 / 255f);
            writeCell(stream, HEADERS[column], x + 4, y - 16, bold, 7.5f, 20);
            x += COLUMN_WIDTHS[column];
        }
        y -= headerHeight;

        int from = pageIndex * rowsPerPage;
        int to = Math.min(reports.size(), from + rowsPerPage);
        for (int index = from; index < to; index++) {
            x = margin;
            float rowHeight = 21;
            List<String> values = profileValues(reports.get(index));
            for (int column = 0; column < values.size(); column++) {
                stream.addRect(x, y - rowHeight, COLUMN_WIDTHS[column], rowHeight);
                stream.setStrokingColor(220 / 255f, 216 / 255f, 225 / 255f);
                stream.stroke();
                stream.setNonStrokingColor(50 / 255f, 45 / 255f, 57 / 255f);
                writeCell(stream, values.get(column), x + 4, y - 14, regular, 7f,
                        Math.max(8, (int) (COLUMN_WIDTHS[column] / 4.2f)));
                x += COLUMN_WIDTHS[column];
            }
            y -= rowHeight;
        }
        stream.beginText();
        stream.setFont(regular, 8);
        stream.newLineAtOffset(margin, margin - 4);
        stream.showText("Page " + (pageIndex + 1) + " of " + pageCount + " | " + reports.size() + " profiles");
        stream.endText();
    }

    private void writeCell(PDPageContentStream stream, String value, float x, float y,
                           PDType1Font font, float size, int maxCharacters) throws IOException {
        String safe = toPdfText(value);
        if (safe.length() > maxCharacters) {
            safe = safe.substring(0, Math.max(0, maxCharacters - 3)) + "...";
        }
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(safe);
        stream.endText();
    }

    private List<String> profileValues(CustomerDto report) {
        String category = "COMPANY".equals(report.getCategory()) ? "Company" : "Individual";
        String passport = "VERIFIED".equals(report.getPassportVerificationStatus()) ? "Verified"
                : "FAILED".equals(report.getPassportVerificationStatus()) ? "Rejected"
                : "PASSED".equals(report.getPassportImageQualityStatus()) ? "Review" : "Required";
        String registeredAt = report.getRegisteredAt() == null ? "" : DATE_FORMAT.format(report.getRegisteredAt());
        return List.of(value(report.getAmlId()), value(report.getFullName()), value(report.getCountry()),
                category, value(report.getCompanyName()), registeredAt, value(report.getOnboardedByUser()),
                passport, value(report.getStatus()));
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private String toPdfText(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "");
        return normalized.codePoints()
                .map(codePoint -> codePoint >= 32 && codePoint <= 126 ? codePoint : '?')
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
