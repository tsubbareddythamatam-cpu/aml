package org.aml.service;

import lombok.RequiredArgsConstructor;
import org.aml.constants.AMLConstants;
import org.aml.dto.LoginRequest;
import org.aml.dto.LoginResponse;
import org.aml.dto.RegisterRequest;
import org.aml.entity.Role;
import org.aml.entity.User;
import org.aml.exception.InvalidCredentialsException;
import org.aml.exception.UserAlreadyExistsException;
import org.aml.exception.UserNotFoundException;
import org.aml.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public String register(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(AMLConstants.USER_DERAILS_CANNOT_BE_NULL);
        }


        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException(
                    AMLConstants.USER_ALREADY_EXISTS + request.getEmail());
        }
        if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
            throw new UserAlreadyExistsException(
                    AMLConstants.USER_ALREADY_EXISTS_PHONE_NUMBER + request.getPhoneNumber());
        }
        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .companyName(request.getCompanyName())
                .phoneNumber(request.getPhoneNumber())
                .country(request.getCountry())
                .password(encoder.encode(request.getPassword()))
                .role(Role.valueOf(request.getRole()))
                .build();

        userRepository.save(user);

        return AMLConstants.USER_REGISTERED_SUCCESS;
    }

    public LoginResponse login(LoginRequest request) {

        try {

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()));

        } catch (BadCredentialsException ex) {

            throw new InvalidCredentialsException(
                    AMLConstants.INVALID_CREDENTIALS);
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new UserNotFoundException(
                                AMLConstants.USER_NOT_FOUND));

        String token = jwtService.generateToken(user.getEmail());


        return LoginResponse.builder()
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .companyName(user.getCompanyName())
                .phoneNumber(user.getPhoneNumber())
                .country(user.getCountry())
                .role(user.getRole().name())
                .token(token)
                .build();
    }
}