package org.aml.service;

import lombok.RequiredArgsConstructor;
import org.aml.constants.AMLConstants;
import org.aml.model.User;
import org.aml.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(CustomUserDetailsService.class);

    private final UserRepository repository;

    /**
     * Loads Spring Security user details using the account email address.
     *
     * @param email account email used as the username
     * @return security user details for the account
     * @throws UsernameNotFoundException if no account matches the email
     */
    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        logger.debug("Loading account for authentication");
        User user = repository.findByEmail(email)
                .orElseThrow(() -> {
                    logger.warn("Authentication account lookup failed");
                    return new UsernameNotFoundException(AMLConstants.USER_NOT_FOUND);
                });

        logger.debug("Authentication account loaded");
        return org.springframework.security.core.userdetails.User
                .builder()
                .username(user.getEmail()) // principal is email
                .password(user.getPassword())
                .roles((user.getRole() != null ? user.getRole() : org.aml.model.Role.USER).name())
                .build();
    }
}