package org.aml.service;

import lombok.RequiredArgsConstructor;
import org.aml.dto.*;
import org.aml.model.Role;
import org.aml.model.User;
import org.aml.exception.ErrorResponse;
import org.aml.repository.UserRepository;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "usersDirectory")
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Retrieves all users and maps them to response DTOs.
     *
     * @param token authenticated token passed from the calling endpoint
     * @return all user profiles
     */
    @Cacheable(key = "'all-users'")
    @Transactional(readOnly = true)
    public List<UserResponse> retrieveAllUsers(String token) {
        logger.info("⚡ Cache Miss: Accessing database records to fetch active user profile directory");

        List<UserResponse> userList = userRepository.findAll().stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());

        // 📊 Fetch and log the count of records
        int recordCount = userList.size();
        logger.info("🎯 Successfully retrieved directory snapshot containing {} user profiles", recordCount);

        return userList;
    }

    /**
     * Retrieves a user profile by its database identifier.
     *
     * @param id user identifier
     * @return matching user profile
     * @throws ResponseStatusException if no user has the identifier
     */
    @Cacheable(key = "'id:' + #id")
    @Transactional(readOnly = true)
    public UserResponse retrieveUserById(Long id) {
        logger.info("⚡ Cache Miss: Accessing database to fetch user profile for ID: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Requested user record profile does not exist."));

        return mapToUserResponse(user);
    }

    /**
     * Retrieves a user profile by email address.
     *
     * @param email email address to search for
     * @return matching user profile
     * @throws ResponseStatusException if no user has the email address
     */
    @Cacheable(key = "'email:' + #email")
    @Transactional(readOnly = true)
    public UserResponse retrieveUserByEmail(String email) {
        logger.info("⚡ Cache Miss: Accessing database to fetch user profile by email");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "No registered account matches that email address."));

        return mapToUserResponse(user);
    }

    /**
     * Maps a user entity to a response DTO.
     *
     * @param user user entity to map
     * @return response DTO, or {@code null} when the entity is null
     */
    private UserResponse mapToUserResponse(User user) {
        if (user == null) {
            return null;
        }

        UserResponse.UserResponseBuilder responseBuilder = UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt());

        return responseBuilder.build();
    }

    /**
     * Imports users from the first worksheet of an uploaded workbook.
     *
     * @param file workbook containing user records
     * @return counts and details of successfully and unsuccessfully processed rows
     * @throws RuntimeException if the workbook cannot be read or processed
     */
    @CacheEvict(allEntries = true)
    public BulkRegistrationResponse bulkRegistration(MultipartFile file) {

        List<User> users = new ArrayList<>();
        List<String> failedEmails = new ArrayList<>();
        List<ErrorResponse> errors = new ArrayList<>();

        int totalRecords = 0;
        int successRecords = 0;
        int failedRecords = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {

            Sheet sheet = workbook.getSheetAt(0);
            totalRecords = sheet.getLastRowNum();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);

                if (row == null || isRowEmpty(row)) {
                    continue;
                }

                String email = null;
                try {
                    // 1. Dynamic Extraction Layer: Pulls value if it exists, otherwise sets explicitly to null
                    String companyName = getCleanValue(row.getCell(0));
                    email = getCleanValue(row.getCell(8));

                    // Optional: Check if your Excel template expands to include columns 9, 10, and 11
                    String firstName = getCleanValue(row.getCell(9));
                    String lastName = getCleanValue(row.getCell(10));
                    String rawPassword = getCleanValue(row.getCell(11));
                    String roleStr = getCleanValue(row.getCell(12));

                    // 2. Strict Core Email Validation Block
                    if (email == null) {
                        failedRecords++;
                        errors.add(ErrorResponse.builder()
                                .errorCode("USR_003")
                                .message("Row " + (i + 1) + " failed: Email column value is empty or missing.")
                                .status(400)
                                .timestamp(LocalDateTime.now())
                                .build());
                        continue;
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

                    // 3. Fallback Logic: If firstName isn't in Excel, dynamically isolate first word from company name
                    if (firstName == null && companyName != null) {
                        firstName = companyName.contains(" ") ? companyName.split(" ")[0] : companyName;
                    }

                    // 4. Encrypt Password only if it was actually provided in the sheet row configuration
                    String encodedPassword = null;
                    if (rawPassword != null) {
                        encodedPassword = passwordEncoder.encode(rawPassword);
                    }

                    // 5. Role Processing
                    Role targetRole = Role.USER;
                    if (roleStr != null && !roleStr.isBlank()) {
                        String normalizedRole = roleStr.trim().toUpperCase();

                        if (normalizedRole.contains("ADMIN")) {
                            targetRole = Role.ADMIN;
                        }
                    }

                    // 6. Build Base User Model
                    User user = User.builder()
                            .email(email)
                            .companyName(companyName)
                            .firstName(firstName)      // Parsed from sheet, fallback to split company name, or null
                            .lastName(lastName)        // Extracted cleanly from sheet column index, or true null
                            .password(encodedPassword) // Encoded string if provided, or clean null
                            .role(targetRole)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();

                    users.add(user);
                    successRecords++;

                } catch (Exception ex) {
                    failedRecords++;
                    failedEmails.add(email == null ? "UNKNOWN_ROW_" + (i + 1) : email);
                    errors.add(ErrorResponse.builder()
                            .errorCode("USR_500")
                            .message("Row " + (i + 1) + " parsing failed: " + ex.getMessage())
                            .status(500)
                            .timestamp(LocalDateTime.now())
                            .build());
                }
            }

            // 9. Multi-Row Bulk Database Commit
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
            logger.error("Critical Workbook Exception inside bulkRegistration: ", e);
            throw new RuntimeException("Error processing corporate Excel template document mapping sequence context", e);
        }
    }

    /**
     * Checks whether a spreadsheet row contains any nonblank cells.
     *
     * @param row row to inspect
     * @return {@code true} if the row is null or all cells are blank
     */
    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK && !cell.toString().trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Converts a spreadsheet cell to trimmed text, returning null for blank cells.
     *
     * @param cell cell to convert
     * @return cleaned cell value or {@code null}
     */
    private String getCleanValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        String value = switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                yield String.valueOf((long) cell.getNumericCellValue());
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };

        return (value == null || value.trim().isBlank()) ? null : value.trim();
    }

    /**
     * Updates supplied user fields while retaining unspecified values.
     *
     * @param id user identifier
     * @param request fields to update
     * @return updated user response
     * @throws ResponseStatusException if no user has the identifier
     */
    @CacheEvict(allEntries = true)
    @Transactional
    public UserResponse updateUserDetails(Long id, UpdateUserRequest request) {
        logger.info("🔄 Initiating profile update sequence context for User ID: {}", id);

        // 1. Fetch existing user or throw 404
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Requested user record profile does not exist."));

        // 2. Dynamically update User fields if provided
        if (request.getFirstName() != null) user.setFirstName(request.getFirstName().trim());
        if (request.getLastName() != null) user.setLastName(request.getLastName().trim());
        if (request.getPhoneNumber() != null) user.setPhoneNumber(request.getPhoneNumber().trim());
        if (request.getRole() != null && !request.getRole().isBlank()) {
            String normalizedRole = request.getRole().trim().toUpperCase();

            if (normalizedRole.contains("ADMIN")) {
                user.setRole(Role.ADMIN);
                logger.info("👑 User ID: {} has been elevated to admin authority", id);
            } else {
                user.setRole(Role.USER);
                logger.info("👤 User ID: {} role assigned standard user tracking bounds", id);
            }
        }
        user.setUpdatedAt(LocalDateTime.now());

        // 3. Save and map back to response.
        User updatedUser = userRepository.save(user);
        logger.info("✅ Successfully persisted updated account metadata logs for User ID: {}", id);

        return mapToUserResponse(updatedUser);
    }

}