package com.veya.profile.infrastructure;

import com.veya.profile.domain.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Spring Data JPA repository of profiles (US-02, US-03). */
public interface ProfileRepository extends JpaRepository<UserProfile, UUID> {
}
