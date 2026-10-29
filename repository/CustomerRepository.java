package org.aml.repository;

import org.aml.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, String> {
    /**
     * Finds a compliance profile by its AML identifier.
     *
     * @param amlId AML identifier to search for
     * @return the matching profile, if present
     */
    Optional<Customer> findByAmlId(String amlId);

    /**
     * Finds a compliance profile by its associated user identifier.
     *
     * @param userId user identifier to search for
     * @return the matching profile, if present
     */
    Optional<Customer> findByUserId(Long userId);
}
