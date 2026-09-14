package org.aml.service;

import lombok.RequiredArgsConstructor;
import org.aml.constants.AMLConstants;
import org.aml.entity.User;
import org.aml.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository repository;

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        User user = repository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException(AMLConstants.USER_NOT_FOUND));

        return org.springframework.security.core.userdetails.User
                .builder()
                .username(user.getEmail()) // principal is email
                .password(user.getPassword())
                .roles(user.getRole().name().replace("ROLE_", ""))
                .build();
    }
}