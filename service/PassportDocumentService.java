package org.aml.service;

import jakarta.annotation.PostConstruct;
import org.springframework.cache.annotation.CacheEvict;
import org.aml.model.Customer;
import org.aml.model.CustomerInformation;
import org.aml.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Service
public class PassportDocumentService {

    private static final Logger logger = LoggerFactory.getLogger(PassportDocumentService.class);
    private static final long MAX_FILE_SIZE = 8L * 1024 * 1024;
    /*private static final int MIN_WIDTH = 600;
    private static final int MIN_HEIGHT = 400*/;
    private static final long MAX_PIXELS = 16_000_000L;

    private final CustomerRepository customerRepository;
    private final Path storageDirectory;

    public PassportDocumentService(
            CustomerRepository customerRepository,
            @Value("${app.passport.storage-dir:./data/passport-documents}") String storageDirectory) {
        this.customerRepository = customerRepository;
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    @PostConstruct
    void createStorageDirectory() throws IOException {
        Files.createDirectories(storageDirectory);
    }

    @Transactional
    @CacheEvict(cacheNames = "complianceReports", allEntries = true, beforeInvocation = true)
    public void upload(String amlId, MultipartFile upload) {
        Customer customer = findCustomer(amlId);
        ValidatedImage validatedImage = readAndValidate(upload);
        byte[] bytes = validatedImage.bytes();
        ImageDetails image = validatedImage.details();

        String key = UUID.randomUUID() + image.extension();
        Path destination = resolveStoredFile(key);
        try {
            Files.write(destination, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            String oldKey = customer.getPassportDocumentKey();
            customer.setPassportDocumentKey(key);
            customer.setPassportDocumentName(safeDisplayName(upload.getOriginalFilename(), image.extension()));
            customer.setPassportImageType(image.contentType());
            customer.setPassportImageQualityStatus("PASSED");
            customer.setPassportVerificationStatus("QUALITY_PASSED");
            customer.setDocumentsVerificationHit(Boolean.FALSE);
            customer.setPassportFullName(null);
            customer.setPassportFirstName(null);
            customer.setPassportLastName(null);
            customer.setVerifiedPassportNumber(null);
            customer.setPassportType(null);
            customer.setPassportIssuingCountryCode(null);
            customer.setPassportExpiryDate(null);
            customer.setPassportDateOfIssue(null);
            customer.setPassportIssuingAuthority(null);
            customer.setPassportVerifiedBy(null);
            customer.setPassportVerifiedAt(null);
            if (customer.getCustomerInformation() != null) {
                customer.getCustomerInformation().setPassportNumber(null);
                customer.getCustomerInformation().setPassportType(null);
                customer.getCustomerInformation().setPassportIssuingCountryCode(null);
                customer.getCustomerInformation().setPassportExpiry(null);
            }
            customerRepository.saveAndFlush(customer);
            scheduleReplacementCleanup(destination, oldKey, key);
        } catch (IOException exception) {
            deleteNewDocument(destination);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not securely store the passport image.", exception);
        } catch (RuntimeException exception) {
            deleteNewDocument(destination);
            throw exception;
        }

    }

    public Path createTemporaryValidatedUpload(MultipartFile upload) {
        ValidatedImage validatedImage = readAndValidate(upload);
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile(storageDirectory, "passport-validation-",
                    validatedImage.details().extension());
            Files.write(temporaryFile, validatedImage.bytes(),
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not prepare the passport image for validation.", exception);
        }
        return temporaryFile;
    }

    public void validateUpload(MultipartFile upload) {
        readAndValidate(upload);
    }

    public void deleteTemporaryUpload(Path temporaryFile) {
        Path normalized = temporaryFile.toAbsolutePath().normalize();
        if (!normalized.startsWith(storageDirectory)
                || !normalized.getFileName().toString().startsWith("passport-validation-")) {
            throw new IllegalArgumentException("Temporary passport image is outside the validation storage area.");
        }
        try {
            Files.deleteIfExists(normalized);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not clean up the temporary passport image.", exception);
        }
    }

    @Transactional(readOnly = true)
    public StoredPassport get(String amlId) {
        Customer customer = findCustomer(amlId);
        if (customer.getPassportDocumentKey() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No passport image has been uploaded.");
        }
        Path path = resolveStoredFile(customer.getPassportDocumentKey());
        if (!Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The stored passport image is unavailable.");
        }
        return new StoredPassport(path, customer.getPassportImageType(), customer.getPassportDocumentName());
    }

    public void deleteAfterCommit(String key) {
        if (key == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteStoredDocument(key);
                }
            });
        } else {
            deleteStoredDocument(key);
        }
    }

    private ImageDetails inspectImage(byte[] bytes) {
        if (bytes.length < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is not a supported image.");
        }
        String extension;
        String contentType;
        if ((bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            extension = ".jpg";
            contentType = "image/jpeg";
        } else if ((bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47) {
            extension = ".png";
            contentType = "image/png";
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a JPEG or PNG passport image.");
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded image cannot be read.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded image cannot be decoded.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MAX_PIXELS) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Image dimensions are too large. Resize the passport image and try again.");
                }
                if (reader.read(0) == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded image is blank or unreadable.");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded image is invalid or unreadable.", exception);
        }
        return new ImageDetails(extension, contentType);
    }

    private ValidatedImage readAndValidate(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a passport image to upload.");
        }
        if (upload.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passport image must be 8 MB or smaller.");
        }
        try {
            byte[] bytes = upload.getBytes();
            return new ValidatedImage(bytes, inspectImage(bytes));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not read the uploaded passport image.", exception);
        }
    }

    private Customer findCustomer(String amlId) {
        return customerRepository.findById(amlId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compliance record not found."));
    }

    private Path resolveStoredFile(String key) {
        if (key == null || !key.matches("[0-9a-fA-F-]{36}\\.(jpg|png)")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Passport image not found.");
        }
        Path file = storageDirectory.resolve(key).normalize();
        if (!file.startsWith(storageDirectory)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Passport image not found.");
        }
        return file;
    }

    private String safeDisplayName(String originalName, String extension) {
        String base = originalName == null ? "passport" : Path.of(originalName).getFileName().toString();
        String safe = base.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (safe.isBlank()) {
            return "passport" + extension;
        }
        return safe.length() <= 255 ? safe : safe.substring(safe.length() - 255);
    }

    private void scheduleReplacementCleanup(Path newPath, String oldKey, String newKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deletePreviousDocument(oldKey, newKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deletePreviousDocument(oldKey, newKey);
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteNewDocument(newPath);
                }
            }
        });
    }

    private void deletePreviousDocument(String oldKey, String newKey) {
        if (oldKey == null || oldKey.equals(newKey)) {
            return;
        }
        try {
            Files.deleteIfExists(resolveStoredFile(oldKey));
        } catch (IOException | ResponseStatusException exception) {
            logger.warn("Unable to remove replaced passport document", exception);
        }
    }

    private void deleteStoredDocument(String key) {
        try {
            Files.deleteIfExists(resolveStoredFile(key));
        } catch (IOException | ResponseStatusException exception) {
            logger.warn("Unable to remove passport document after compliance record deletion", exception);
        }
    }

    private void deleteNewDocument(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException cleanupException) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Passport image storage cleanup failed.", cleanupException);
        }
    }

    private record ImageDetails(String extension, String contentType) {
    }

    private record ValidatedImage(byte[] bytes, ImageDetails details) {
    }

    public record StoredPassport(Path path, String contentType, String displayName) {
    }
}
