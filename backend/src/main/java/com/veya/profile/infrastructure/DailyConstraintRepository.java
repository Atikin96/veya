package com.veya.profile.infrastructure;

import com.veya.profile.domain.DailyConstraint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Spring Data JPA repository of daily planning constraints (unique per user and date). */
public interface DailyConstraintRepository extends JpaRepository<DailyConstraint, UUID> {

    Optional<DailyConstraint> findByUserIdAndDate(UUID userId, LocalDate date);
}
