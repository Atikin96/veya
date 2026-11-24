package com.veya.activity.infrastructure;

import com.veya.activity.domain.ActivityEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Spring Data JPA repository of actual workout records (US-13). */
public interface ActivityEntryRepository extends JpaRepository<ActivityEntry, UUID> {

    List<ActivityEntry> findByUserIdAndDateOrderByCreatedAtAscIdAsc(UUID userId, LocalDate date);
}
