package com.veya.shared.persistence;

import com.veya.shared.web.FieldViolation;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates codes against the reference tables (goal_type, meal_slot, equipment, ingredient), so an unknown code is
 * reported as a field error (400) instead of a foreign-key violation. The tables are the single source of codes.
 */
@Component
public class ReferenceCodes {

    public enum Vocabulary {
        GOAL("goal_type"), MEAL_SLOT("meal_slot"), EQUIPMENT("equipment"), INGREDIENT("ingredient");

        private final String table;

        Vocabulary(String table) {
            this.table = table;
        }
    }

    private final JdbcClient jdbc;

    public ReferenceCodes(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Set<String> existing(Vocabulary vocabulary, Collection<String> codes) {
        if (codes.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("SELECT code FROM " + vocabulary.table + " WHERE code IN (:codes)")
                .param("codes", codes).query(String.class).list());
    }

    /** Adds a field error for each unknown or duplicated code of {@code codes} (field {@code name[i]}). */
    public void check(Vocabulary vocabulary, String field, List<String> codes, List<FieldViolation> errors) {
        Set<String> known = existing(vocabulary, codes.stream().filter(c -> c != null).toList());
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < codes.size(); i++) {
            String code = codes.get(i);
            if (code == null || !known.contains(code)) {
                errors.add(new FieldViolation(field + "[" + i + "]", "is not a known code", code));
            } else if (!seen.add(code)) {
                errors.add(new FieldViolation(field + "[" + i + "]", "is a duplicate", code));
            }
        }
    }

    public void check(Vocabulary vocabulary, String field, String code, List<FieldViolation> errors) {
        if (code != null && existing(vocabulary, List.of(code)).isEmpty()) {
            errors.add(new FieldViolation(field, "is not a known code", code));
        }
    }
}
