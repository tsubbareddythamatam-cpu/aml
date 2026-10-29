package org.aml.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class InsightsExportService {

    public byte[] exportExcel(InsightsDashboardService.Dashboard dashboard) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            var summary = workbook.createSheet("Overview");
            addRows(summary, headerStyle, List.of(
                    List.of("AML Insights Report", dashboard.startDate() + " to " + dashboard.endDate()),
                    List.of("Customers onboarded", String.valueOf(dashboard.onboarded())),
                    List.of("Verification pending", String.valueOf(dashboard.verificationPending())),
                    List.of("Approved or active", String.valueOf(dashboard.approved())),
                    List.of("Rejected", String.valueOf(dashboard.rejected())),
                    List.of("With alerts", String.valueOf(dashboard.withAlerts())),
                    List.of("Without alerts", String.valueOf(dashboard.withoutAlerts()))));

            var activity = workbook.createSheet("Activity");
            addRows(activity, headerStyle, withHeader(
                    List.of("Date", "Profiles onboarded", "Passports verified"),
                    dashboard.activity().stream().map(point ->
                            List.of(point.date(), String.valueOf(point.onboarded()),
                                    String.valueOf(point.passportsVerified()))).toList()));

            var alerts = workbook.createSheet("Alerts");
            addRows(alerts, headerStyle, withHeader(
                    List.of("Customer", "AML ID", "Alert type", "Process status", "Registered", "Score"),
                    dashboard.alerts().stream().map(alert ->
                            List.of(alert.name(), alert.amlId(), alert.type(), alert.status(),
                                    alert.date().toString(), alert.score() == null ? "" : alert.score().toString())).toList()));

            var customers = workbook.createSheet("Customers");
            addRows(customers, headerStyle, withHeader(
                    List.of("Customer", "AML ID", "Category", "Country", "Process status",
                            "Passport status", "Registered"),
                    dashboard.reports().stream().map(report ->
                            List.of(value(report.getFullName()), value(report.getAmlId()), value(report.getCategory()),
                                    value(report.getCountry()), value(report.getStatus()),
                                    "VERIFIED".equals(report.getPassportVerificationStatus()) ? "Verified" : "Pending",
                                    report.getRegisteredAt() == null ? "" : report.getRegisteredAt().toLocalDate().toString())).toList()));

            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                var sheet = workbook.getSheetAt(sheetIndex);
                if (sheet.getPhysicalNumberOfRows() > 0) {
                    int columns = sheet.getRow(0).getPhysicalNumberOfCells();
                    for (int column = 0; column < columns; column++) {
                        sheet.autoSizeColumn(column);
                        sheet.setColumnWidth(column, Math.min(sheet.getColumnWidth(column) + 512, 14000));
                    }
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    public byte[] exportPdf(InsightsDashboardService.Dashboard dashboard) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            List<String> lines = new java.util.ArrayList<>(List.of(
                    "AML Insights Report",
                    "Reporting period: " + dashboard.startDate() + " to " + dashboard.endDate(),
                    "",
                    "Customers onboarded: " + dashboard.onboarded(),
                    "Verification pending: " + dashboard.verificationPending(),
                    "Approved or active: " + dashboard.approved(),
                    "Rejected: " + dashboard.rejected(),
                    "With alerts: " + dashboard.withAlerts(),
                    "Without alerts: " + dashboard.withoutAlerts(),
                    "",
                    "ALERT DETAILS",
                    "Customer | AML ID | Alert type | Status | Registered | Score"));
            dashboard.alerts().forEach(alert -> lines.add(String.join(" | ",
                    pdfText(alert.name()), pdfText(alert.amlId()), pdfText(alert.type()),
                    pdfText(alert.status()), alert.date().toString(),
                    alert.score() == null ? "-" : alert.score().toString())));
            if (dashboard.alerts().isEmpty()) {
                lines.add("No alerts in the selected reporting period.");
            }
            int linesPerPage = 40;
            int pages = Math.max(1, (lines.size() + linesPerPage - 1) / linesPerPage);
            for (int pageNumber = 0; pageNumber < pages; pageNumber++) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    float y = page.getMediaBox().getHeight() - 44;
                    int from = pageNumber * linesPerPage;
                    int to = Math.min(lines.size(), from + linesPerPage);
                    for (int index = from; index < to; index++) {
                        String line = lines.get(index);
                        boolean title = index == 0;
                        stream.beginText();
                        stream.setFont(title || line.equals("ALERT DETAILS") ? bold : regular,
                                title ? 16 : 9);
                        stream.newLineAtOffset(40, y);
                        stream.showText(truncate(line, 105));
                        stream.endText();
                        y -= title ? 28 : 16;
                    }
                    stream.beginText();
                    stream.setFont(regular, 8);
                    stream.newLineAtOffset(40, 24);
                    stream.showText("Page " + (pageNumber + 1) + " of " + pages);
                    stream.endText();
                }
            }
            drawActivityChart(document, dashboard);
            drawBarChart(document, "Alerts by Screening Type", dashboard.alertTypes());
            drawBarChart(document, "Profiles by Process Status", dashboard.statuses());
            drawBarChart(document, "Alert Aging from Registration", dashboard.aging());
            drawDonutChart(document, "Profiles With / Without Alerts", List.of(
                    new InsightsDashboardService.ChartItem("With alert", dashboard.withAlerts()),
                    new InsightsDashboardService.ChartItem("Without alert", dashboard.withoutAlerts())));
            drawDonutChart(document, "Profiles by Customer Category", dashboard.categories());
            document.save(output);
            return output.toByteArray();
        }
    }

    private void drawActivityChart(PDDocument document, InsightsDashboardService.Dashboard dashboard)
            throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            drawPageTitle(stream, "Activity Timeline");
            float left = 58;
            float right = page.getMediaBox().getWidth() - 42;
            float bottom = 110;
            float top = page.getMediaBox().getHeight() - 100;
            float plotWidth = right - left;
            float plotHeight = top - bottom;
            long maximum = Math.max(1, dashboard.activity().stream()
                    .mapToLong(point -> Math.max(point.onboarded(), point.passportsVerified())).max().orElse(1));
            stream.setStrokingColor(225 / 255f, 226 / 255f, 231 / 255f);
            for (int tick = 0; tick <= 4; tick++) {
                float y = bottom + plotHeight * tick / 4;
                stream.moveTo(left, y);
                stream.lineTo(right, y);
                stream.stroke();
            }
            drawActivitySeries(stream, dashboard.activity(), left, plotWidth, bottom, plotHeight, maximum, true);
            drawActivitySeries(stream, dashboard.activity(), left, plotWidth, bottom, plotHeight, maximum, false);
            drawLegendItem(stream, left, 77, 37, 165, 112, "Profiles onboarded");
            drawLegendItem(stream, left + 170, 77, 207, 75, 82, "Passports verified");
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
            stream.newLineAtOffset(left, 55);
            stream.showText(dashboard.startDate() + " to " + dashboard.endDate());
            stream.endText();
        }
    }

    private void drawActivitySeries(PDPageContentStream stream, List<InsightsDashboardService.ActivityPoint> points,
                                    float left, float width, float bottom, float height,
                                    long maximum, boolean onboarded) throws IOException {
        if (points.isEmpty()) {
            return;
        }
        if (onboarded) {
            stream.setStrokingColor(37 / 255f, 173 / 255f, 114 / 255f);
        } else {
            stream.setStrokingColor(213 / 255f, 75 / 255f, 84 / 255f);
        }
        stream.setLineWidth(2);
        for (int index = 0; index < points.size(); index++) {
            float x = points.size() == 1 ? left + width / 2 : left + width * index / (points.size() - 1);
            long value = onboarded ? points.get(index).onboarded() : points.get(index).passportsVerified();
            float y = bottom + height * value / maximum;
            if (index == 0) {
                stream.moveTo(x, y);
            } else {
                stream.lineTo(x, y);
            }
        }
        stream.stroke();
    }

    private void drawBarChart(PDDocument document, String title, List<InsightsDashboardService.ChartItem> items)
            throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            drawPageTitle(stream, title);
            float labelX = 45;
            float barX = 190;
            float barMaxWidth = page.getMediaBox().getWidth() - barX - 70;
            float rowHeight = Math.min(54, (page.getMediaBox().getHeight() - 180) / Math.max(1, items.size()));
            float y = page.getMediaBox().getHeight() - 125;
            long maximum = Math.max(1, items.stream().mapToLong(InsightsDashboardService.ChartItem::value).max().orElse(1));
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (int index = 0; index < items.size(); index++) {
                InsightsDashboardService.ChartItem item = items.get(index);
                stream.beginText();
                stream.setFont(font, 9);
                stream.newLineAtOffset(labelX, y + 5);
                stream.showText(truncate(pdfText(item.label()), 25));
                stream.endText();
                float barWidth = Math.max(2, barMaxWidth * item.value() / maximum);
                stream.setNonStrokingColor(132 / 255f, 56 / 255f, 189 / 255f);
                stream.addRect(barX, y, barWidth, 20);
                stream.fill();
                stream.beginText();
                stream.setFont(font, 9);
                stream.newLineAtOffset(barX + barWidth + 7, y + 6);
                stream.showText(String.valueOf(item.value()));
                stream.endText();
                y -= rowHeight;
            }
            if (items.isEmpty()) {
                stream.beginText();
                stream.setFont(font, 10);
                stream.newLineAtOffset(labelX, y);
                stream.showText("No data in the selected reporting period.");
                stream.endText();
            }
        }
    }

    private void drawDonutChart(PDDocument document, String title,
                                List<InsightsDashboardService.ChartItem> items) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            drawPageTitle(stream, title);
            float centerX = 205;
            float centerY = 440;
            float radius = 105;
            float circumference = (float) (2 * Math.PI * radius);
            long total = items.stream().mapToLong(InsightsDashboardService.ChartItem::value).sum();
            int[][] colors = {{132, 56, 189}, {20, 153, 220}, {37, 173, 114}, {227, 79, 87}, {241, 165, 29}};
            if (total == 0) {
                stream.setStrokingColor(225 / 255f, 226 / 255f, 231 / 255f);
                stream.setLineWidth(28);
                addCirclePath(stream, centerX, centerY, radius);
                stream.stroke();
            } else {
                float offset = 0;
                for (int index = 0; index < items.size(); index++) {
                    InsightsDashboardService.ChartItem item = items.get(index);
                    float segmentLength = circumference * item.value() / total;
                    if (segmentLength > 0) {
                        int[] color = colors[index % colors.length];
                        stream.setStrokingColor(color[0] / 255f, color[1] / 255f, color[2] / 255f);
                        stream.setLineWidth(28);
                        stream.setLineDashPattern(new float[]{segmentLength, Math.max(.1f, circumference - segmentLength)},
                                offset);
                        addCirclePath(stream, centerX, centerY, radius);
                        stream.stroke();
                        offset += segmentLength;
                    }
                }
                stream.setLineDashPattern(new float[0], 0);
            }
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            stream.beginText();
            stream.setFont(bold, 20);
            stream.newLineAtOffset(centerX - 15, centerY + 2);
            stream.showText(String.valueOf(total));
            stream.endText();
            stream.beginText();
            stream.setFont(regular, 9);
            stream.newLineAtOffset(centerX - 16, centerY - 14);
            stream.showText("Profiles");
            stream.endText();

            float legendY = 620;
            for (int index = 0; index < items.size(); index++) {
                InsightsDashboardService.ChartItem item = items.get(index);
                int[] color = colors[index % colors.length];
                stream.setNonStrokingColor(color[0] / 255f, color[1] / 255f, color[2] / 255f);
                stream.addRect(390, legendY - 3, 10, 10);
                stream.fill();
                String percentage = total == 0 ? "0.0%" : String.format(java.util.Locale.ROOT,
                        "%.1f%%", item.value() * 100.0 / total);
                stream.beginText();
                stream.setFont(regular, 9);
                stream.newLineAtOffset(408, legendY);
                stream.showText(truncate(pdfText(item.label() + "  " + item.value() + " (" + percentage + ")"), 42));
                stream.endText();
                legendY -= 25;
            }
        }
    }

    private void addCirclePath(PDPageContentStream stream, float centerX, float centerY, float radius)
            throws IOException {
        float control = radius * .55228475f;
        stream.moveTo(centerX + radius, centerY);
        stream.curveTo(centerX + radius, centerY + control, centerX + control, centerY + radius, centerX, centerY + radius);
        stream.curveTo(centerX - control, centerY + radius, centerX - radius, centerY + control, centerX - radius, centerY);
        stream.curveTo(centerX - radius, centerY - control, centerX - control, centerY - radius, centerX, centerY - radius);
        stream.curveTo(centerX + control, centerY - radius, centerX + radius, centerY - control, centerX + radius, centerY);
        stream.closePath();
    }

    private void drawPageTitle(PDPageContentStream stream, String title) throws IOException {
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
        stream.newLineAtOffset(40, 770);
        stream.showText(pdfText(title));
        stream.endText();
    }

    private void drawLegendItem(PDPageContentStream stream, float x, float y,
                                int red, int green, int blue, String label) throws IOException {
        stream.setStrokingColor(red / 255f, green / 255f, blue / 255f);
        stream.setLineWidth(2);
        stream.moveTo(x, y);
        stream.lineTo(x + 24, y);
        stream.stroke();
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
        stream.newLineAtOffset(x + 30, y - 3);
        stream.showText(label);
        stream.endText();
    }

    private void addRows(org.apache.poi.ss.usermodel.Sheet sheet, CellStyle headerStyle, List<List<String>> rows) {
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex);
            List<String> values = rows.get(rowIndex);
            for (int column = 0; column < values.size(); column++) {
                var cell = row.createCell(column);
                cell.setCellValue(values.get(column));
                if (rowIndex == 0) {
                    cell.setCellStyle(headerStyle);
                }
            }
        }
    }

    private List<List<String>> withHeader(List<String> header, List<List<String>> rows) {
        java.util.ArrayList<List<String>> result = new java.util.ArrayList<>();
        result.add(header);
        result.addAll(rows);
        return result;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String pdfText(String value) {
        return value == null ? "-" : value.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
