package com.veya.nutrition.infrastructure;

import com.veya.nutrition.domain.FoodEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Spring Data JPA repository of confirmed diary entries (US-06, US-07, US-08). */
public interface FoodEntryRepository extends JpaRepository<FoodEntry, UUID> {

    /** One day, sorted by meal-slot order, then creation time (index food_entry_user_date_idx). */
    @Query("select e from FoodEntry e where e.userId = :userId and e.date = :date order by e.slotOrder, e.createdAt, e.id")
    List<FoodEntry> findDay(UUID userId, LocalDate date);

    /**
     * Ids of the newest entry per food name (case-insensitive), newest first (US-08). Native because PostgreSQL's
     * DISTINCT ON has no JPQL equivalent; the entities are then loaded by id.
     */
    @Query(value = """
            SELECT r.id FROM (SELECT DISTINCT ON (lower(f.food_name)) f.id, f.created_at
                              FROM food_entry f WHERE f.user_id = :userId
                              ORDER BY lower(f.food_name), f.created_at DESC, f.id) r
            ORDER BY r.created_at DESC, r.id LIMIT :limit""", nativeQuery = true)
    List<UUID> findRecentIds(UUID userId, int limit);
}
