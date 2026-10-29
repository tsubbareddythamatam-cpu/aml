package org.aml.repository;


import java.util.Optional;

import org.aml.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User,Long> {

    /**
     * Finds a user by email address.
     *
     * @param email email address to search for
     * @return matching user, if present
     */
    Optional<User> findByEmail(String email);

    /**
     * Finds a user by phone number.
     *
     * @param phoneNumber phone number to search for
     * @return matching user, if present
     */
    Optional<User> findByPhoneNumber(String phoneNumber);

    /**
     * Finds a user by password reset or registration token.
     *
     * @param resetToken token to search for
     * @return matching user, if present
     */
    Optional<User> findByResetToken(String resetToken);
}