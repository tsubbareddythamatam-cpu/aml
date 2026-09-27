package org.aml.service;

import org.aml.dto.*;
import org.aml.exception.ResourceNotFoundException;
import org.aml.model.*;
import org.aml.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.security.SecureRandom;
import java.util.stream.Collectors;
import java.util.function.Supplier;

@Service
@CacheConfig(cacheNames = "complianceReports")
public class ComplianceService {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceService.class);
    private static final SecureRandom AML_ID_RANDOM = new SecureRandom();
    private static final int AML_ID_GENERATION_ATTEMPTS = 100;

    private final CustomerRepository customerRepository;

    /**
     * Creates the service with the repository used to persist compliance profiles.
     *
     * @param customerRepository repository for compliance records
     */
    public ComplianceService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /**
     * Creates and persists a compliance profile, including supplied KYC data and its creation audit entry.
     *
     * @param dto compliance profile data to persist
     * @return the saved compliance profile with related data
     */
    @Transactional
    @CacheEvict(allEntries = true)
    public CustomerDto processReport(CustomerDto dto) {
        logger.info("Creating compliance profile for userId={}", dto.getUserId());
        Customer entity = convertToEntity(dto);
        Customer saved = customerRepository.save(entity);
        logger.info("Created compliance profile for userId={}", saved.getUserId());
        return convertToDto(saved);
    }

    /**
     * Applies non-null profile updates and records the names of changed fields in the audit history.
     *
     * @param id AML identifier of the profile to update
     * @param dto fields and related data to update
     * @return the updated compliance profile
     * @throws ResourceNotFoundException if no profile has the supplied identifier
     */
    @Transactional
    @CacheEvict(allEntries = true)
    public CustomerDto updateReport(String id, CustomerDto dto) {
        logger.debug("Updating compliance profile");
        Customer existingCustomer = customerRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Compliance profile update failed because the profile was not found");
                    return new ResourceNotFoundException(
                            "Cannot update profile. Compliance record matching reference ID: " + id + " not found.");
                });

        // 🆕 Dynamic audit string collection to compile historical value shifts
        List<String> auditChanges = new ArrayList<>();

        if (dto.getUserId() != null && !Objects.equals(existingCustomer.getUserId(), dto.getUserId())) {
            auditChanges.add("userId");
            existingCustomer.setUserId(dto.getUserId());
        }
        if (dto.getExternalRefNumber() != null && !Objects.equals(existingCustomer.getExternalRefNumber(), nullIfEmpty(dto.getExternalRefNumber()))) {
            auditChanges.add("externalRefNumber");
            existingCustomer.setExternalRefNumber(nullIfEmpty(dto.getExternalRefNumber()));
        }
        if (dto.getFullName() != null && !Objects.equals(existingCustomer.getFullName(), nullIfEmpty(dto.getFullName()))) {
            auditChanges.add("fullName");
            existingCustomer.setFullName(nullIfEmpty(dto.getFullName()));
        }
        if (dto.getOtherNationalities() != null && !Objects.equals(existingCustomer.getOtherNationalities(), nullIfEmpty(dto.getOtherNationalities()))) {
            auditChanges.add("otherNationalities");
            existingCustomer.setOtherNationalities(nullIfEmpty(dto.getOtherNationalities()));
        }
        if (dto.getDeliveryChannel() != null && !Objects.equals(existingCustomer.getDeliveryChannel(), nullIfEmpty(dto.getDeliveryChannel()))) {
            auditChanges.add("deliveryChannel");
            existingCustomer.setDeliveryChannel(nullIfEmpty(dto.getDeliveryChannel()));
        }
        if (dto.getTransactionType() != null && !Objects.equals(existingCustomer.getTransactionType(), nullIfEmpty(dto.getTransactionType()))) {
            auditChanges.add("transactionType");
            existingCustomer.setTransactionType(nullIfEmpty(dto.getTransactionType()));
        }
        if (dto.getNameScreeningHit() != null && !Objects.equals(existingCustomer.getNameScreeningHit(), dto.getNameScreeningHit())) {
            auditChanges.add("nameScreeningHit");
            existingCustomer.setNameScreeningHit(dto.getNameScreeningHit());
        }
        if (dto.getDocumentsVerificationHit() != null && !Objects.equals(existingCustomer.getDocumentsVerificationHit(), dto.getDocumentsVerificationHit())) {
            auditChanges.add("documentsVerificationHit");
            existingCustomer.setDocumentsVerificationHit(dto.getDocumentsVerificationHit());
        }
        if (dto.getRiskRatingHit() != null && !Objects.equals(existingCustomer.getRiskRatingHit(), dto.getRiskRatingHit())) {
            auditChanges.add("riskRatingHit");
            existingCustomer.setRiskRatingHit(dto.getRiskRatingHit());
        }
        if (dto.getStatus() != null && !Objects.equals(existingCustomer.getStatus(), nullIfEmpty(dto.getStatus()))) {
            auditChanges.add("status");
            existingCustomer.setStatus(nullIfEmpty(dto.getStatus()));
        }
        if (dto.getOnboardedByCompany() != null
                && !Objects.equals(existingCustomer.getOnboardedByCompany(), nullIfEmpty(dto.getOnboardedByCompany()))) {
            auditChanges.add("onboardedByCompany");
            existingCustomer.setOnboardedByCompany(nullIfEmpty(dto.getOnboardedByCompany()));
        }
        if (dto.getComment() != null && !Objects.equals(existingCustomer.getComment(), nullIfEmpty(dto.getComment()))) {
            auditChanges.add("comment");
            existingCustomer.setComment(nullIfEmpty(dto.getComment()));
        }
        if (dto.getRegisteredAt() != null && !Objects.equals(existingCustomer.getRegisteredAt(), dto.getRegisteredAt())) {
            auditChanges.add("registeredAt");
            existingCustomer.setRegisteredAt(dto.getRegisteredAt());
        }
        if (dto.getLastReviewDate() != null
                && !Objects.equals(existingCustomer.getLastReviewDate(), dto.getLastReviewDate())) {
            auditChanges.add("lastReviewDate");
            existingCustomer.setLastReviewDate(dto.getLastReviewDate());
        }
        if (dto.getReportGeneratedOn() != null
                && !Objects.equals(existingCustomer.getReportGeneratedOn(), dto.getReportGeneratedOn())) {
            auditChanges.add("reportGeneratedOn");
            existingCustomer.setReportGeneratedOn(dto.getReportGeneratedOn());
        }
        if (dto.getCompanyName() != null && !Objects.equals(existingCustomer.getCompanyName(), nullIfEmpty(dto.getCompanyName()))) {
            auditChanges.add("companyName");
            existingCustomer.setCompanyName(nullIfEmpty(dto.getCompanyName()));
        }
        if (dto.getPositionInCompany() != null && !Objects.equals(existingCustomer.getPositionInCompany(), nullIfEmpty(dto.getPositionInCompany()))) {
            auditChanges.add("positionInCompany");
            existingCustomer.setPositionInCompany(nullIfEmpty(dto.getPositionInCompany()));
        }
        if (dto.getKycData() != null) {
            updateKycData(dto.getKycData(), existingCustomer, auditChanges);
        }

        if (dto.getRiskRatingsAndOverrides() != null) {
            RiskRatings riskRatings = existingCustomer.getRiskRatingsAndOverrides();
            if (riskRatings == null) {
                riskRatings = new RiskRatings();
                riskRatings.setCustomer(existingCustomer);
                existingCustomer.setRiskRatingsAndOverrides(riskRatings);
            }
            applyFieldUpdates("riskRatingsAndOverrides", riskRatings, dto.getRiskRatingsAndOverrides(), auditChanges);
        }

        // Replace screening hits only when their values or order differ.
        if (dto.getScreeningHits() != null && screeningHitsChanged(existingCustomer, dto.getScreeningHits())) {
            existingCustomer.getScreeningHits().clear();
            for (ScreeningHitDto hDto : dto.getScreeningHits()) {
                ScreeningHit hEntity = new ScreeningHit();
                hEntity.setHitName(nullIfEmpty(hDto.getHitName()));
                hEntity.setCategory(nullIfEmpty(hDto.getCategory()));
                hEntity.setSource(nullIfEmpty(hDto.getSource()));
                hEntity.setScore(hDto.getScore());
                hEntity.setHitDetermination(nullIfEmpty(hDto.getHitDetermination()));
                hEntity.setComments(nullIfEmpty(hDto.getComments()));
                hEntity.setCustomer(existingCustomer);
                existingCustomer.getScreeningHits().add(hEntity);
            }
            auditChanges.add("screeningHits");
        }

        if (!auditChanges.isEmpty()) {
            List<String> changedFields = auditChanges.stream().distinct().collect(Collectors.toList());
            addAuditLog(existingCustomer, dto.getOnboardedByUser(),
                    "Updated fields: " + changedFields.stream().collect(Collectors.joining(", ")));
            logger.info("Updated compliance profile for userId={} with fields={}",
                    existingCustomer.getUserId(), changedFields);
        } else {
            logger.debug("Compliance profile update contained no changed fields");
        }

        Customer updatedEntity = customerRepository.save(existingCustomer);
        return convertToDto(updatedEntity);
    }

    /**
     * Determines whether a requested screening-hit list differs from the stored list.
     *
     * @param customer profile containing the stored hits
     * @param requestedHits screening hits supplied by the request
     * @return {@code true} when the list differs in size, order, or field values
     */
    private boolean screeningHitsChanged(Customer customer, List<ScreeningHitDto> requestedHits) {
        List<ScreeningHitDto> currentHits = customer.getScreeningHits().stream()
                .map(hit -> ScreeningHitDto.builder()
                        .hitName(hit.getHitName())
                        .category(hit.getCategory())
                        .source(hit.getSource())
                        .score(hit.getScore())
                        .hitDetermination(hit.getHitDetermination())
                        .comments(hit.getComments())
                        .build())
                .collect(Collectors.toList());
        return !currentHits.equals(requestedHits);
    }

    /**
     * Retrieves all compliance profiles with their KYC, risk, screening, and audit data.
     *
     * @return all stored compliance profiles
     */
    @Cacheable(key = "'all-reports'")
    @Transactional(readOnly = true)
    public List<CustomerDto> fetchAll() {
        List<CustomerDto> reports = customerRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        logger.debug("Retrieved {} compliance profiles", reports.size());
        return reports;
    }
    /**
     * Retrieves a compliance profile by its AML identifier.
     *
     * @param amlId AML identifier to search for
     * @return the matching profile with all related data
     * @throws ResourceNotFoundException if no profile has the supplied identifier
     */
    @Cacheable(key = "'aml:' + #amlId")
    @Transactional(readOnly = true)
    public CustomerDto fetchByAmlId(String amlId) {
        Customer entity = customerRepository.findByAmlId(amlId)
                .orElseThrow(() -> {
                    logger.warn("Compliance profile lookup by AML identifier failed");
                    return new ResourceNotFoundException(
                            "Compliance record matching tracking identifier amlId: " + amlId + " not found.");
                });
        logger.debug("Retrieved compliance profile by AML identifier");
        return convertToDto(entity);
    }

    /**
     * Retrieves a compliance profile by its associated user identifier.
     *
     * @param userId user identifier to search for
     * @return the matching profile with all related data
     * @throws ResourceNotFoundException if no profile is associated with the user
     */
    @Cacheable(key = "'user:' + #userId")
    @Transactional(readOnly = true)
    public CustomerDto fetchByUserId(Long userId) {
        Customer entity = customerRepository.findByUserId(userId)
                .orElseThrow(() -> {
                    logger.warn("No compliance profile found for userId={}", userId);
                    return new ResourceNotFoundException(
                            "Compliance record matching technical ID: " + userId + " not found.");
                });
        logger.debug("Retrieved compliance profile for userId={}", userId);
        return convertToDto(entity);
    }

    /**
     * Deletes all compliance profiles and their cascading related records.
     */
    @CacheEvict(allEntries = true)
    @Transactional
    public void resetComplianceDatabase() {
        logger.warn("Deleting all compliance profiles and related records");
        customerRepository.deleteAll();
        logger.info("All compliance profiles and related records were deleted");
    }

    /**
     * Deletes a compliance profile and its cascading related records by AML identifier.
     *
     * @param amlId AML identifier of the profile to delete
     * @throws ResourceNotFoundException if no profile has the supplied identifier
     */
    @CacheEvict(allEntries = true)
    @Transactional
    public void deleteByAmlId(String amlId) {
        Customer customer = customerRepository.findById(amlId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Compliance record matching AML ID: " + amlId + " not found."));
        customerRepository.delete(customer);
        logger.info("Deleted compliance profile by AML ID");
    }

    /**
     * Maps a compliance request DTO and its nested records to a new persistence entity.
     *
     * @param dto request data to convert
     * @return a new customer entity populated from the request
     */
    private Customer convertToEntity(CustomerDto dto) {
        Customer entity = new Customer();
        entity.setAmlId(generateAmlId());
        entity.setUserId(dto.getUserId());
        entity.setExternalRefNumber(nullIfEmpty(dto.getExternalRefNumber()));
        entity.setFullName(nullIfEmpty(dto.getFullName()));
        entity.setOtherNationalities(nullIfEmpty(dto.getOtherNationalities()));
        entity.setDeliveryChannel(nullIfEmpty(dto.getDeliveryChannel()));
        entity.setTransactionType(nullIfEmpty(dto.getTransactionType()));
        entity.setNameScreeningHit(dto.getNameScreeningHit());
        entity.setDocumentsVerificationHit(dto.getDocumentsVerificationHit());
        entity.setRiskRatingHit(dto.getRiskRatingHit());
        entity.setStatus(nullIfEmpty(dto.getStatus()));
        entity.setOnboardedByUser(nullIfEmpty(dto.getOnboardedByUser()));
        entity.setOnboardedByCompany(nullIfEmpty(dto.getOnboardedByCompany()));
        entity.setComment(nullIfEmpty(dto.getComment()));
        entity.setRegisteredAt(dto.getRegisteredAt());
        entity.setLastReviewDate(dto.getLastReviewDate());
        entity.setReportGeneratedOn(dto.getReportGeneratedOn());
        entity.setCompanyName(nullIfEmpty(dto.getCompanyName()));
        entity.setPositionInCompany(nullIfEmpty(dto.getPositionInCompany()));
        mapKycToEntity(dto.getKycData(), entity);
        if (dto.getRiskRatingsAndOverrides() != null) {
            RiskRatings rEntity = new RiskRatings();
            RiskRatingsDto rDto = dto.getRiskRatingsAndOverrides();
            rEntity.setTotalMatches(rDto.getTotalMatches());
            rEntity.setResolvedMatches(rDto.getResolvedMatches());
            rEntity.setGenuineMatches(rDto.getGenuineMatches());
            rEntity.setNotGenuineMatches(rDto.getNotGenuineMatches());
            rEntity.setUnresolvedMatches(rDto.getUnresolvedMatches());
            rEntity.setCountryResidenceScore(rDto.getCountryResidenceScore());
            rEntity.setCountryResidenceLevel(nullIfEmpty(rDto.getCountryResidenceLevel()));
            rEntity.setDeliveryChannelScore(rDto.getDeliveryChannelScore());
            rEntity.setDeliveryChannelLevel(nullIfEmpty(rDto.getDeliveryChannelLevel()));
            rEntity.setIndustryScore(rDto.getIndustryScore());
            rEntity.setIndustryLevel(nullIfEmpty(rDto.getIndustryLevel()));
            rEntity.setNationalityScore(rDto.getNationalityScore());
            rEntity.setNationalityLevel(nullIfEmpty(rDto.getNationalityLevel()));
            rEntity.setProductScore(rDto.getProductScore());
            rEntity.setProductLevel(nullIfEmpty(rDto.getProductLevel()));
            rEntity.setAntiSpoofingScore(rDto.getAntiSpoofingScore());
            rEntity.setAntiSpoofingLevel(nullIfEmpty(rDto.getAntiSpoofingLevel()));
            rEntity.setBaseRatingScore(rDto.getBaseRatingScore());
            rEntity.setBaseRatingLevel(nullIfEmpty(rDto.getBaseRatingLevel()));
            rEntity.setStrFiledOverride(nullIfEmpty(rDto.getStrFiledOverride()));
            rEntity.setNonResidentOverride(nullIfEmpty(rDto.getNonResidentOverride()));
            rEntity.setSanctionedResidenceOverride(nullIfEmpty(rDto.getSanctionedResidenceOverride()));
            rEntity.setSanctionedNationalityOverride(nullIfEmpty(rDto.getSanctionedNationalityOverride()));
            rEntity.setSanctionedPhoneCodeOverride(nullIfEmpty(rDto.getSanctionedPhoneCodeOverride()));
            rEntity.setSanctionHitOverride(nullIfEmpty(rDto.getSanctionHitOverride()));
            rEntity.setPepOverride(nullIfEmpty(rDto.getPepOverride()));
            rEntity.setSpecialInterestOverride(nullIfEmpty(rDto.getSpecialInterestOverride()));
            rEntity.setDocVerificationOverride(nullIfEmpty(rDto.getDocVerificationOverride()));
            rEntity.setAdverseMediaOverride(nullIfEmpty(rDto.getAdverseMediaOverride()));
            rEntity.setTransactionOverride(nullIfEmpty(rDto.getTransactionOverride()));
            rEntity.setOverallRatingCalculated(nullIfEmpty(rDto.getOverallRatingCalculated()));
            rEntity.setOverallRatingFinal(nullIfEmpty(rDto.getOverallRatingFinal()));
            rEntity.setCustomer(entity);
            entity.setRiskRatingsAndOverrides(rEntity);
        }
        if (dto.getScreeningHits() != null) {
            for (ScreeningHitDto hDto : dto.getScreeningHits()) {
                ScreeningHit hEntity = new ScreeningHit();
                hEntity.setHitName(nullIfEmpty(hDto.getHitName()));
                hEntity.setCategory(nullIfEmpty(hDto.getCategory()));
                hEntity.setSource(nullIfEmpty(hDto.getSource()));
                hEntity.setScore(hDto.getScore());
                hEntity.setHitDetermination(nullIfEmpty(hDto.getHitDetermination()));
                hEntity.setComments(nullIfEmpty(hDto.getComments()));
                hEntity.setCustomer(entity);
                entity.getScreeningHits().add(hEntity);
            }
        }
        if (dto.getAuditLogs() != null) {
            for (AuditLogDto aDto : dto.getAuditLogs()) {
                AuditLog aEntity = new AuditLog();
                aEntity.setActionDate(aDto.getActionDate());
                aEntity.setActionedBy(nullIfEmpty(aDto.getActionedBy()));
                aEntity.setActionTaken(nullIfEmpty(aDto.getActionTaken()));
                aEntity.setCustomer(entity);
                entity.getAuditLogs().add(aEntity);
            }
        }
        addAuditLog(entity, dto.getOnboardedByUser(), "Registered new compliance profile.");
        return entity;
    }

    /**
     * Generates a unique six-digit AML identifier for a newly created compliance profile.
     *
     * @return an unused six-digit numeric AML identifier
     */
    private String generateAmlId() {
        for (int attempt = 0; attempt < AML_ID_GENERATION_ATTEMPTS; attempt++) {
            String candidate = Integer.toString(100_000 + AML_ID_RANDOM.nextInt(900_000));
            if (!customerRepository.existsById(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique six-digit AML ID after multiple attempts.");
    }

    /**
     * Adds an audit record to a profile using the current date and a default actor when needed.
     *
     * @param customer profile to attach the audit record to
     * @param actionedBy actor responsible for the action
     * @param actionTaken description of the action
     */
    private void addAuditLog(Customer customer, String actionedBy, String actionTaken) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActionDate(LocalDate.now());
        auditLog.setActionedBy(nullIfEmpty(actionedBy) != null ? nullIfEmpty(actionedBy) : "Compliance System");
        auditLog.setActionTaken(actionTaken);
        auditLog.setCustomer(customer);
        customer.getAuditLogs().add(auditLog);
    }

    /**
     * Applies supplied KYC sections and adds changed property paths to the audit change list.
     *
     * @param dto KYC sections to update
     * @param customer profile containing the KYC sections
     * @param auditChanges collection receiving changed property paths
     */
    private void updateKycData(KycDataDto dto, Customer customer, List<String> auditChanges) {
        if (dto.getCustomerInformation() != null) {
            customer.setCustomerInformation(updateSection("kycData.customerInformation",
                    customer.getCustomerInformation(), dto.getCustomerInformation(),
                    CustomerInformation::new, auditChanges));
        }
        if (dto.getProfileInformation() != null) {
            customer.setProfileInformation(updateSection("kycData.profileInformation",
                    customer.getProfileInformation(), dto.getProfileInformation(),
                    ProfileInformation::new, auditChanges));
        }
        if (dto.getContactInformation() != null) {
            customer.setContactInformation(updateSection("kycData.contactInformation",
                    customer.getContactInformation(), dto.getContactInformation(),
                    ContactInformation::new, auditChanges));
        }
        if (dto.getPepDeclaration() != null) {
            customer.setPepDeclaration(updateSection("kycData.pepDeclaration",
                    customer.getPepDeclaration(), dto.getPepDeclaration(),
                    PepDeclaration::new, auditChanges));
        }
        if (dto.getSourceOfWealthFunds() != null) {
            customer.setSourceOfWealthFunds(updateSection("kycData.sourceOfWealthFunds",
                    customer.getSourceOfWealthFunds(), dto.getSourceOfWealthFunds(),
                    SourceOfWealthFunds::new, auditChanges));
        }
        if (dto.getInvestmentRange() != null) {
            customer.setInvestmentRange(updateSection("kycData.investmentRange",
                    customer.getInvestmentRange(), dto.getInvestmentRange(),
                    InvestmentRange::new, auditChanges));
        }
        if (dto.getOngoingDueDiligence() != null) {
            customer.setOngoingDueDiligence(updateSection("kycData.ongoingDueDiligence",
                    customer.getOngoingDueDiligence(), dto.getOngoingDueDiligence(),
                    OngoingDueDiligence::new, auditChanges));
        }
        if (dto.getSignaturePanel() != null) {
            customer.setSignaturePanel(updateSection("kycData.signaturePanel",
                    customer.getSignaturePanel(), dto.getSignaturePanel(),
                    SignaturePanel::new, auditChanges));
        }
    }

    /**
     * Updates non-null values in one KYC section while keeping unspecified existing values.
     *
     * @param prefix property path prefix used in the audit log
     * @param existing existing KYC section, or {@code null} if it has not been created
     * @param updates DTO containing section values to apply
     * @param factory factory for creating a missing section
     * @param auditChanges collection receiving changed property paths
     * @param <T> persistence entity type for the KYC section
     * @return the updated or newly created section
     */
    private <T> T updateSection(String prefix, T existing, Object updates,
                                Supplier<T> factory, List<String> auditChanges) {
        T target = existing != null ? existing : factory.get();
        applyFieldUpdates(prefix, target, updates, auditChanges);
        return target;
    }

    /**
     * Copies non-null bean properties from an update DTO and tracks names whose values changed.
     *
     * @param prefix property path prefix used in the audit log
     * @param target persistence object receiving updated values
     * @param updates DTO providing values to apply
     * @param auditChanges collection receiving changed property paths
     * @throws IllegalStateException if the bean properties cannot be inspected or invoked
     */
    private void applyFieldUpdates(String prefix, Object target, Object updates,
                                   List<String> auditChanges) {
        try {
            Map<String, PropertyDescriptor> targetProperties = new HashMap<>();
            for (PropertyDescriptor property : Introspector.getBeanInfo(target.getClass(), Object.class).getPropertyDescriptors()) {
                targetProperties.put(property.getName(), property);
            }
            for (PropertyDescriptor updateProperty : Introspector.getBeanInfo(updates.getClass(), Object.class).getPropertyDescriptors()) {
                PropertyDescriptor targetProperty = targetProperties.get(updateProperty.getName());
                if (targetProperty == null || updateProperty.getReadMethod() == null || targetProperty.getWriteMethod() == null) {
                    continue;
                }
                Object newValue = updateProperty.getReadMethod().invoke(updates);
                if (newValue == null) {
                    continue;
                }
                Object currentValue = targetProperty.getReadMethod().invoke(target);
                if (!Objects.equals(currentValue, newValue)) {
                    targetProperty.getWriteMethod().invoke(target, newValue);
                    auditChanges.add(prefix + "." + updateProperty.getName());
                }
            }
        } catch (IntrospectionException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Unable to apply compliance update fields.", exception);
        }
    }
    /**
     * Maps a compliance entity and every related record to the response DTO.
     *
     * @param entity compliance entity to convert
     * @return response DTO containing the profile and associated data
     */
    private CustomerDto convertToDto(Customer entity) {
        return CustomerDto.builder()
                .amlId(entity.getAmlId())
                .userId(entity.getUserId())
                .externalRefNumber(entity.getExternalRefNumber())
                .fullName(entity.getFullName())
                .otherNationalities(entity.getOtherNationalities())
                .deliveryChannel(entity.getDeliveryChannel())
                .transactionType(entity.getTransactionType())
                .nameScreeningHit(entity.getNameScreeningHit())
                .documentsVerificationHit(entity.getDocumentsVerificationHit())
                .riskRatingHit(entity.getRiskRatingHit())
                .status(entity.getStatus())
                .onboardedByUser(entity.getOnboardedByUser())
                .onboardedByCompany(entity.getOnboardedByCompany())
                .comment(entity.getComment())
                .registeredAt(entity.getRegisteredAt())
                .lastReviewDate(entity.getLastReviewDate())
                .reportGeneratedOn(entity.getReportGeneratedOn())
                .companyName(entity.getCompanyName())
                .positionInCompany(entity.getPositionInCompany())
                .kycData(mapKycToDto(entity))
                .riskRatingsAndOverrides(mapRiskRatingsToDto(entity.getRiskRatingsAndOverrides()))
                .screeningHits(entity.getScreeningHits().stream()
                        .map(hit -> ScreeningHitDto.builder()
                                .hitName(hit.getHitName())
                                .category(hit.getCategory())
                                .source(hit.getSource())
                                .score(hit.getScore())
                                .hitDetermination(hit.getHitDetermination())
                                .comments(hit.getComments())
                                .build())
                        .collect(Collectors.toList()))
                .auditLogs(entity.getAuditLogs().stream()
                        .map(log -> AuditLogDto.builder()
                                .actionDate(log.getActionDate())
                                .actionedBy(log.getActionedBy())
                                .actionTaken(log.getActionTaken())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    /**
     * Maps persisted risk ratings to their response DTO.
     *
     * @param risk persisted risk ratings, or {@code null}
     * @return mapped risk ratings, or {@code null} when none are stored
     */
    private RiskRatingsDto mapRiskRatingsToDto(RiskRatings risk) {
        if (risk == null) {
            return null;
        }
        return RiskRatingsDto.builder()
                .totalMatches(risk.getTotalMatches())
                .resolvedMatches(risk.getResolvedMatches())
                .genuineMatches(risk.getGenuineMatches())
                .notGenuineMatches(risk.getNotGenuineMatches())
                .unresolvedMatches(risk.getUnresolvedMatches())
                .countryResidenceScore(risk.getCountryResidenceScore())
                .countryResidenceLevel(risk.getCountryResidenceLevel())
                .deliveryChannelScore(risk.getDeliveryChannelScore())
                .deliveryChannelLevel(risk.getDeliveryChannelLevel())
                .industryScore(risk.getIndustryScore())
                .industryLevel(risk.getIndustryLevel())
                .nationalityScore(risk.getNationalityScore())
                .nationalityLevel(risk.getNationalityLevel())
                .productScore(risk.getProductScore())
                .productLevel(risk.getProductLevel())
                .antiSpoofingScore(risk.getAntiSpoofingScore())
                .antiSpoofingLevel(risk.getAntiSpoofingLevel())
                .baseRatingScore(risk.getBaseRatingScore())
                .baseRatingLevel(risk.getBaseRatingLevel())
                .strFiledOverride(risk.getStrFiledOverride())
                .nonResidentOverride(risk.getNonResidentOverride())
                .sanctionedResidenceOverride(risk.getSanctionedResidenceOverride())
                .sanctionedNationalityOverride(risk.getSanctionedNationalityOverride())
                .sanctionedPhoneCodeOverride(risk.getSanctionedPhoneCodeOverride())
                .sanctionHitOverride(risk.getSanctionHitOverride())
                .pepOverride(risk.getPepOverride())
                .specialInterestOverride(risk.getSpecialInterestOverride())
                .docVerificationOverride(risk.getDocVerificationOverride())
                .adverseMediaOverride(risk.getAdverseMediaOverride())
                .transactionOverride(risk.getTransactionOverride())
                .overallRatingCalculated(risk.getOverallRatingCalculated())
                .overallRatingFinal(risk.getOverallRatingFinal())
                .build();
    }

    /**
     * Maps KYC data supplied on a create request to related persistence entities.
     *
     * @param dto KYC request data, or {@code null} when no KYC data was supplied
     * @param customer compliance entity that will own the KYC entities
     */
    private void mapKycToEntity(KycDataDto dto, Customer customer) {
        if (dto == null) {
            return;
        }
        if (dto.getCustomerInformation() != null) {
            KycDataDto.CustomerInformation d = dto.getCustomerInformation();
            CustomerInformation e = new CustomerInformation();
            e.setAmlReferenceId(d.getAmlReferenceId()); e.setAmlDateTime(d.getAmlDateTime());
            e.setAmlStatus(d.getAmlStatus()); e.setAmlType(d.getAmlType());
            e.setFullName(d.getFullName()); e.setIndividualType(d.getIndividualType());
            e.setFatherName(d.getFatherName()); e.setGender(d.getGender()); e.setDateOfBirth(d.getDateOfBirth());
            e.setNationality(d.getNationality()); e.setCountryOfResidence(d.getCountryOfResidence());
            e.setResidentStatus(d.getResidentStatus()); e.setNationalIdNumber(d.getNationalIdNumber());
            e.setNationalIdExpiry(d.getNationalIdExpiry()); e.setPassportNumber(d.getPassportNumber());
            e.setPassportExpiry(d.getPassportExpiry()); e.setOtherNationalities(d.getOtherNationalities());
            e.setIsCrs(d.getIsCrs()); e.setOnboardedBy(d.getOnboardedBy()); e.setOnboardingDate(d.getOnboardingDate());
            e.setExternalReference(d.getExternalReference()); e.setRecordLastUpdated(d.getRecordLastUpdated()); e.setFooterText(d.getFooterText());
            customer.setCustomerInformation(e);
        }
        if (dto.getProfileInformation() != null) {
            KycDataDto.ProfileInformation d = dto.getProfileInformation();
            ProfileInformation e = new ProfileInformation();
            e.setWorkType(d.getWorkType()); e.setIndustry(d.getIndustry()); e.setDeliveryChannel(d.getDeliveryChannel());
            e.setRelationshipStartDate(d.getRelationshipStartDate()); e.setProducts(d.getProducts());
            e.setProductOffered(d.getProductOffered()); customer.setProfileInformation(e);
        }
        if (dto.getContactInformation() != null) {
            KycDataDto.ContactInformation d = dto.getContactInformation();
            ContactInformation e = new ContactInformation();
            e.setAddress(d.getAddress()); e.setTownCity(d.getTownCity()); e.setCountyState(d.getCountyState());
            e.setPostalCode(d.getPostalCode()); e.setContactNumber(d.getContactNumber()); e.setEmailAddress(d.getEmailAddress());
            customer.setContactInformation(e);
        }
        if (dto.getPepDeclaration() != null) {
            KycDataDto.PepDeclaration d = dto.getPepDeclaration();
            PepDeclaration e = new PepDeclaration();
            e.setCurrentlyHoldPublicPosition(d.getCurrentlyHoldPublicPosition());
            e.setHeldPublicPositionLast12Months(d.getHeldPublicPositionLast12Months());
            e.setEverHeldPublicPosition(d.getEverHeldPublicPosition()); e.setDiplomaticImmunity(d.getDiplomaticImmunity());
            e.setRelativeHeldPublicPositionLast12Months(d.getRelativeHeldPublicPositionLast12Months());
            e.setCloseAssociateHeldPublicPositionLast12Months(d.getCloseAssociateHeldPublicPositionLast12Months());
            e.setCourtConviction(d.getCourtConviction()); e.setDetails(d.getDetails());
            customer.setPepDeclaration(e);
        }
        if (dto.getSourceOfWealthFunds() != null) {
            KycDataDto.SourceOfWealthFunds d = dto.getSourceOfWealthFunds();
            SourceOfWealthFunds e = new SourceOfWealthFunds();
            e.setSourceOfWealth(d.getSourceOfWealth()); e.setSourceOfWealthOther(d.getSourceOfWealthOther());
            e.setSourceOfFunds(d.getSourceOfFunds()); e.setSourceOfFundsOther(d.getSourceOfFundsOther());
            customer.setSourceOfWealthFunds(e);
        }
        if (dto.getInvestmentRange() != null) {
            InvestmentRange e = new InvestmentRange();
            e.setInvestmentRange(dto.getInvestmentRange().getInvestmentRange());
            customer.setInvestmentRange(e);
        }
        if (dto.getOngoingDueDiligence() != null) {
            OngoingDueDiligence e = new OngoingDueDiligence();
            e.setLastReview(dto.getOngoingDueDiligence().getLastReview());
            e.setNextReview(dto.getOngoingDueDiligence().getNextReview());
            customer.setOngoingDueDiligence(e);
        }
        if (dto.getSignaturePanel() != null) {
            KycDataDto.SignaturePanel d = dto.getSignaturePanel();
            SignaturePanel e = new SignaturePanel();
            e.setSignature(d.getSignature()); e.setName(d.getName()); e.setPosition(d.getPosition()); e.setDate(d.getDate());
            customer.setSignaturePanel(e);
        }
    }

    /**
     * Builds the nested KYC response DTO from the profile's persisted KYC sections.
     *
     * @param customer profile containing KYC sections
     * @return KYC response data, or {@code null} when the profile has no KYC sections
     */
    private KycDataDto mapKycToDto(Customer c) {
        if (c.getCustomerInformation() == null && c.getProfileInformation() == null
                && c.getContactInformation() == null && c.getPepDeclaration() == null
                && c.getSourceOfWealthFunds() == null && c.getInvestmentRange() == null
                && c.getOngoingDueDiligence() == null && c.getSignaturePanel() == null) {
            return null;
        }
        KycDataDto.KycDataDtoBuilder builder = KycDataDto.builder();
        CustomerInformation c1 = c.getCustomerInformation();
        if (c1 != null) builder.customerInformation(KycDataDto.CustomerInformation.builder()
                .amlReferenceId(c1.getAmlReferenceId()).amlDateTime(c1.getAmlDateTime())
                .amlStatus(c1.getAmlStatus()).amlType(c1.getAmlType())
                .fullName(c1.getFullName()).individualType(c1.getIndividualType())
                .fatherName(c1.getFatherName()).gender(c1.getGender()).dateOfBirth(c1.getDateOfBirth())
                .nationality(c1.getNationality()).countryOfResidence(c1.getCountryOfResidence()).residentStatus(c1.getResidentStatus())
                .nationalIdNumber(c1.getNationalIdNumber()).nationalIdExpiry(c1.getNationalIdExpiry()).passportNumber(c1.getPassportNumber())
                .passportExpiry(c1.getPassportExpiry()).otherNationalities(c1.getOtherNationalities()).isCrs(c1.getIsCrs())
                .onboardedBy(c1.getOnboardedBy()).onboardingDate(c1.getOnboardingDate()).externalReference(c1.getExternalReference())
                .recordLastUpdated(c1.getRecordLastUpdated()).footerText(c1.getFooterText()).build());
        ProfileInformation p = c.getProfileInformation();
        if (p != null) builder.profileInformation(KycDataDto.ProfileInformation.builder().workType(p.getWorkType()).industry(p.getIndustry())
                .deliveryChannel(p.getDeliveryChannel()).relationshipStartDate(p.getRelationshipStartDate()).products(p.getProducts())
                .productOffered(p.getProductOffered()).build());
        ContactInformation ci = c.getContactInformation();
        if (ci != null) builder.contactInformation(KycDataDto.ContactInformation.builder().address(ci.getAddress()).townCity(ci.getTownCity())
                .countyState(ci.getCountyState()).postalCode(ci.getPostalCode()).contactNumber(ci.getContactNumber()).emailAddress(ci.getEmailAddress()).build());
        PepDeclaration pep = c.getPepDeclaration();
        if (pep != null) builder.pepDeclaration(KycDataDto.PepDeclaration.builder().currentlyHoldPublicPosition(pep.getCurrentlyHoldPublicPosition())
                .heldPublicPositionLast12Months(pep.getHeldPublicPositionLast12Months()).everHeldPublicPosition(pep.getEverHeldPublicPosition())
                .diplomaticImmunity(pep.getDiplomaticImmunity()).relativeHeldPublicPositionLast12Months(pep.getRelativeHeldPublicPositionLast12Months())
                .closeAssociateHeldPublicPositionLast12Months(pep.getCloseAssociateHeldPublicPositionLast12Months())
                .courtConviction(pep.getCourtConviction()).details(pep.getDetails()).build());
        SourceOfWealthFunds swf = c.getSourceOfWealthFunds();
        if (swf != null) builder.sourceOfWealthFunds(KycDataDto.SourceOfWealthFunds.builder().sourceOfWealth(swf.getSourceOfWealth())
                .sourceOfWealthOther(swf.getSourceOfWealthOther()).sourceOfFunds(swf.getSourceOfFunds()).sourceOfFundsOther(swf.getSourceOfFundsOther()).build());
        InvestmentRange ir = c.getInvestmentRange();
        if (ir != null) builder.investmentRange(KycDataDto.InvestmentRange.builder().investmentRange(ir.getInvestmentRange()).build());
        OngoingDueDiligence odd = c.getOngoingDueDiligence();
        if (odd != null) builder.ongoingDueDiligence(KycDataDto.OngoingDueDiligence.builder().lastReview(odd.getLastReview()).nextReview(odd.getNextReview()).build());
        SignaturePanel sp = c.getSignaturePanel();
        if (sp != null) builder.signaturePanel(KycDataDto.SignaturePanel.builder().signature(sp.getSignature()).name(sp.getName())
                .position(sp.getPosition()).date(sp.getDate()).build());
        return builder.build();
    }
    /**
     * Trims a string and converts null or blank values to {@code null}.
     *
     * @param value input string
     * @return trimmed value, or {@code null} when the input is null or blank
     */
    private String nullIfEmpty(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }
}
