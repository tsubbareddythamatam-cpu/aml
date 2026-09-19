package org.aml.controller;

import lombok.RequiredArgsConstructor;
import org.aml.constants.AMLConstants;
import org.aml.dto.BulkRegistrationResponse;
import org.aml.service.RegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(AMLConstants.REGISTRATION_PATH)
@RequiredArgsConstructor
public class RegistrationController {


    private final RegistrationService registrationService;

    @PostMapping(value = "/bulk",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BulkRegistrationResponse> bulkRegistration(
            @RequestParam("file") MultipartFile file) {

        BulkRegistrationResponse response =
                registrationService.bulkRegistration(file);

        if (response.getFailedRecords() > 0) {
            return ResponseEntity.status(HttpStatus.MULTI_STATUS)
                    .body(response); // 207
        }

        return ResponseEntity.ok(response);
    }
}
