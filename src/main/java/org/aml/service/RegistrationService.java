package org.aml.service;

import lombok.RequiredArgsConstructor;
import org.aml.constants.AMLConstants;
import org.aml.entity.Role;
import org.aml.entity.User;
import org.aml.exception.UserAlreadyExistsException;
import org.aml.repository.UserRepository;
import org.apache.poi.ss.usermodel.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public void bulkRegistration(
            MultipartFile file) {

        try (Workbook workbook =
                     WorkbookFactory.create(file.getInputStream())) {

            Sheet sheet = workbook.getSheetAt(0);

            List<User> users = new ArrayList<>();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {

                Row row = sheet.getRow(i);

                if (row == null) {
                    continue;
                }

                String firstName =
                        getCellValue(row.getCell(0));

                String lastName =
                        getCellValue(row.getCell(1));

                String email =
                        getCellValue(row.getCell(2));

                String companyName =
                        getCellValue(row.getCell(3));

                String phoneNumber =
                        getCellValue(row.getCell(4));

                String country =
                        getCellValue(row.getCell(5));

                String password =
                        getCellValue(row.getCell(6));

                String role =
                        getCellValue(row.getCell(7));

                if(role.equalsIgnoreCase("user")){
                    role = "ROLE_USER";
                }else if(role.equalsIgnoreCase("admin")){
                    role = "ROLE_ADMIN";
                }

                if (userRepository.findByEmail(email).isPresent()) {
                    throw new UserAlreadyExistsException(
                            AMLConstants.USER_ALREADY_EXISTS + email);
                }
                if (userRepository.findByPhoneNumber(phoneNumber).isPresent()) {
                    throw new UserAlreadyExistsException(
                            AMLConstants.USER_ALREADY_EXISTS_PHONE_NUMBER + phoneNumber);
                }
                if (role.isBlank()) {
                    throw new IllegalArgumentException("Role column is missing a value at row " + (i + 1));
                }
                String normalizedRole = role.trim().toUpperCase();
                User user = User.builder()
                        .firstName(firstName)
                        .lastName(lastName)
                        .email(email)
                        .companyName(companyName)
                        .phoneNumber(phoneNumber)
                        .country(country)
                        .password(
                                passwordEncoder.encode(password))
                        .role(Role.valueOf(normalizedRole))
                        .build();

                users.add(user);
            }

            userRepository.saveAll(users);

        } catch (Exception e) {
            e.printStackTrace();
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
                    String.valueOf(
                            (long) cell.getNumericCellValue());

            case BOOLEAN ->
                    String.valueOf(
                            cell.getBooleanCellValue());

            default ->
                    "";
        };
    }
}