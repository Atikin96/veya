package com.veya.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Account ({@code veya.app_user}, US-01). The password is stored only as a bcrypt hash and never leaves the identity
 * module. Timestamps are set by the database (defaults and the {@code set_updated_at} trigger). The application role
 * may only update {@code email} and {@code password_hash}, hence {@link DynamicUpdate} and non-updatable columns.
 */
@Entity
@Table(name = "app_user")
@DynamicUpdate
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** Q8/Q13: time of the explicit "I am at least 18" confirmation. */
    @Column(name = "adult_confirmed_at", nullable = false, updatable = false)
    private Instant adultConfirmedAt;

    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected AppUser() {
    }

    public AppUser(String email, String passwordHash, Instant adultConfirmedAt) {
        this.email = Objects.requireNonNull(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.adultConfirmedAt = Objects.requireNonNull(adultConfirmedAt);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getAdultConfirmedAt() {
        return adultConfirmedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "AppUser[id=" + id + "]"; // no e-mail or hash in logs (LEG-5)
    }
}
