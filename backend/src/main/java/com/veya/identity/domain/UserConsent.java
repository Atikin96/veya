package com.veya.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;

import java.time.Instant;
import java.util.UUID;

/**
 * A recorded consent ({@code veya.user_consent}, LEG-2 / Q7). Only {@code withdrawn_at} may change, exactly once
 * (database trigger {@code user_consent_guard}; the app role has UPDATE on that column only).
 */
@Entity
@Table(name = "user_consent")
@DynamicUpdate
public class UserConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "consent_document_id", nullable = false, updatable = false)
    private ConsentDocument document;

    @Generated
    @Column(name = "granted_at", insertable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    protected UserConsent() {
    }

    public UserConsent(UUID userId, ConsentDocument document) {
        this.userId = userId;
        this.document = document;
    }

    public boolean isActive() {
        return withdrawnAt == null;
    }

    /** @throws IllegalStateException if already withdrawn (the database would reject it as well). */
    public void withdraw(Instant at) {
        if (withdrawnAt != null) {
            throw new IllegalStateException("Consent already withdrawn");
        }
        // never before the database-generated grant time (CHECK user_consent_withdrawn_after_granted)
        withdrawnAt = grantedAt != null && at.isBefore(grantedAt) ? grantedAt : at;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public ConsentDocument getDocument() {
        return document;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }
}
