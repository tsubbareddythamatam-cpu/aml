package org.aml.controller;

import lombok.RequiredArgsConstructor;
import org.aml.constants.AMLConstants;
import org.aml.dto.LoginRequest;
import org.aml.dto.LoginResponse;
import org.aml.dto.RegisterRequest;
import org.aml.service.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping(AMLConstants.REQUEST_PATH)
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping(AMLConstants.REGISTER)
    public String register(
            @RequestBody RegisterRequest request) {

        return authService.register(request);
    }

    @PostMapping(AMLConstants.LOGIN)
    public LoginResponse login(
            @RequestBody LoginRequest request) {

        return authService.login(request);
    }
}