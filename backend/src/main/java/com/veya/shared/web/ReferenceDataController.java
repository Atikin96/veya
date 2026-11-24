package com.veya.shared.web;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reference vocabularies for the profile, constraint and diary forms (US-02, US-03, US-06). Read-only, not personal.
 * The codes are the primary keys of the reference tables, so they are exactly the values the API accepts.
 */
@RestController
public class ReferenceDataController {

    public record CodeName(String code, String name) {
    }

    public record MealSlotName(String code, String name, int sortOrder) {
    }

    public record ReferenceData(List<CodeName> goals, List<MealSlotName> mealSlots, List<CodeName> equipment,
                                List<CodeName> ingredients) {
    }

    private final JdbcClient jdbc;

    public ReferenceDataController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/v1/reference-data")
    @Transactional(readOnly = true)
    public ReferenceData referenceData() {
        return new ReferenceData(
                jdbc.sql("SELECT code, name FROM goal_type ORDER BY code").query(CodeName.class).list(),
                jdbc.sql("SELECT code, name, sort_order FROM meal_slot ORDER BY sort_order").query(MealSlotName.class).list(),
                jdbc.sql("SELECT code, name FROM equipment ORDER BY name").query(CodeName.class).list(),
                jdbc.sql("SELECT code, name FROM ingredient ORDER BY name").query(CodeName.class).list());
    }
}
