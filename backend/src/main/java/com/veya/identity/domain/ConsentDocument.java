package com.veya.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;

/** Versioned privacy notice / health-data consent text ({@code veya.consent_document}, LEG-2, Q7). Reference data. */
@Entity
@Immutable
@Table(name = "consent_document")
public class ConsentDocument {

    @Id
    private Short id;

    @Column(name = "consent_type", nullable = false)
    private String consentType;

    @Column(nullable = false)
    private String version;

    @Column(name = "published_on", nullable = false)
    private LocalDate publishedOn;

    @Column(nullable = false)
    private String summary;

    protected ConsentDocument() {
    }

    public int getId() {
        return id;
    }

    public String getConsentType() {
        return consentType;
    }

    public String getVersion() {
        return version;
    }

    public LocalDate getPublishedOn() {
        return publishedOn;
    }

    public String getSummary() {
        return summary;
    }
}
