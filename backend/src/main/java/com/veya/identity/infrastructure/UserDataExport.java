package com.veya.identity.infrastructure;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * LEG-4 / Q6 export. Deliberately JDBC: the database function {@code export_user_data} builds one consistent JSON
 * snapshot of all of the user's tables in a single statement (no password hash); re-implementing it with entities
 * would duplicate that logic.
 */
@Repository
public class UserDataExport {

    private final JdbcClient jdbc;

    public UserDataExport(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @return the export document, or {@code null} if the user does not exist. */
    public String exportJson(UUID userId) {
        return jdbc.sql("SELECT export_user_data(:id)::text").param("id", userId).query(String.class).single();
    }
}
