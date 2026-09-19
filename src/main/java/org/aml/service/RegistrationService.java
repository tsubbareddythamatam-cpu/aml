package org.aml.service;

import lombok.RequiredArgsConstructor;
import org.aml.constants.AMLConstants;
import org.aml.dto.BulkRegistrationResponse;
import org.aml.entity.Role;
import org.aml.entity.User;
import org.aml.exception.ErrorResponse;
import org.aml.exception.UserAlreadyExistsException;
import org.aml.repository.UserRepository;
import org.apache.poi.ss.usermodel.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public BulkRegistrationResponse bulkRegistration(MultipartFile file) {

        List<User> users = new ArrayList<>();
        List<String> failedEmails = new ArrayList<>();
        List<ErrorResponse> errors = new ArrayList<>();

        int totalRecords = 0;
        int successRecords = 0;
        int failedRecords = 0;

        try (Workbook workbook =
                     WorkbookFactory.create(file.getInputStream())) {

            Sheet sheet = workbook.getSheetAt(0);

            totalRecords = sheet.getLastRowNum();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {

                Row row = sheet.getRow(i);

                if (row == null) {
                    continue;
                }

                try {

                    String firstName = getCellValue(row.getCell(0));
                    String lastName = getCellValue(row.getCell(1));
                    String email = getCellValue(row.getCell(2));
                    String companyName = getCellValue(row.getCell(3));
                    String phoneNumber = getCellValue(row.getCell(4));
                    String country = getCellValue(row.getCell(5));
                    String password = getCellValue(row.getCell(6));
                    String role = getCellValue(row.getCell(7));

                    if (role.equalsIgnoreCase("user")) {
                        role = "ROLE_USER";
                    } else if (role.equalsIgnoreCase("admin")) {
                        role = "ROLE_ADMIN";
                    }

                    if (userRepository.findByEmail(email).isPresent()) {

                        failedRecords++;
                        failedEmails.add(email);

                        errors.add(ErrorResponse.builder()
                                .errorCode("USR_001")
                                .message("User already exists with email: " + email)
                                .status(409)
                                .timestamp(LocalDateTime.now())
                                .build());

                        continue;
                    }

                    if (userRepository.findByPhoneNumber(phoneNumber).isPresent()) {

                        failedRecords++;
                        failedEmails.add(email);

                        errors.add(ErrorResponse.builder()
                                .errorCode("USR_002")
                                .message("Phone number already exists: " + phoneNumber)
                                .status(409)
                                .timestamp(LocalDateTime.now())
                                .build());

                        continue;
                    }

                    if (role.isBlank()) {

                        failedRecords++;
                        failedEmails.add(email);

                        errors.add(ErrorResponse.builder()
                                .errorCode("USR_003")
                                .message("Role column is missing at row " + (i + 1))
                                .status(400)
                                .timestamp(LocalDateTime.now())
                                .build());

                        continue;
                    }

                    String normalizedRole = role.trim().toUpperCase();

                    User user = User.builder()
                            .firstName(firstName)
                            .lastName(lastName)
                            .email(email)
                            .companyName(companyName)
                            .phoneNumber(phoneNumber)
                            .country(country)
                            .password(passwordEncoder.encode(password))
                            .role(Role.valueOf(normalizedRole))
                            .build();

                    users.add(user);
                    successRecords++;

                } catch (Exception ex) {

                    failedRecords++;

                    String email = "";
                    Cell emailCell = row.getCell(2);
                    if (emailCell != null) {
                        email = getCellValue(emailCell);
                    }

                    failedEmails.add(email);

                    errors.add(ErrorResponse.builder()
                            .errorCode("USR_500")
                            .message("Row " + (i + 1) + " failed: "
                                    + ex.getMessage())
                            .status(500)
                            .timestamp(LocalDateTime.now())
                            .build());
                }
            }

            if (!users.isEmpty()) {
                userRepository.saveAll(users);
            }

            return BulkRegistrationResponse.builder()
                    .totalRecords(totalRecords)
                    .successRecords(successRecords)
                    .failedRecords(failedRecords)
                    .failedEmails(failedEmails)
                    .errors(errors)
                    .build();

        } catch (Exception e) {

            throw new RuntimeException(
                    "Error processing Excel file",
                    e);
        }
    }

    private String getCellValue(Cell cell) {

        if (cell == null) {
            return "";
        }

        return switch (cell.getCellType()) {

            case STRING ->
                    cell.getStringCellValue();

            case NUMERIC ->
                    String.valueOf((long) cell.getNumericCellValue());

            case BOOLEAN ->
                    String.valueOf(cell.getBooleanCellValue());

            default ->
                    "";
        };
    }
}