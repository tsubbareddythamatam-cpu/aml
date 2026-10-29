package org.aml.service;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.aml.dto.BulkRegistrationResponse;
import org.aml.dto.CustomerDto;
import org.aml.dto.KycDataDto;
import org.aml.exception.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class IndividualCustomerBulkImportService {

    private static final int MAX_DATA_ROWS = 5_000;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final List<String> REQUIRED_COLUMNS = List.of(
            "CUSTOMER_NAME",
            "DATE_OF_BIRTH",
            "NATIONALITY",
            "COUNTRY_OF_RESIDENCE",
            "PASSPORT_NUMBER",
            "PASSPORT_EXPIRY_DATE",
            "NATIONAL_ID_NUMBER",
            "ID_EXPIRY_DATE",
            "PRODUCT",
            "INDUSTRY",
            "COMPANY_NAME",
            "POSITION_IN_COMPANY",
            "EMAIL_ADDRESS");
    private static final List<DateTimeFormatter> TEXT_DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("M/d/uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d-MMM-uuuu", Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT));

    private final ComplianceService complianceService;

    public IndividualCustomerBulkImportService(ComplianceService complianceService) {
        this.complianceService = complianceService;
    }

    public BulkRegistrationResponse importWorkbook(MultipartFile upload, String onboardedBy) {
        validateUpload(upload);
        try (InputStream input = upload.getInputStream();
             Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0 || workbook.getSheetAt(0).getRow(0) == null) {
                throw badRequest("The workbook must have a header row in its first worksheet.");
            }
            return importFirstSheet(workbook.getSheetAt(0), onboardedBy);
        } catch (IOException | EncryptedDocumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The uploaded file is not a readable Excel workbook.", exception);
        }
    }

    private BulkRegistrationResponse importFirstSheet(org.apache.poi.ss.usermodel.Sheet sheet, String onboardedBy) {
        if (sheet.getLastRowNum() > MAX_DATA_ROWS) {
            throw badRequest("The workbook cannot contain more than " + MAX_DATA_ROWS + " data rows.");
        }

        Map<String, Integer> columns = findColumns(sheet.getRow(0));
        List<String> missingColumns = REQUIRED_COLUMNS.stream()
                .filter(column -> !columns.containsKey(column))
                .toList();
        if (!missingColumns.isEmpty()) {
            throw badRequest("The first worksheet is missing required template columns: "
                    + String.join(", ", missingColumns) + ".");
        }

        Set<String> existingEmails = new HashSet<>();
        for (CustomerDto report : complianceService.fetchAll()) {
            if (report.getKycData() != null && report.getKycData().getContactInformation() != null) {
                String email = report.getKycData().getContactInformation().getEmailAddress();
                if (email != null && !email.isBlank()) {
                    existingEmails.add(normalizeEmail(email));
                }
            }
        }

        List<String> createdAmlIds = new ArrayList<>();
        List<ErrorResponse> errors = new ArrayList<>();
        List<String> failedEmails = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        int totalRecords = 0;
        int failedRecords = 0;

        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isEmpty(row, formatter)) {
                continue;
            }
            totalRecords++;
            int displayedRow = rowIndex + 1;
            String email = readText(row, columns.get("EMAIL_ADDRESS"), formatter);
            try {
                CustomerDto report = toCustomer(row, columns, formatter, onboardedBy, displayedRow);
                String normalizedEmail = normalizeEmail(email);
                if (!existingEmails.add(normalizedEmail)) {
                    throw new RowImportException("Email address is already used by an existing profile or another row.");
                }
                CustomerDto created = complianceService.processReport(report);
                createdAmlIds.add(created.getAmlId());
            } catch (RowImportException | DateTimeParseException exception) {
                failedRecords++;
                addRowError(errors, displayedRow, exception.getMessage());
                addFailedEmail(failedEmails, email);
            } catch (ResponseStatusException exception) {
                failedRecords++;
                addRowError(errors, displayedRow, exception.getReason());
                addFailedEmail(failedEmails, email);
            }
        }

        return BulkRegistrationResponse.builder()
                .totalRecords(totalRecords)
                .successRecords(createdAmlIds.size())
                .failedRecords(failedRecords)
                .failedEmails(failedEmails)
                .createdAmlIds(createdAmlIds)
                .errors(errors)
                .build();
    }

    private CustomerDto toCustomer(Row row, Map<String, Integer> columns, DataFormatter formatter,
                                   String onboardedBy, int displayedRow) {
        String name = requiredText(row, columns, "CUSTOMER_NAME", formatter, displayedRow);
        String email = requiredText(row, columns, "EMAIL_ADDRESS", formatter, displayedRow);
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new RowImportException("Email address is not valid.");
        }

        LocalDate dateOfBirth = optionalDate(row, columns, "DATE_OF_BIRTH", formatter);
        if (dateOfBirth != null && dateOfBirth.isAfter(LocalDate.now())) {
            throw new RowImportException("Date of birth cannot be in the future.");
        }
        LocalDate passportExpiry = requiredDate(row, columns, "PASSPORT_EXPIRY_DATE", formatter, displayedRow);
        if (!passportExpiry.isAfter(LocalDate.now())) {
            throw new RowImportException("Passport expiry date must be in the future.");
        }
        LocalDate nationalIdExpiry = requiredDate(row, columns, "ID_EXPIRY_DATE", formatter, displayedRow);
        if (!nationalIdExpiry.isAfter(LocalDate.now())) {
            throw new RowImportException("National ID expiry date must be in the future.");
        }

        String nationality = requiredText(row, columns, "NATIONALITY", formatter, displayedRow);
        String country = requiredText(row, columns, "COUNTRY_OF_RESIDENCE", formatter, displayedRow);
        String passportNumber = requiredText(row, columns, "PASSPORT_NUMBER", formatter, displayedRow);
        String nationalIdNumber = requiredText(row, columns, "NATIONAL_ID_NUMBER", formatter, displayedRow);

        KycDataDto.CustomerInformation customerInformation = KycDataDto.CustomerInformation.builder()
                .fullName(name)
                .dateOfBirth(dateOfBirth == null ? null : dateOfBirth.toString())
                .nationality(nationality)
                .countryOfResidence(country)
                .passportNumber(passportNumber)
                .passportExpiry(passportExpiry.toString())
                .nationalIdNumber(nationalIdNumber)
                .nationalIdExpiry(nationalIdExpiry.toString())
                .build();
        KycDataDto.ProfileInformation profileInformation = KycDataDto.ProfileInformation.builder()
                .products(optionalText(row, columns, "PRODUCT", formatter))
                .industry(optionalText(row, columns, "INDUSTRY", formatter))
                .build();
        KycDataDto.ContactInformation contactInformation = KycDataDto.ContactInformation.builder()
                .emailAddress(email)
                .build();

        CustomerDto report = new CustomerDto();
        report.setFullName(name);
        report.setCategory("INDIVIDUAL");
        report.setCountry(country);
        report.setCompanyName(optionalText(row, columns, "COMPANY_NAME", formatter));
        report.setPositionInCompany(optionalText(row, columns, "POSITION_IN_COMPANY", formatter));
        report.setOnboardedByUser(onboardedBy);
        report.setKycData(KycDataDto.builder()
                .customerInformation(customerInformation)
                .profileInformation(profileInformation)
                .contactInformation(contactInformation)
                .build());
        return report;
    }

    private Map<String, Integer> findColumns(Row headerRow) {
        Map<String, Integer> columns = new HashMap<>();
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        for (Cell cell : headerRow) {
            String header = normalizeHeader(formatter.formatCellValue(cell));
            if (!header.isEmpty() && columns.putIfAbsent(header, cell.getColumnIndex()) != null) {
                throw badRequest("The first worksheet contains duplicate column headers.");
            }
        }
        return columns;
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_|_$", "");
    }

    private boolean isEmpty(Row row, DataFormatter formatter) {
        for (Cell cell : row) {
            if (!formatter.formatCellValue(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String requiredText(Row row, Map<String, Integer> columns, String column,
                                DataFormatter formatter, int displayedRow) {
        String value = optionalText(row, columns, column, formatter);
        if (value == null) {
            throw new RowImportException("Required value is missing from " + column + ".");
        }
        return value;
    }

    private String optionalText(Row row, Map<String, Integer> columns, String column, DataFormatter formatter) {
        Integer index = columns.get(column);
        if (index == null || row.getCell(index) == null) {
            return null;
        }
        String value = formatter.formatCellValue(row.getCell(index)).trim();
        return value.isEmpty() ? null : value;
    }

    private String readText(Row row, Integer column, DataFormatter formatter) {
        if (column == null || row.getCell(column) == null) {
            return null;
        }
        String value = formatter.formatCellValue(row.getCell(column)).trim();
        return value.isEmpty() ? null : value;
    }

    private LocalDate requiredDate(Row row, Map<String, Integer> columns, String column,
                                   DataFormatter formatter, int displayedRow) {
        LocalDate value = optionalDate(row, columns, column, formatter);
        if (value == null) {
            throw new RowImportException("Required date is missing from " + column + ".");
        }
        return value;
    }

    private LocalDate optionalDate(Row row, Map<String, Integer> columns, String column, DataFormatter formatter) {
        Integer index = columns.get(column);
        if (index == null || row.getCell(index) == null) {
            return null;
        }
        Cell cell = row.getCell(index);
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                && DateUtil.isValidExcelDate(cell.getNumericCellValue())) {
            return DateUtil.getLocalDateTime(cell.getNumericCellValue()).toLocalDate();
        }
        String value = formatter.formatCellValue(cell).trim();
        if (value.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter dateFormat : TEXT_DATE_FORMATS) {
            try {
                return LocalDate.parse(value, dateFormat);
            } catch (DateTimeParseException ignored) {
                // Try the next supported date representation.
            }
        }
        throw new RowImportException("Date in " + column + " must use YYYY-MM-DD or a valid Excel date cell.");
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void addFailedEmail(List<String> failedEmails, String email) {
        if (email != null && !email.isBlank()) {
            failedEmails.add(email);
        }
    }

    private void addRowError(List<ErrorResponse> errors, int row, String reason) {
        errors.add(ErrorResponse.builder()
                .errorCode("BULK_INDIVIDUAL_ROW")
                .message("Row " + row + ": " + (reason == null ? "The record could not be imported." : reason))
                .status(HttpStatus.BAD_REQUEST.value())
                .timestamp(LocalDateTime.now())
                .build());
    }

    private void validateUpload(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw badRequest("Choose an individual onboarding Excel workbook to upload.");
        }
        String filename = upload.getOriginalFilename();
        if (filename == null || !(filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")
                || filename.toLowerCase(Locale.ROOT).endsWith(".xls"))) {
            throw badRequest("Upload an .xlsx or .xls individual onboarding workbook.");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static final class RowImportException extends RuntimeException {
        private RowImportException(String message) {
            super(message);
        }
    }
}
