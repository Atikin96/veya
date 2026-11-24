package com.veya.shared.persistence;

import java.util.Locale;

/** Escapes user text for a case-insensitive {@code lower(column) LIKE '%' || :text || '%'} search. */
public final class SqlLike {

    private SqlLike() {
    }

    /**
     * Lower-cases the text and escapes the LIKE wildcards {@code %} and {@code _} and the escape character itself
     * (PostgreSQL's default LIKE escape is the backslash), so user input is matched literally.
     *
     * @return {@code null} for {@code null}
     */
    public static String escape(String text) {
        if (text == null) {
            return null;
        }
        return text.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
