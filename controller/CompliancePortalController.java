package org.aml.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.aml.dto.ComplianceForm;
import org.aml.dto.BulkRegistrationResponse;
import org.aml.dto.CustomerDto;
import org.aml.dto.KycDataDto;
import org.aml.dto.PassportReviewForm;
import org.aml.dto.PassportDetailsExtraction;
import org.aml.dto.PassportUploadValidationResponse;
import org.aml.model.User;
import org.aml.repository.UserRepository;
import org.aml.service.ComplianceService;
import org.aml.service.IndividualCustomerBulkImportService;
import org.aml.service.PassportOcrService;
import org.aml.service.PassportDocumentService;
import org.aml.service.SourceEvidenceDocumentService;
import org.aml.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Controller
@RequestMapping("/portal")
@RequiredArgsConstructor
public class CompliancePortalController {

    private static final Set<String> PROCESS_STATUSES =
            Set.of("IN_REVIEW", "ACTIVE", "PENDING", "REJECTED", "APPROVED");

    private final ComplianceService complianceService;
    private final UserRepository userRepository;
    private final PassportDocumentService passportDocumentService;
    private final SourceEvidenceDocumentService sourceEvidenceDocumentService;
    private final PassportOcrService passportOcrService;
    private final UserService userService;
    private final IndividualCustomerBulkImportService individualCustomerBulkImportService;

    @ModelAttribute("currentUser")
    public String currentUser(Authentication authentication) {
        return authentication == null ? "" : authentication.getName();
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error,
                        @RequestParam(required = false) String logout,
                        Model model) {
        model.addAttribute("loginError", error != null);
        model.addAttribute("loggedOut", logout != null);
        return "portal/login";
    }

    @GetMapping("/compliance")
    public String listCompliance(@RequestParam(required = false) String filter, Model model) {
        model.addAttribute("reports", complianceService.fetchAll());
        model.addAttribute("activeFilter", filter == null ? "ALL" : filter);
        return "portal/compliance-list";
    }

    private String mostFrequent(java.util.Map<String, Integer> values) {
        return values.entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey)
                .orElse("");
    }

    @GetMapping("/onboarding")
    public String onboardingHome() {
        return "portal/onboarding";
    }

    @GetMapping("/onboarding/self")
    public String selfOnboarding() {
        return "portal/self-onboarding";
    }

    @GetMapping("/bulk")
    public String bulkOnboarding() {
        return "portal/bulk-onboarding";
    }

    @PostMapping("/bulk")
    public String processBulkOnboarding(@RequestParam("file") MultipartFile file,
                                        RedirectAttributes redirectAttributes) {
        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("bulkError", "Choose an Excel workbook to upload.");
            return "redirect:/portal/bulk";
        }
        BulkRegistrationResponse result = userService.bulkRegistration(file);
        redirectAttributes.addFlashAttribute("bulkResult", result);
        return "redirect:/portal/bulk";
    }

    @PostMapping("/bulk/individual")
    public String processBulkIndividualOnboarding(@RequestParam("file") MultipartFile file,
                                                  Authentication authentication,
                                                  RedirectAttributes redirectAttributes) {
        try {
            BulkRegistrationResponse result =
                    individualCustomerBulkImportService.importWorkbook(file, authentication.getName());
            redirectAttributes.addFlashAttribute("bulkIndividualResult", result);
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("bulkIndividualError", exception.getReason());
        }
        return "redirect:/portal/bulk";
    }

    @GetMapping("/team")
    public String teamMembers(Model model) {
        model.addAttribute("teamMembers", userService.retrieveAllUsers(""));
        return "portal/team-members";
    }

    @GetMapping("/compliance/new")
    public String newCompliance(@RequestParam(defaultValue = "INDIVIDUAL") String type, Model model) {
        String normalizedType = type.trim().toUpperCase(java.util.Locale.ROOT);
        if (!normalizedType.equals("INDIVIDUAL") && !normalizedType.equals("COMPANY")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose individual or company onboarding.");
        }
        ComplianceForm form = new ComplianceForm();
        form.setOnboardingType(normalizedType);
        model.addAttribute("complianceForm", form);
        addFormOptions(model);
        model.addAttribute("pageTitle", "Customer onboarding");
        model.addAttribute("formAction", "/portal/compliance");
        model.addAttribute("editing", false);
        model.addAttribute("passportVerified", false);
        return "portal/compliance-form";
    }

    @PostMapping("/compliance")
    public String createCompliance(@Valid @ModelAttribute("complianceForm") ComplianceForm form,
                                   BindingResult bindingResult,
                                   Authentication authentication,
                                   Model model,
                                   RedirectAttributes redirectAttributes,
                                   @RequestParam(name = "passportImage", required = false) MultipartFile passportImage,
                                   @RequestParam(name = "sourceOfWealthEvidence", required = false) MultipartFile sourceOfWealthEvidence,
                                   @RequestParam(name = "sourceOfFundsEvidence", required = false) MultipartFile sourceOfFundsEvidence) {
        validateOnboardingType(form, bindingResult);
        validateRegistrationContactDetails(form, bindingResult);
        validateUserId(form.getUserId(), bindingResult);
        validatePassportDetails(form, bindingResult);
        validateKycDates(form, bindingResult);
        validateSourceEvidence(sourceOfWealthEvidence, sourceOfFundsEvidence, bindingResult);
        boolean hasPassportImage = passportImage != null && !passportImage.isEmpty();
        if (!hasPassportImage) {
            bindingResult.reject("passportImage.required", "Upload a passport image to continue.");
        } else {
            try {
                passportDocumentService.validateUpload(passportImage);
            } catch (ResponseStatusException exception) {
                bindingResult.reject("passportImage.invalid", exception.getReason());
            }
        }
        if (bindingResult.hasErrors()) {
            addFormOptions(model);
            model.addAttribute("pageTitle", "Customer onboarding");
            model.addAttribute("formAction", "/portal/compliance");
            model.addAttribute("editing", false);
            model.addAttribute("passportVerified", false);
            return "portal/compliance-form";
        }

        CustomerDto created;
        try {
            created = complianceService.processReport(toCustomerDto(form, authentication.getName()));
        } catch (ResponseStatusException exception) {
            model.addAttribute("errorMessage", exception.getReason());
            addNewComplianceFormModel(model);
            return "portal/compliance-form";
        }
        try {
            sourceEvidenceDocumentService.upload(created.getAmlId(), sourceOfWealthEvidence, sourceOfFundsEvidence);
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "The compliance record was created, but the source evidence upload failed: " + exception.getReason());
            return "redirect:/portal/compliance/" + created.getAmlId() + "/passport";
        }
        PassportReviewForm reviewForm = newPassportReviewForm(form);
        String message = "Passport image uploaded. Review the details below and confirm they match the document.";
        try {
            passportDocumentService.upload(created.getAmlId(), passportImage);
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Compliance record was created, but the passport image upload failed: " + exception.getReason());
            return "redirect:/portal/compliance/" + created.getAmlId() + "/passport";
        }
        try {
            PassportDocumentService.StoredPassport stored = passportDocumentService.get(created.getAmlId());
            reviewForm = toPassportReviewForm(passportOcrService.extract(stored.path()));
            message = "Passport image uploaded. Verify the OCR-detected details against the document before confirming.";
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    exception.getReason() + " Enter or correct the passport details manually.");
        }
        redirectAttributes.addFlashAttribute("passportReviewForm", reviewForm);
        redirectAttributes.addFlashAttribute("successMessage", message);
        return "redirect:/portal/compliance/" + created.getAmlId() + "/passport";
    }

    @GetMapping("/compliance/{amlId}/edit")
    public String editCompliance(@PathVariable String amlId,
                                 @RequestParam(defaultValue = "false") boolean registration,
                                 Model model) {
        CustomerDto report = complianceService.fetchByAmlId(amlId);
        if (!"VERIFIED".equals(report.getPassportVerificationStatus())) {
            return "redirect:/portal/compliance/" + amlId + "/passport";
        }
        ComplianceForm form = toForm(report);
        model.addAttribute("complianceForm", form);
        addFormOptions(model);
        model.addAttribute("pageTitle", registration ? "Register compliance record" : "Edit compliance record");
        model.addAttribute("formAction", "/portal/compliance/" + amlId);
        model.addAttribute("editing", true);
        model.addAttribute("amlId", amlId);
        model.addAttribute("passportVerified", true);
        addEvidenceDocumentNames(report, model);
        return "portal/compliance-form";
    }

    @GetMapping("/compliance/{amlId}")
    public String viewCompliance(@PathVariable String amlId, Model model) {
        model.addAttribute("report", complianceService.fetchByAmlId(amlId));
        return "portal/compliance-view";
    }

    @PostMapping("/compliance/{amlId}")
    public String updateCompliance(@PathVariable String amlId,
                                   @Valid @ModelAttribute("complianceForm") ComplianceForm form,
                                   BindingResult bindingResult,
                                   Authentication authentication,
                                   Model model,
                                   RedirectAttributes redirectAttributes,
                                   @RequestParam(name = "sourceOfWealthEvidence", required = false) MultipartFile sourceOfWealthEvidence,
                                   @RequestParam(name = "sourceOfFundsEvidence", required = false) MultipartFile sourceOfFundsEvidence) {
        validateOnboardingType(form, bindingResult);
        validateUserId(form.getUserId(), bindingResult);
        validateKycDates(form, bindingResult);
        validateSourceEvidence(sourceOfWealthEvidence, sourceOfFundsEvidence, bindingResult);
        CustomerDto existing = complianceService.fetchByAmlId(amlId);
        String email = form.getEmailAddress() == null ? "" : form.getEmailAddress().trim();
        String confirmedEmail = form.getConfirmEmailAddress() == null
                ? "" : form.getConfirmEmailAddress().trim();
        if (!email.isEmpty() && !confirmedEmail.isEmpty()
                && !email.equalsIgnoreCase(confirmedEmail)) {
                bindingResult.rejectValue("confirmEmailAddress", "confirmEmailAddress.mismatch",
                        "Email addresses must match.");
        }
        if (bindingResult.hasErrors()) {
            addFormOptions(model);
            model.addAttribute("pageTitle", "Edit compliance record");
            model.addAttribute("formAction", "/portal/compliance/" + amlId);
            model.addAttribute("editing", true);
            model.addAttribute("amlId", amlId);
            model.addAttribute("passportVerified", true);
            addEvidenceDocumentNames(existing, model);
            return "portal/compliance-form";}

        CustomerDto update = toCustomerDto(form, authentication.getName());
        update.setStatus(null);
        boolean passportNeedsReview = "VERIFIED".equals(existing.getPassportVerificationStatus())
                && !normalizeName(existing.getFullName()).equals(normalizeName(form.getFullName()));
        try {
            complianceService.updateReport(amlId, update);
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getReason());
            return "redirect:/portal/compliance/" + amlId + "/edit";
        }
        try {
            sourceEvidenceDocumentService.upload(amlId, sourceOfWealthEvidence, sourceOfFundsEvidence);
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getReason());
            return "redirect:/portal/compliance/" + amlId + "/edit";
        }
        if (passportNeedsReview) {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Customer name changed. Review the passport again before continuing compliance processing.");
            return "redirect:/portal/compliance/" + amlId + "/passport";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Compliance record updated.");
        return "redirect:/portal/compliance";
    }

    @GetMapping("/compliance/{amlId}/delete")
    public String confirmDelete(@PathVariable String amlId, Model model) {
        model.addAttribute("report", complianceService.fetchByAmlId(amlId));
        return "portal/compliance-delete";
    }

    @PostMapping("/compliance/{amlId}/delete")
    public String deleteCompliance(@PathVariable String amlId,
                                   RedirectAttributes redirectAttributes) {
        complianceService.deleteByAmlId(amlId);
        redirectAttributes.addFlashAttribute("successMessage", "Compliance record deleted.");
        return "redirect:/portal/compliance";
    }

    @PostMapping("/compliance/{amlId}/status")
    public String updateStatus(@PathVariable String amlId,
                               @RequestParam String status,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        if (!PROCESS_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid process status.");
        }

        CustomerDto update = new CustomerDto();
        update.setStatus(status);
        update.setOnboardedByUser(authentication.getName());
        try {
            complianceService.updateReport(amlId, update);
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getReason());
            return "redirect:/portal/compliance";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Process status updated.");
        return "redirect:/portal/compliance";
    }

    @GetMapping("/compliance/{amlId}/passport")
    public String passportReview(@PathVariable String amlId, Model model) {
        CustomerDto report = complianceService.fetchByAmlId(amlId);
        model.addAttribute("report", report);
        if (!model.containsAttribute("passportReviewForm")) {
            PassportReviewForm reviewForm = toPassportReviewForm(report);
            if (report.getPassportDocumentName() != null && hasMissingPassportFields(reviewForm)) {
                try {
                    PassportDocumentService.StoredPassport stored = passportDocumentService.get(amlId);
                    mergeMissingPassportFields(reviewForm,
                            toPassportReviewForm(passportOcrService.extract(stored.path())));
                } catch (ResponseStatusException exception) {
                    if (hasMissingPassportFields(reviewForm)) {
                        model.addAttribute("errorMessage",
                                exception.getReason() + " Enter or correct the passport details manually.");
                    }
                }
            }
            model.addAttribute("passportReviewForm", reviewForm);
        }
        return "portal/passport-review";
    }

    @PostMapping(value = "/compliance/passport/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public PassportUploadValidationResponse validatePassportUpload(
            @RequestParam("passportImage") MultipartFile passportImage) {
        Path temporaryImage = passportDocumentService.createTemporaryValidatedUpload(passportImage);
        try {
            PassportDetailsExtraction extracted = passportOcrService.extract(temporaryImage);
            return new PassportUploadValidationResponse(true,
                    "Image is valid. Check the extracted details against the passport.", extracted);
        } catch (ResponseStatusException exception) {
            return new PassportUploadValidationResponse(true,
                    exception.getReason() + " You can enter the passport details manually.", null);
        } finally {
            passportDocumentService.deleteTemporaryUpload(temporaryImage);
        }
    }

    @PostMapping("/compliance/{amlId}/passport/upload")
    public String uploadPassport(@PathVariable String amlId,
                                 @RequestParam("passportImage") MultipartFile passportImage,
                                 RedirectAttributes redirectAttributes) {
        try {
            passportDocumentService.upload(amlId, passportImage);
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getReason());
            return "redirect:/portal/compliance/" + amlId + "/passport";
        }
        PassportReviewForm reviewForm = null;
        try {
            PassportDocumentService.StoredPassport stored = passportDocumentService.get(amlId);
            reviewForm = toPassportReviewForm(passportOcrService.extract(stored.path()));
            redirectAttributes.addFlashAttribute("successMessage",
                    "Passport text was read locally. Review and correct all detected details before confirming.");
        } catch (ResponseStatusException exception) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    exception.getReason() + " Enter or correct the passport details manually.");
        }
        if (reviewForm != null) {
            redirectAttributes.addFlashAttribute("passportReviewForm", reviewForm);
        }
        return "redirect:/portal/compliance/" + amlId + "/passport";
    }

    private PassportReviewForm newPassportReviewForm(ComplianceForm form) {
        PassportReviewForm reviewForm = new PassportReviewForm();
        reviewForm.setPassportFullName(form.getPassportFullName());
        reviewForm.setFirstName(form.getPassportFirstName());
        reviewForm.setLastName(form.getPassportLastName());
        reviewForm.setPassportNumber(form.getPassportNumber());
        reviewForm.setPassportType(form.getPassportType());
        reviewForm.setPassportIssuingCountryCode(form.getPassportIssuingCountryCode());
        if (form.getPassportExpiry() != null && !form.getPassportExpiry().isBlank()) {
            reviewForm.setPassportExpiryDate(LocalDate.parse(form.getPassportExpiry()));
        }
        if (form.getPassportDateOfIssue() != null && !form.getPassportDateOfIssue().isBlank()) {
            reviewForm.setPassportDateOfIssue(LocalDate.parse(form.getPassportDateOfIssue()));
        }
        reviewForm.setPassportIssuingAuthority(form.getPassportIssuingAuthority());
        if (form.getDateOfBirth() != null && !form.getDateOfBirth().isBlank()) {
            reviewForm.setDateOfBirth(LocalDate.parse(form.getDateOfBirth()));
        }
        reviewForm.setNationality(form.getNationality());
        reviewForm.setGender(form.getGender());
        return reviewForm;
    }

    private PassportReviewForm toPassportReviewForm(PassportDetailsExtraction extracted) {
        PassportReviewForm reviewForm = new PassportReviewForm();
        reviewForm.setFirstName(extracted.firstName());
        reviewForm.setLastName(extracted.lastName());
        reviewForm.setPassportFullName(extracted.fullName());
        reviewForm.setPassportNumber(extracted.passportNumber());
        reviewForm.setPassportExpiryDate(extracted.passportExpiryDate());
        reviewForm.setPassportDateOfIssue(extracted.passportDateOfIssue());
        reviewForm.setPassportIssuingAuthority(extracted.passportIssuingAuthority());
        reviewForm.setPassportType(extracted.passportType());
        reviewForm.setPassportIssuingCountryCode(extracted.passportIssuingCountryCode());
        reviewForm.setDateOfBirth(extracted.dateOfBirth());
        reviewForm.setNationality(extracted.nationality());
        reviewForm.setGender(extracted.gender());
        return reviewForm;
    }

    private PassportReviewForm toPassportReviewForm(CustomerDto report) {
        PassportReviewForm reviewForm = new PassportReviewForm();
        reviewForm.setFirstName(report.getPassportFirstName());
        reviewForm.setLastName(report.getPassportLastName());
        reviewForm.setPassportFullName(report.getPassportFullName());
        reviewForm.setPassportNumber(report.getVerifiedPassportNumber());
        reviewForm.setPassportType(report.getPassportType());
        reviewForm.setPassportIssuingCountryCode(report.getPassportIssuingCountryCode());
        reviewForm.setPassportExpiryDate(report.getPassportExpiryDate());
        reviewForm.setPassportDateOfIssue(report.getPassportDateOfIssue());
        reviewForm.setPassportIssuingAuthority(report.getPassportIssuingAuthority());
        KycDataDto.CustomerInformation customerInformation = report.getKycData() == null
                ? null : report.getKycData().getCustomerInformation();
        if (customerInformation != null) {
            reviewForm.setDateOfBirth(parseDate(customerInformation.getDateOfBirth()));
            reviewForm.setNationality(customerInformation.getNationality());
            reviewForm.setGender(customerInformation.getGender());
        }
        return reviewForm;
    }

    private void mergeMissingPassportFields(PassportReviewForm target, PassportReviewForm extracted) {
        if (isBlank(target.getFirstName())) target.setFirstName(extracted.getFirstName());
        if (isBlank(target.getLastName())) target.setLastName(extracted.getLastName());
        if (isBlank(target.getPassportFullName())) target.setPassportFullName(extracted.getPassportFullName());
        if (isBlank(target.getPassportNumber())) target.setPassportNumber(extracted.getPassportNumber());
        if (isBlank(target.getPassportType())) target.setPassportType(extracted.getPassportType());
        if (isBlank(target.getPassportIssuingCountryCode())) {
            target.setPassportIssuingCountryCode(extracted.getPassportIssuingCountryCode());
        }
        if (target.getPassportExpiryDate() == null) target.setPassportExpiryDate(extracted.getPassportExpiryDate());
        if (target.getPassportDateOfIssue() == null) {
            target.setPassportDateOfIssue(extracted.getPassportDateOfIssue());
        }
        if (isBlank(target.getPassportIssuingAuthority())) {
            target.setPassportIssuingAuthority(extracted.getPassportIssuingAuthority());
        }
        if (target.getDateOfBirth() == null) target.setDateOfBirth(extracted.getDateOfBirth());
        if (isBlank(target.getNationality())) target.setNationality(extracted.getNationality());
        if (isBlank(target.getGender())) target.setGender(extracted.getGender());
    }

    private boolean hasMissingPassportFields(PassportReviewForm form) {
        return isBlank(form.getFirstName()) || isBlank(form.getLastName())
                || isBlank(form.getPassportFullName()) || isBlank(form.getPassportNumber())
                || isBlank(form.getPassportType()) || isBlank(form.getPassportIssuingCountryCode())
                || form.getPassportExpiryDate() == null || form.getDateOfBirth() == null
                || isBlank(form.getNationality()) || isBlank(form.getGender());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    @GetMapping("/compliance/{amlId}/passport/image")
    public ResponseEntity<Resource> passportImage(@PathVariable String amlId) throws IOException {
        PassportDocumentService.StoredPassport stored = passportDocumentService.get(amlId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(stored.displayName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(stored.path()));
    }

    @GetMapping("/compliance/{amlId}/evidence/{kind}")
    public ResponseEntity<Resource> sourceEvidence(@PathVariable String amlId, @PathVariable String kind) {
        SourceEvidenceDocumentService.StoredEvidence stored = sourceEvidenceDocumentService.get(amlId, kind);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(stored.displayName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(stored.path()));
    }

    @PostMapping("/compliance/{amlId}/passport/verify")
    public String verifyPassport(@PathVariable String amlId,
                                 @Valid @ModelAttribute("passportReviewForm") PassportReviewForm form,
                                 BindingResult bindingResult,
                                 Authentication authentication,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("report", complianceService.fetchByAmlId(amlId));
            return "portal/passport-review";
        }
        try {
            complianceService.verifyPassport(amlId, form.getFirstName(), form.getLastName(),
                    form.getPassportFullName(),
                    form.getPassportNumber(), form.getPassportType(),
                    form.getPassportIssuingCountryCode(), form.getPassportExpiryDate(),
                    form.getPassportDateOfIssue(), form.getPassportIssuingAuthority(),
                    form.getDateOfBirth(), form.getNationality(), form.getGender(),
                    form.isIdentityConfirmed(), authentication.getName());
        } catch (ResponseStatusException exception) {
            bindingResult.reject("passport.verification", exception.getReason());
            model.addAttribute("report", complianceService.fetchByAmlId(amlId));
            return "portal/passport-review";
        }
        redirectAttributes.addFlashAttribute("successMessage",
                "Passport verified. Your verified passport details are prefilled in the compliance registration form.");
        return "redirect:/portal/compliance/" + amlId + "/edit?registration=true";
    }

    void validateUserId(Long userId, BindingResult bindingResult) {
        if (userId != null && !userRepository.existsById(userId)) {
            bindingResult.rejectValue("userId", "userId.notFound", "Select an existing user account.");
        }
    }

    void validateRegistrationContactDetails(ComplianceForm form, BindingResult bindingResult) {
        if (form.getFullName() == null || form.getFullName().isBlank()) {
            bindingResult.rejectValue("fullName", "fullName.required", "Enter the customer's full name.");
        }
        if (form.getEmailAddress() == null || form.getEmailAddress().isBlank()) {
            bindingResult.rejectValue("emailAddress", "emailAddress.required", "Enter an email address.");
        }
        if (form.getConfirmEmailAddress() == null || form.getConfirmEmailAddress().isBlank()) {
            bindingResult.rejectValue("confirmEmailAddress", "confirmEmailAddress.required",
                    "Confirm the email address.");
        } else if (form.getEmailAddress() != null && !form.getEmailAddress().isBlank()
                && !form.getEmailAddress().trim().equalsIgnoreCase(form.getConfirmEmailAddress().trim())) {
            bindingResult.rejectValue("confirmEmailAddress", "confirmEmailAddress.mismatch",
                    "Email addresses must match.");
        }
    }

    void validateOnboardingType(ComplianceForm form, BindingResult bindingResult) {
        if (!"INDIVIDUAL".equals(form.getOnboardingType())
                && !"COMPANY".equals(form.getOnboardingType())) {
            bindingResult.rejectValue("onboardingType", "onboardingType.invalid",
                    "Choose individual or company onboarding.");
            return;
        }
    }

    void validatePassportDetails(ComplianceForm form, BindingResult bindingResult) {
        if (form.getPassportFullName() == null || form.getPassportFullName().isBlank()) {
            bindingResult.rejectValue("passportFullName", "passportFullName.required",
                    "Enter the name shown on the passport.");
        }
        if (form.getPassportFirstName() == null || form.getPassportFirstName().isBlank()) {
            bindingResult.rejectValue("passportFirstName", "passportFirstName.required",
                    "Enter the first name shown on the passport.");
        }
        if (form.getPassportLastName() == null || form.getPassportLastName().isBlank()) {
            bindingResult.rejectValue("passportLastName", "passportLastName.required",
                    "Enter the last name shown on the passport.");
        }
        if (form.getPassportNumber() == null || form.getPassportNumber().isBlank()) {
            bindingResult.rejectValue("passportNumber", "passportNumber.required",
                    "Enter the passport number.");
        }
        if (form.getPassportType() == null || form.getPassportType().isBlank()) {
            bindingResult.rejectValue("passportType", "passportType.required",
                    "Enter the passport document type.");
        }
        if (form.getPassportIssuingCountryCode() == null
                || !form.getPassportIssuingCountryCode().matches("(?i)[a-z]{3}")) {
            bindingResult.rejectValue("passportIssuingCountryCode", "passportIssuingCountryCode.invalid",
                    "Enter the three-letter issuing country code shown on the passport.");
        }
        LocalDate passportExpiryDate = null;
        if (form.getPassportExpiry() == null || form.getPassportExpiry().isBlank()) {
            bindingResult.rejectValue("passportExpiry", "passportExpiry.required",
                    "Enter the passport expiry date.");
        } else {
            try {
                passportExpiryDate = LocalDate.parse(form.getPassportExpiry());
                if (!passportExpiryDate.isAfter(LocalDate.now())) {
                    bindingResult.rejectValue("passportExpiry", "passportExpiry.expired",
                            "Passport expiry date must be in the future.");
                }
            } catch (DateTimeParseException exception) {
                bindingResult.rejectValue("passportExpiry", "passportExpiry.invalid",
                        "Enter a valid passport expiry date.");
            }
        }
        if (form.getPassportDateOfIssue() != null && !form.getPassportDateOfIssue().isBlank()) {
            try {
                LocalDate issueDate = LocalDate.parse(form.getPassportDateOfIssue());
                if (issueDate.isAfter(LocalDate.now())) {
                    bindingResult.rejectValue("passportDateOfIssue", "passportDateOfIssue.future",
                            "Passport issue date cannot be in the future.");
                } else if (passportExpiryDate != null && !issueDate.isBefore(passportExpiryDate)) {
                    bindingResult.rejectValue("passportDateOfIssue", "passportDateOfIssue.afterExpiry",
                            "Passport issue date must be before the expiry date.");
                }
            } catch (DateTimeParseException exception) {
                bindingResult.rejectValue("passportDateOfIssue", "passportDateOfIssue.invalid",
                        "Enter a valid passport issue date.");
            }
        }
    }

    void validateKycDates(ComplianceForm form, BindingResult bindingResult) {
        if (form.getDateOfBirth() != null && !form.getDateOfBirth().isBlank()) {
            try {
                if (LocalDate.parse(form.getDateOfBirth()).isAfter(LocalDate.now())) {
                    bindingResult.rejectValue("dateOfBirth", "dateOfBirth.future",
                            "Date of birth cannot be in the future.");
                }
            } catch (DateTimeParseException exception) {
                bindingResult.rejectValue("dateOfBirth", "dateOfBirth.invalid",
                        "Enter a valid date of birth.");
            }
        }
        if (form.getNationalIdExpiry() != null && !form.getNationalIdExpiry().isBlank()) {
            try {
                if (!LocalDate.parse(form.getNationalIdExpiry()).isAfter(LocalDate.now())) {
                    bindingResult.rejectValue("nationalIdExpiry", "nationalIdExpiry.expired",
                            "National ID expiry date must be in the future.");
                }
            } catch (DateTimeParseException exception) {
                bindingResult.rejectValue("nationalIdExpiry", "nationalIdExpiry.invalid",
                        "Enter a valid national ID expiry date.");
            }
        }
    }

    private void addFormOptions(Model model) {
        addFormOptions(model, true);
    }

    void addPublicFormOptions(Model model) {
        addFormOptions(model, false);
    }

    private void addFormOptions(Model model, boolean includeUsers) {
        List<User> users = includeUsers ? userRepository.findAll().stream()
                .filter(user -> user.getId() != null && user.getEmail() != null)
                .sorted((left, right) -> left.getEmail().compareToIgnoreCase(right.getEmail()))
                .toList() : List.of();
        List<PostalCountry> postalCountries = java.util.Arrays.stream(Locale.getISOCountries())
                .map(code -> new PostalCountry(code,
                        new Locale("", code).getDisplayCountry(Locale.ENGLISH)))
                .sorted((left, right) -> left.name().compareToIgnoreCase(right.name()))
                .toList();
        model.addAttribute("users", users);
        model.addAttribute("processStatuses", PROCESS_STATUSES);
        model.addAttribute("postalCountries", postalCountries);
        model.addAttribute("postalCountryNames", postalCountries.stream().map(PostalCountry::name).toList());
    }

    CustomerDto toCustomerDto(ComplianceForm form, String actor) {
        CustomerDto dto = new CustomerDto();
        dto.setUserId(form.getUserId());
        dto.setFullName(trimToNull(form.getFullName()));
        dto.setCategory(form.getOnboardingType());
        dto.setCompanyName("COMPANY".equals(form.getOnboardingType())
                ? trimToNull(form.getCompanyName()) : null);
        dto.setCountry(trimToNull(form.getCountry()) != null
                ? trimToNull(form.getCountry()) : trimToNull(form.getCountryOfResidence()));
        dto.setPositionInCompany(trimToNull(form.getPositionInCompany()));
        dto.setExternalRefNumber(trimToNull(form.getExternalRefNumber()));
        dto.setStatus(form.getStatus());
        dto.setDeliveryChannel(trimToNull(form.getDeliveryChannel()));
        dto.setTransactionType(trimToNull(form.getTransactionType()));
        dto.setOtherNationalities("No".equalsIgnoreCase(form.getHasOtherNationalities())
                ? null : trimToNull(form.getOtherNationalities()));
        dto.setLastReviewDate(form.getLastReviewDate());
        dto.setComment(trimToNull(form.getComment()));
        dto.setPassportFirstName(trimToNull(form.getPassportFirstName()));
        dto.setPassportLastName(trimToNull(form.getPassportLastName()));
        dto.setPassportDateOfIssue(parseDate(form.getPassportDateOfIssue()));
        dto.setPassportIssuingAuthority(trimToNull(form.getPassportIssuingAuthority()));
        dto.setOnboardedByUser(actor);
        dto.setKycData(KycDataDto.builder()
                .customerInformation(KycDataDto.CustomerInformation.builder()
                        .fullName(trimToNull(form.getFullName()))
                        .fatherName(trimToNull(form.getFatherName()))
                        .gender(trimToNull(form.getGender()))
                        .individualType(trimToNull(form.getIndividualType()))
                        .dateOfBirth(form.getDateOfBirth())
                        .nationality(form.getNationality())
                        .countryOfResidence(form.getCountryOfResidence())
                        .residentStatus(form.getResidentStatus())
                        .nationalIdNumber(form.getNationalIdNumber())
                        .nationalIdExpiry(form.getNationalIdExpiry())
                        .passportNumber(trimToNull(form.getPassportNumber()))
                        .passportType(trimToNull(form.getPassportType()))
                        .passportIssuingCountryCode(trimToNull(form.getPassportIssuingCountryCode()))
                        .passportExpiry(trimToNull(form.getPassportExpiry()))
                        .otherNationalities("No".equalsIgnoreCase(form.getHasOtherNationalities())
                                ? null : trimToNull(form.getOtherNationalities()))
                        .build())
                .profileInformation(KycDataDto.ProfileInformation.builder()
                        .workType(form.getWorkType())
                        .industry(form.getIndustry())
                        .products(form.getProducts())
                        .productOffered(form.getProductOffered())
                        .deliveryChannel(form.getDeliveryChannel())
                        .build())
                .contactInformation(KycDataDto.ContactInformation.builder()
                        .emailAddress(trimToNull(form.getEmailAddress()))
                        .phoneCountryCode(trimToNull(form.getPhoneCountryCode()))
                        .contactNumber(trimToNull(form.getContactNumber()))
                        .address(trimToNull(form.getAddress()))
                        .townCity(trimToNull(form.getTownCity()))
                        .countyState(trimToNull(form.getCountyState()))
                        .postalCode(trimToNull(form.getPostalCode()))
                        .build())
                .pepDeclaration(KycDataDto.PepDeclaration.builder()
                        .currentlyHoldPublicPosition(form.getCurrentlyHoldPublicPosition())
                        .heldPublicPositionLast12Months(form.getHeldPublicPositionLast12Months())
                        .everHeldPublicPosition(form.getEverHeldPublicPosition())
                        .diplomaticImmunity(form.getDiplomaticImmunity())
                        .relativeHeldPublicPositionLast12Months(form.getRelativeHeldPublicPositionLast12Months())
                        .closeAssociateHeldPublicPositionLast12Months(form.getCloseAssociateHeldPublicPositionLast12Months())
                        .courtConviction(form.getCourtConviction())
                        .details(trimToNull(form.getPepDetails()))
                        .build())
                .sourceOfWealthFunds(KycDataDto.SourceOfWealthFunds.builder()
                        .sourceOfWealth(joinSelections(form.getSourceOfWealthSelections()))
                        .sourceOfWealthOther(trimToNull(form.getSourceOfWealthOther()))
                        .sourceOfFunds(joinSelections(form.getSourceOfFundsSelections()))
                        .sourceOfFundsOther(trimToNull(form.getSourceOfFundsOther()))
                        .build())
                .build());
        return dto;
    }

    private ComplianceForm toForm(CustomerDto report) {
        ComplianceForm form = new ComplianceForm();
        form.setUserId(report.getUserId());
        form.setFullName(report.getFullName());
        form.setCompanyName(report.getCompanyName());
        form.setOnboardingType("COMPANY".equals(report.getCategory())
                || (report.getCategory() == null && report.getCompanyName() != null && !report.getCompanyName().isBlank())
                ? "COMPANY" : "INDIVIDUAL");
        form.setCountry(report.getCountry());
        form.setPositionInCompany(report.getPositionInCompany());
        form.setExternalRefNumber(report.getExternalRefNumber());
        form.setStatus(report.getStatus() == null ? "IN_REVIEW" : report.getStatus());
        form.setDeliveryChannel(report.getDeliveryChannel());
        form.setTransactionType(report.getTransactionType());
        form.setOtherNationalities(report.getOtherNationalities());
        form.setLastReviewDate(report.getLastReviewDate());
        form.setComment(report.getComment());
        form.setPassportFullName(report.getPassportFullName());
        form.setPassportFirstName(report.getPassportFirstName());
        form.setPassportLastName(report.getPassportLastName());
        form.setPassportNumber(report.getVerifiedPassportNumber());
        form.setPassportType(report.getPassportType());
        form.setPassportIssuingCountryCode(report.getPassportIssuingCountryCode());
        form.setPassportExpiry(report.getPassportExpiryDate() == null
                ? null : report.getPassportExpiryDate().toString());
        form.setPassportDateOfIssue(report.getPassportDateOfIssue() == null
                ? null : report.getPassportDateOfIssue().toString());
        form.setPassportIssuingAuthority(report.getPassportIssuingAuthority());
        if (report.getKycData() != null) {
            KycDataDto kyc = report.getKycData();
            if (kyc.getCustomerInformation() != null) {
                var customer = kyc.getCustomerInformation();
                form.setFatherName(customer.getFatherName());
                form.setGender(customer.getGender());
                form.setIndividualType(customer.getIndividualType());
                form.setDateOfBirth(customer.getDateOfBirth());
                form.setNationality(customer.getNationality());
                form.setCountryOfResidence(customer.getCountryOfResidence());
                form.setResidentStatus(customer.getResidentStatus());
                if (form.getCountry() == null) {
                    form.setCountry(customer.getCountryOfResidence());
                }
                form.setNationalIdNumber(customer.getNationalIdNumber());
                form.setNationalIdExpiry(customer.getNationalIdExpiry());
                form.setOtherNationalities(customer.getOtherNationalities());
                form.setHasOtherNationalities(customer.getOtherNationalities() == null
                        || customer.getOtherNationalities().isBlank() ? "No" : "Yes");
                if (form.getPassportNumber() == null) {
                    form.setPassportNumber(customer.getPassportNumber());
                }
                if (form.getPassportType() == null) {
                    form.setPassportType(customer.getPassportType());
                }
                if (form.getPassportIssuingCountryCode() == null) {
                    form.setPassportIssuingCountryCode(customer.getPassportIssuingCountryCode());
                }
                if (form.getPassportExpiry() == null) {
                    form.setPassportExpiry(customer.getPassportExpiry());
                }
            }
            if (kyc.getProfileInformation() != null) {
                var profile = kyc.getProfileInformation();
                form.setWorkType(profile.getWorkType());
                form.setIndustry(profile.getIndustry());
                form.setProducts(profile.getProducts());
                form.setProductOffered(profile.getProductOffered());
            }
            if (kyc.getContactInformation() != null) {
                var contact = kyc.getContactInformation();
                form.setEmailAddress(contact.getEmailAddress());
                form.setConfirmEmailAddress(contact.getEmailAddress());
                form.setPhoneCountryCode(contact.getPhoneCountryCode());
                form.setContactNumber(contact.getContactNumber());
                form.setAddress(contact.getAddress());
                form.setTownCity(contact.getTownCity());
                form.setCountyState(contact.getCountyState());
                form.setPostalCode(contact.getPostalCode());
            }
            if (kyc.getPepDeclaration() != null) {
                var pep = kyc.getPepDeclaration();
                form.setCurrentlyHoldPublicPosition(pep.getCurrentlyHoldPublicPosition());
                form.setHeldPublicPositionLast12Months(pep.getHeldPublicPositionLast12Months());
                form.setEverHeldPublicPosition(pep.getEverHeldPublicPosition());
                form.setDiplomaticImmunity(pep.getDiplomaticImmunity());
                form.setRelativeHeldPublicPositionLast12Months(pep.getRelativeHeldPublicPositionLast12Months());
                form.setCloseAssociateHeldPublicPositionLast12Months(pep.getCloseAssociateHeldPublicPositionLast12Months());
                form.setCourtConviction(pep.getCourtConviction());
                form.setPepDetails(pep.getDetails());
            }
            if (kyc.getSourceOfWealthFunds() != null) {
                var sources = kyc.getSourceOfWealthFunds();
                form.setSourceOfWealthSelections(splitSelections(sources.getSourceOfWealth()));
                form.setSourceOfWealthOther(sources.getSourceOfWealthOther());
                form.setSourceOfFundsSelections(splitSelections(sources.getSourceOfFunds()));
                form.setSourceOfFundsOther(sources.getSourceOfFundsOther());
            }
        }
        return form;
    }

    void validateSourceEvidence(MultipartFile wealthEvidence, MultipartFile fundsEvidence,
                                BindingResult bindingResult) {
        try {
            sourceEvidenceDocumentService.validateUploads(wealthEvidence, fundsEvidence);
        } catch (ResponseStatusException exception) {
            bindingResult.reject("sourceEvidence.invalid", exception.getReason());
        }
    }

    private void addEvidenceDocumentNames(CustomerDto report, Model model) {
        model.addAttribute("sourceOfWealthEvidenceName", report.getSourceOfWealthEvidenceName());
        model.addAttribute("sourceOfFundsEvidenceName", report.getSourceOfFundsEvidenceName());
    }

    private void addNewComplianceFormModel(Model model) {
        addFormOptions(model);
        model.addAttribute("pageTitle", "Customer onboarding");
        model.addAttribute("formAction", "/portal/compliance");
        model.addAttribute("editing", false);
        model.addAttribute("passportVerified", false);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String joinSelections(List<String> selections) {
        return selections == null ? null : selections.stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private List<String> splitSelections(String selections) {
        return selections == null || selections.isBlank() ? List.of()
                : java.util.Arrays.stream(selections.split(",\\s*")).toList();
    }

    private String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\p{L}\\p{N}]", "")
                .toLowerCase(java.util.Locale.ROOT);
    }

    public record PostalCountry(String isoCode, String name) {
    }
}
