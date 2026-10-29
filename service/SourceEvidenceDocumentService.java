package org.aml.service;

import jakarta.annotation.PostConstruct;
import org.aml.model.Customer;
import org.aml.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SourceEvidenceDocumentService {

    private static final long MAX_FILE_SIZE = 8L * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 30_000_000L;

    private final CustomerRepository customerRepository;
    private final Path storageDirectory;

    public SourceEvidenceDocumentService(
            CustomerRepository customerRepository,
            @Value("${app.evidence.storage-dir:./data/evidence-documents}") String storageDirectory) {
        this.customerRepository = customerRepository;
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    @PostConstruct
    void createStorageDirectory() throws IOException {
        Files.createDirectories(storageDirectory);
    }

    public void validateUploads(MultipartFile... uploads) {
        for (MultipartFile upload : uploads) {
            if (upload != null && !upload.isEmpty()) {
                validate(upload);
            }
        }
    }

    @Transactional
    public void upload(String amlId, MultipartFile wealthUpload, MultipartFile fundsUpload) {
        List<ValidatedDocument> uploads = new ArrayList<>();
        if (wealthUpload != null && !wealthUpload.isEmpty()) {
            uploads.add(validate(wealthUpload).withKind("wealth"));
        }
        if (fundsUpload != null && !fundsUpload.isEmpty()) {
            uploads.add(validate(fundsUpload).withKind("funds"));
        }
        if (uploads.isEmpty()) {
            return;
        }

        Customer customer = customerRepository.findById(amlId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compliance record not found."));
        List<Path> newFiles = new ArrayList<>();
        List<String> replacedKeys = new ArrayList<>();
        try {
            for (ValidatedDocument document : uploads) {
                String key = UUID.randomUUID() + document.extension();
                Path destination = resolveStoredFile(key);
                newFiles.add(destination);
                Files.write(destination, document.bytes(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                if ("wealth".equals(document.kind())) {
                    replacedKeys.add(customer.getSourceOfWealthEvidenceKey());
                    customer.setSourceOfWealthEvidenceKey(key);
                    customer.setSourceOfWealthEvidenceName(document.displayName());
                    customer.setSourceOfWealthEvidenceType(document.contentType());
                } else {
                    replacedKeys.add(customer.getSourceOfFundsEvidenceKey());
                    customer.setSourceOfFundsEvidenceKey(key);
                    customer.setSourceOfFundsEvidenceName(document.displayName());
                    customer.setSourceOfFundsEvidenceType(document.contentType());
                }
            }
            customerRepository.saveAndFlush(customer);
            replacedKeys.forEach(this::deleteAfterCommit);
        } catch (IOException exception) {
            deleteNewFiles(newFiles, exception);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not securely store the source evidence document.", exception);
        } catch (RuntimeException exception) {
            deleteNewFiles(newFiles, exception);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public StoredEvidence get(String amlId, String kind) {
        Customer customer = customerRepository.findById(amlId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compliance record not found."));
        String key;
        String name;
        String contentType;
        if ("wealth".equals(kind)) {
            key = customer.getSourceOfWealthEvidenceKey();
            name = customer.getSourceOfWealthEvidenceName();
            contentType = customer.getSourceOfWealthEvidenceType();
        } else if ("funds".equals(kind)) {
            key = customer.getSourceOfFundsEvidenceKey();
            name = customer.getSourceOfFundsEvidenceName();
            contentType = customer.getSourceOfFundsEvidenceType();
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Source evidence document not found.");
        }
        if (key == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No source evidence document has been uploaded.");
        }
        Path path = resolveStoredFile(key);
        if (!Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The source evidence document is unavailable.");
        }
        return new StoredEvidence(path, contentType, name);
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

    private ValidatedDocument validate(MultipartFile upload) {
        if (upload.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each evidence file must be 8 MB or smaller.");
        }
        try {
            byte[] bytes = upload.getBytes();
            String extension;
            String contentType;
            if (bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D'
                    && bytes[3] == 'F' && bytes[4] == '-') {
                extension = ".pdf";
                contentType = "application/pdf";
            } else if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
                validateImage(bytes);
                extension = ".jpg";
                contentType = "image/jpeg";
            } else if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89
                    && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
                validateImage(bytes);
                extension = ".png";
                contentType = "image/png";
            } else if (isTiff(bytes)) {
                validateImage(bytes);
                extension = ".tiff";
                contentType = "image/tiff";
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Evidence files must be PDF, JPEG, PNG, or TIFF documents.");
            }
            return new ValidatedDocument(bytes, extension, contentType, safeDisplayName(upload.getOriginalFilename()), null);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not read the uploaded source evidence document.", exception);
        }
    }

    private boolean isTiff(byte[] bytes) {
        return bytes.length >= 4
                && ((bytes[0] == 'I' && bytes[1] == 'I' && bytes[2] == 42 && bytes[3] == 0)
                || (bytes[0] == 'M' && bytes[1] == 'M' && bytes[2] == 0 && bytes[3] == 42)
                || (bytes.length >= 8 && bytes[0] == 'I' && bytes[1] == 'I'
                && bytes[2] == 43 && bytes[3] == 0 && bytes[4] == 8 && bytes[5] == 0
                && bytes[6] == 0 && bytes[7] == 0));
    }

    private void validateImage(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded evidence image cannot be read.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded evidence image cannot be decoded.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MAX_IMAGE_PIXELS) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "The uploaded evidence image dimensions are too large.");
                }
                if (reader.read(0) == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "The uploaded evidence image cannot be decoded.");
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private String safeDisplayName(String originalName) {
        String base = originalName == null ? "evidence-document" : originalName.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        String safe = base.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (safe.isBlank()) {
            return "evidence-document";
        }
        return safe.length() <= 255 ? safe : safe.substring(safe.length() - 255);
    }

    private Path resolveStoredFile(String key) {
        if (key == null || !key.matches("[0-9a-fA-F-]{36}\\.(pdf|jpg|png|tiff)")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Source evidence document not found.");
        }
        Path file = storageDirectory.resolve(key).normalize();
        if (!file.startsWith(storageDirectory)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Source evidence document not found.");
        }
        return file;
    }

    private void deleteNewFiles(List<Path> files, RuntimeException originalException) {
        for (Path file : files) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException cleanupException) {
                originalException.addSuppressed(cleanupException);
            }
        }
    }

    private void deleteNewFiles(List<Path> files, IOException originalException) {
        for (Path file : files) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException cleanupException) {
                originalException.addSuppressed(cleanupException);
            }
        }
    }

    private void deleteStoredDocument(String key) {
        try {
            Files.deleteIfExists(resolveStoredFile(key));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not remove the replaced source evidence document.", exception);
        }
    }

    private record ValidatedDocument(byte[] bytes, String extension, String contentType,
                                     String displayName, String kind) {
        private ValidatedDocument withKind(String kind) {
            return new ValidatedDocument(bytes, extension, contentType, displayName, kind);
        }
    }

    public record StoredEvidence(Path path, String contentType, String displayName) {
    }
}
