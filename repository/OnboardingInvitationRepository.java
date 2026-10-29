package org.aml.repository;

import org.aml.model.OnboardingInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface OnboardingInvitationRepository extends JpaRepository<OnboardingInvitation, Long> {

    Optional<OnboardingInvitation> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from OnboardingInvitation invitation where invitation.tokenHash = :tokenHash")
    Optional<OnboardingInvitation> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
