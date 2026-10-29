package org.aml.service;

import org.aml.dto.PassportDetailsExtraction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PassportOcrService {

    private static final Pattern LABELED_VALUE = Pattern.compile(
            "(?im)^\\s*(surname|last\\s+name|given\\s+names?|first\\s+name|full\\s+name|passport\\s*(?:no|number|type)|document\\s+type|issuing\\s+(?:authority|country|state)|authority|issued\\s+by|place\\s+of\\s+issue|issuing\\s+office|country\\s+code|date\\s+of\\s+issue|issue\\s+date|date\\s+issued|issued\\s+on|date\\s+of\\s+birth|birth\\s+date|nationality|sex|gender|date\\s+of\\s+expiry|expiry\\s+date)\\s*[:#-]?\\s*(.+?)\\s*$");
    private static final String DEFAULT_COMMAND = "tesseract";

    private final String tesseractCommand;

    public PassportOcrService(
            @Value("${app.passport.ocr-command:${TESSERACT_CMD:tesseract}}") String tesseractCommand) {
        this.tesseractCommand = tesseractCommand;
    }

    public PassportDetailsExtraction extract(Path passportImage) {
        Path outputBase = null;
        Path outputText = null;
        Path errorOutput = null;
        try {
            outputBase = Files.createTempFile("aml-passport-ocr-", "");
            Files.delete(outputBase);
            outputText = Path.of(outputBase + ".txt");
            errorOutput = Files.createTempFile("aml-passport-ocr-error-", ".txt");
            PassportDetailsExtraction bestExtraction = null;
            boolean timedOut = false;
            String diagnostic = "";
            for (String pageSegmentationMode : List.of("6", "11", "7")) {
                Files.deleteIfExists(outputText);
                Process process;
                try {
                    process = new ProcessBuilder(resolveTesseractCommand(), passportImage.toString(),
                            outputBase.toString(), "-l", "eng", "--psm", pageSegmentationMode)
                            .redirectError(errorOutput.toFile())
                            .start();
                } catch (IOException exception) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Passport image was saved, but local OCR is unavailable. Install Tesseract OCR or configure app.passport.ocr-command.",
                            exception);
                }
                if (!process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    timedOut = true;
                    continue;
                }
                if (process.exitValue() != 0 || !Files.isRegularFile(outputText)) {
                    diagnostic = Files.isRegularFile(errorOutput)
                            ? Files.readString(errorOutput, StandardCharsets.UTF_8).trim() : "";
                    continue;
                }
                try {
                    PassportDetailsExtraction extraction =
                            parse(Files.readString(outputText, StandardCharsets.UTF_8));
                    if (extractionScore(extraction) > extractionScore(bestExtraction)) {
                        bestExtraction = extraction;
                    }
                    if (extractionScore(bestExtraction) == 12) {
                        break;
                    }
                } catch (ResponseStatusException exception) {
                    if (exception.getStatusCode() != HttpStatus.UNPROCESSABLE_ENTITY) {
                        throw exception;
                    }
                }
            }
            if (bestExtraction != null) {
                return bestExtraction;
            }
            if (timedOut) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Passport OCR took too long. The image is saved; enter the details manually.");
            }
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    diagnostic.isBlank()
                            ? "Passport text was not recognized. Enter the details manually."
                            : "OCR could not read the passport image. " + diagnostic);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Passport OCR was interrupted. Enter the details manually.", exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not read the local passport OCR result.", exception);
        } finally {
            deleteTemporaryFile(outputText);
            deleteTemporaryFile(errorOutput);
            deleteTemporaryFile(outputBase);
        }
    }

    private int extractionScore(PassportDetailsExtraction extraction) {
        if (extraction == null) {
            return 0;
        }
        return (extraction.firstName().isBlank() ? 0 : 1)
                + (extraction.lastName().isBlank() ? 0 : 1)
                + (extraction.fullName().isBlank() ? 0 : 1)
                + (extraction.passportNumber().isBlank() ? 0 : 1)
                + (extraction.passportExpiryDate() == null ? 0 : 1)
                + (extraction.dateOfBirth() == null ? 0 : 1)
                + (extraction.nationality().isBlank() ? 0 : 1)
                + (extraction.gender().isBlank() ? 0 : 1)
                + (extraction.passportType().isBlank() ? 0 : 1)
                + (extraction.passportIssuingCountryCode().isBlank() ? 0 : 1)
                + (extraction.passportDateOfIssue() == null ? 0 : 1)
                + (extraction.passportIssuingAuthority().isBlank() ? 0 : 1);
    }

    private String resolveTesseractCommand() {
        if (!DEFAULT_COMMAND.equals(tesseractCommand)) {
            return tesseractCommand;
        }
        for (String candidate : List.of(
                "C:\\Program Files\\Tesseract-OCR\\tesseract.exe",
                "C:\\Program Files (x86)\\Tesseract-OCR\\tesseract.exe")) {
            if (Files.isExecutable(Paths.get(candidate))) {
                return candidate;
            }
        }
        return tesseractCommand;
    }

    private PassportDetailsExtraction parse(String text) {
        List<String> lines = text.lines().map(this::normalizeMrzLine)
                .filter(line -> !line.isBlank()).toList();
        PassportDetailsExtraction mrzExtraction = null;
        for (int index = 0; index + 1 < lines.size(); index++) {
            String documentLine = lines.get(index);
            String dataLine = lines.get(index + 1);
            if (documentLine.startsWith("P") && documentLine.contains("<<")
                    && documentLine.length() >= 25 && dataLine.length() >= 30) {
                PassportDetailsExtraction mrz = parseMrz(documentLine, dataLine);
                if (mrz != null) {
                    mrzExtraction = mrz;
                    break;
                }
            }
        }
        if (mrzExtraction != null) {
            try {
                PassportDetailsExtraction labeled = parseLabeledFields(text);
                return mergeLabeledPassportFields(mrzExtraction, labeled);
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode() != HttpStatus.UNPROCESSABLE_ENTITY) {
                    throw exception;
                }
            }
            return mrzExtraction;
        }
        return parseLabeledFields(text);
    }

    private PassportDetailsExtraction mergeLabeledPassportFields(
            PassportDetailsExtraction mrz, PassportDetailsExtraction labeled) {
        return new PassportDetailsExtraction(
                mrz.firstName().isBlank() ? labeled.firstName() : mrz.firstName(),
                mrz.lastName().isBlank() ? labeled.lastName() : mrz.lastName(),
                mrz.fullName().isBlank() ? labeled.fullName() : mrz.fullName(),
                mrz.passportNumber().isBlank() ? labeled.passportNumber() : mrz.passportNumber(),
                mrz.passportExpiryDate() == null ? labeled.passportExpiryDate() : mrz.passportExpiryDate(),
                mrz.dateOfBirth() == null ? labeled.dateOfBirth() : mrz.dateOfBirth(),
                mrz.nationality().isBlank() ? labeled.nationality() : mrz.nationality(),
                mrz.gender().isBlank() ? labeled.gender() : mrz.gender(),
                mrz.passportType().isBlank() ? labeled.passportType() : mrz.passportType(),
                mrz.passportIssuingCountryCode().isBlank()
                        ? labeled.passportIssuingCountryCode() : mrz.passportIssuingCountryCode(),
                mrz.passportDateOfIssue() == null ? labeled.passportDateOfIssue() : mrz.passportDateOfIssue(),
                mrz.passportIssuingAuthority().isBlank()
                        ? labeled.passportIssuingAuthority() : mrz.passportIssuingAuthority());
    }

    private PassportDetailsExtraction parseMrz(String documentLine, String dataLine) {
        String names = documentLine.length() > 5 ? documentLine.substring(5) : "";
        int separator = names.indexOf("<<");
        if (separator < 1) {
            return null;
        }
        String surname = names.substring(0, separator).replace('<', ' ').trim();
        String givenNames = names.substring(separator + 2).replace('<', ' ').trim();
        String fullName = (givenNames + " " + surname).trim().replaceAll("\\s+", " ");
        String paddedData = dataLine.length() < 28
                ? dataLine : dataLine.substring(0, Math.min(44, dataLine.length()));
        String number = paddedData.length() >= 9
                ? paddedData.substring(0, 9).replace("<", "").trim() : "";
        String passportType = documentLine.length() >= 1
                ? documentLine.substring(0, 1) : "";
        String issuingCountryCode = documentLine.length() >= 5
                ? documentLine.substring(2, 5) : "";
        if (!issuingCountryCode.matches("[A-Z]{3}")) {
            issuingCountryCode = "";
        }
        String nationality = paddedData.length() >= 13
                ? countryNameFromMrzCode(paddedData.substring(10, 13)) : "";
        LocalDate dateOfBirth = paddedData.length() >= 19
                ? parseMrzBirthDate(paddedData.substring(13, 19)) : null;
        String gender = paddedData.length() > 20 ? normalizeMrzGender(paddedData.charAt(20)) : "";
        LocalDate expiry = null;
        if (paddedData.length() >= 27) {
            String dateDigits = normalizeMrzDigits(paddedData.substring(21, 27));
            if (dateDigits.matches("\\d{6}")) {
                int twoDigitYear = Integer.parseInt(dateDigits.substring(0, 2));
                int month = Integer.parseInt(dateDigits.substring(2, 4));
                int day = Integer.parseInt(dateDigits.substring(4, 6));
                try {
                    int year = LocalDate.now().getYear() / 100 * 100 + twoDigitYear;
                    expiry = LocalDate.of(year, month, day);
                } catch (RuntimeException ignored) {
                    expiry = null;
                }
            }
        }
        if (surname.isBlank() && givenNames.isBlank() && number.isBlank() && expiry == null
                && dateOfBirth == null && nationality.isBlank() && passportType.isBlank()
                && issuingCountryCode.isBlank()) {
            return null;
        }
        return new PassportDetailsExtraction(givenNames, surname, fullName, number, expiry,
                dateOfBirth, nationality, gender, passportType, issuingCountryCode, null, "");
    }

    private PassportDetailsExtraction parseLabeledFields(String text) {
        String firstName = "";
        String lastName = "";
        String fullName = "";
        String passportNumber = "";
        LocalDate expiry = null;
        LocalDate dateOfBirth = null;
        String nationality = "";
        String gender = "";
        String passportType = "";
        String issuingCountryCode = "";
        LocalDate dateOfIssue = null;
        String issuingAuthority = "";
        Matcher matcher = LABELED_VALUE.matcher(text);
        while (matcher.find()) {
            String label = matcher.group(1).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            String value = matcher.group(2).replaceAll("[^\\p{L}\\p{M}0-9 /.-]", "").trim();
            if (label.equals("surname") || label.equals("last name")) {
                lastName = value;
            } else if (label.startsWith("given name") || label.equals("first name")) {
                firstName = value;
            } else if (label.equals("full name")) {
                fullName = value;
            } else if (label.startsWith("passport")) {
                if (label.equals("passport type")) {
                    passportType = value.toUpperCase(Locale.ROOT);
                } else {
                    passportNumber = value.replaceAll("\\s+", "");
                }
            } else if (label.equals("document type")) {
                passportType = value.toUpperCase(Locale.ROOT);
            } else if (label.startsWith("issuing") || label.equals("country code")) {
                if (label.contains("authority") || label.equals("issuing office")) {
                    issuingAuthority = value;
                } else {
                    issuingCountryCode = value.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
                }
            } else if (label.equals("authority") || label.equals("issued by")
                    || label.equals("place of issue")) {
                issuingAuthority = value;
            } else if (label.equals("date of issue") || label.equals("issue date")
                    || label.equals("date issued") || label.equals("issued on")) {
                dateOfIssue = parseDate(value);
            } else if (label.equals("date of birth") || label.equals("birth date")) {
                dateOfBirth = parseLabeledBirthDate(value);
            } else if (label.equals("nationality")) {
                nationality = value;
            } else if (label.equals("sex") || label.equals("gender")) {
                gender = normalizeLabeledGender(value);
            } else {
                expiry = parseDate(value);
            }
        }
        if (fullName.isBlank()) {
            fullName = (firstName + " " + lastName).trim().replaceAll("\\s+", " ");
        }
        if (firstName.isBlank() && lastName.isBlank() && fullName.isBlank()
                && passportNumber.isBlank() && expiry == null && dateOfBirth == null
                && nationality.isBlank() && passportType.isBlank() && issuingCountryCode.isBlank()
                && dateOfIssue == null && issuingAuthority.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Passport text was not recognized. Enter the passport details manually.");
        }
        return new PassportDetailsExtraction(firstName, lastName, fullName, passportNumber, expiry,
                dateOfBirth, nationality, gender, passportType, issuingCountryCode,
                dateOfIssue, issuingAuthority);
    }

    private LocalDate parseMrzBirthDate(String value) {
        String digits = normalizeMrzDigits(value);
        if (!digits.matches("\\d{6}")) {
            return null;
        }
        int twoDigitYear = Integer.parseInt(digits.substring(0, 2));
        int currentYear = LocalDate.now().getYear();
        int year = currentYear / 100 * 100 + twoDigitYear;
        if (year > currentYear) {
            year -= 100;
        }
        try {
            return LocalDate.of(year, Integer.parseInt(digits.substring(2, 4)),
                    Integer.parseInt(digits.substring(4, 6)));
        } catch (java.time.DateTimeException exception) {
            return null;
        }
    }

    private LocalDate parseLabeledBirthDate(String value) {
        for (java.time.format.DateTimeFormatter formatter : List.of(
                java.time.format.DateTimeFormatter.ISO_LOCAL_DATE,
                java.time.format.DateTimeFormatter.ofPattern("d/M/uuuu"),
                java.time.format.DateTimeFormatter.ofPattern("d-M-uuuu"),
                java.time.format.DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH),
                java.time.format.DateTimeFormatter.ofPattern("ddMMyyyy"),
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (java.time.format.DateTimeParseException ignored) {
                // Try the next printed date format.
            }
        }
        String digits = value.replaceAll("\\D", "");
        return digits.length() == 6 ? parseMrzBirthDate(digits) : null;
    }

    private String countryNameFromMrzCode(String code) {
        if (!code.matches("[A-Z]{3}") || "XXX".equals(code)) {
            return "";
        }
        for (String countryCode : Locale.getISOCountries()) {
            Locale locale = new Locale("", countryCode);
            if (locale.getISO3Country().equals(code)) {
                return locale.getDisplayCountry(Locale.ENGLISH);
            }
        }
        return code;
    }

    private String normalizeMrzGender(char code) {
        return switch (code) {
            case 'F' -> "Female";
            case 'M' -> "Male";
            case 'X' -> "Other";
            default -> "";
        };
    }

    private String normalizeLabeledGender(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "F", "FEMALE" -> "Female";
            case "M", "MALE" -> "Male";
            case "X", "OTHER" -> "Other";
            default -> "";
        };
    }

    private LocalDate parseDate(String value) {
        String digits = value.replaceAll("\\D", "");
        if (digits.length() == 6) {
            String normalized = normalizeMrzDigits(digits);
            if (normalized.matches("\\d{6}")) {
                int year = LocalDate.now().getYear() / 100 * 100
                        + Integer.parseInt(normalized.substring(0, 2));
                int month = Integer.parseInt(normalized.substring(2, 4));
                int day = Integer.parseInt(normalized.substring(4, 6));
                try {
                    return LocalDate.of(year, month, day);
                } catch (RuntimeException ignored) {
                    return null;
                }
            }
        }
        for (java.time.format.DateTimeFormatter formatter : List.of(
                java.time.format.DateTimeFormatter.ofPattern("d/M/uuuu"),
                java.time.format.DateTimeFormatter.ofPattern("d-M-uuuu"),
                java.time.format.DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH))) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (java.time.format.DateTimeParseException ignored) {
                // Try the next printed date format.
            }
        }
        if (digits.length() == 8) {
            String normalized = normalizeMrzDigits(digits);
            for (java.time.format.DateTimeFormatter formatter : List.of(
                    java.time.format.DateTimeFormatter.ofPattern("ddMMyyyy"),
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))) {
                try {
                    return LocalDate.parse(normalized, formatter);
                } catch (java.time.format.DateTimeParseException ignored) {
                    // Try the next numeric date format.
                }
            }
        }
        return null;
    }

    private String normalizeMrzLine(String line) {
        return line.toUpperCase(Locale.ROOT)
                .replace('«', '<')
                .replace('‹', '<')
                .replaceAll("[^A-Z0-9<]", "");
    }

    private String normalizeMrzDigits(String value) {
        return value.toUpperCase(Locale.ROOT)
                .replace('O', '0').replace('Q', '0').replace('D', '0')
                .replace('I', '1').replace('L', '1')
                .replace('Z', '2').replace('S', '5')
                .replace('G', '6').replace('B', '8');
    }

    private void deleteTemporaryFile(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not clean up temporary passport OCR files.", exception);
        }
    }
}
