package com.veya.identity.infrastructure;

import com.veya.identity.domain.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Spring Data JPA repository of consent records (LEG-2, Q7). */
public interface ConsentRepository extends JpaRepository<UserConsent, UUID> {

    List<UserConsent> findByUserIdOrderByGrantedAtAscDocumentIdAsc(UUID userId);

    boolean existsByUserIdAndDocumentIdAndWithdrawnAtIsNull(UUID userId, Short documentId);
}
