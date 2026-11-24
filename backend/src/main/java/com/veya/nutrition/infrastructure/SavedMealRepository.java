package com.veya.nutrition.infrastructure;

import com.veya.nutrition.domain.SavedMeal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/** Spring Data JPA repository of saved meal templates with their lines (US-08). */
public interface SavedMealRepository extends JpaRepository<SavedMeal, UUID> {

    @Query("select m from SavedMeal m left join fetch m.items where m.userId = :userId order by lower(m.name), m.id")
    List<SavedMeal> findAllOfUser(UUID userId);

    boolean existsByUserIdAndName(UUID userId, String name);
}
