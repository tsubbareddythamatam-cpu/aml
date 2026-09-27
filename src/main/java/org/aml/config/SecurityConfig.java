package org.aml.config;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.DispatcherType;
import org.aml.constants.AMLConstants;
import org.aml.filter.JwtAuthenticationFilter;
import org.aml.filter.RateLimitFilter;
import org.aml.service.CustomUserDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger logger =
            LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtAuthenticationFilter jwtFilter;
    private final RateLimitFilter rateLimitFilter;
    private final CustomUserDetailsService userDetailsService;

    /**
     * Creates the password encoder bean used by authentication services.
     *
     * @return BCrypt password encoder
     */
    @Bean
    PasswordEncoder passwordEncoder() {

        logger.info("Initializing BCrypt PasswordEncoder");

        return new BCryptPasswordEncoder();
    }

    /**
     * Creates the authentication manager from Spring Security's configured authentication providers.
     *
     * @param config authentication configuration
     * @return configured authentication manager
     * @throws Exception if Spring cannot create the authentication manager
     */
    @Bean
    AuthenticationManager authenticationManager(
            AuthenticationConfiguration config)
            throws Exception {

        logger.info("Creating AuthenticationManager bean");

        AuthenticationManager authenticationManager =
                config.getAuthenticationManager();

        logger.info("AuthenticationManager bean created successfully");

        return authenticationManager;
    }

    /**
     * Configures stateless authentication rules for the OpenAPI and Swagger endpoints.
     *
     * @param http HTTP security builder
     * @return Swagger-specific security filter chain
     * @throws Exception if the security chain cannot be configured
     */
    @Bean
    @Order(1)
    SecurityFilterChain swaggerSecurityFilterChain(
            HttpSecurity http) throws Exception {

        http
                .securityMatcher(
                        "/swagger-ui/**",
                        "/v3/api-docs/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest()
                        .authenticated())
                .httpBasic(Customizer.withDefaults())
                .userDetailsService(userDetailsService);

        return http.build();
    }

    /**
     * Configures the application's stateless authorization and authentication filters.
     *
     * @param http HTTP security builder
     * @return application security filter chain
     * @throws Exception if the security chain cannot be configured
     */
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        logger.info("Starting Spring Security configuration");

        http
                .csrf(csrf -> {
                    logger.debug("Disabling CSRF protection");
                    csrf.disable();
                })

                .sessionManagement(session -> {
                    logger.debug("Configuring stateless session management");
                    session.sessionCreationPolicy(
                            SessionCreationPolicy.STATELESS);
                })

                .authorizeHttpRequests(auth -> {

                    logger.info(
                            "Configuring authorization rules");

                    logger.debug(
                            "Permitting public access to: {}",
                            AMLConstants.AUTH_API);

                    logger.debug(
                            "Restricting admin access to: {}",
                            AMLConstants.ADMIN_API);

                    logger.debug(
                            "Restricting user access to: {}",
                            AMLConstants.USER_API);

                    auth
                            .dispatcherTypeMatchers(DispatcherType.ERROR)
                            .permitAll()

                            .requestMatchers(
                                    AMLConstants.AUTH_API)
                            .permitAll()

                            .requestMatchers(
                                    AMLConstants.ADMIN_API)
                            .hasRole(
                                    AMLConstants.ADMIN)

                            .requestMatchers(
                                    AMLConstants.USER_API)
                            .hasAnyRole(
                                    AMLConstants.USER,
                                    AMLConstants.ADMIN)

                            .anyRequest()
                            .authenticated();
                })

                .userDetailsService(userDetailsService)

                .addFilterBefore(
                        rateLimitFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                        jwtFilter,
                        UsernamePasswordAuthenticationFilter.class);

        logger.info(
                "JWT Authentication Filter registered before UsernamePasswordAuthenticationFilter");

        logger.info(
                "Spring Security configuration completed successfully");

        return http.build();
    }
}