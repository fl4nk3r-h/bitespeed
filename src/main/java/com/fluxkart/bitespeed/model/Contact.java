package com.fluxkart.bitespeed.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity representing a customer contact record.
 * <p>
 * Each contact holds an optional email and phone number and is classified
 * as either {@code primary} or {@code secondary}. Secondary contacts are
 * linked to their primary via {@link #linkedId}. Together, a primary and
 * all its secondaries form an identity cluster.
 * </p>
 * <p>
 * The table is indexed on {@code email}, {@code phone_number}, and
 * {@code linked_id} for fast lookups during identity reconciliation.
 * </p>
 *
 * @author fl4nk3r
 * @version 1.0
 * @since 2026-03-01
 */
@Entity
@Table(name = "contact", indexes = {
        @Index(name = "idx_contact_email", columnList = "email"),
        @Index(name = "idx_contact_phone", columnList = "phone_number"),
        @Index(name = "idx_contact_linked_id", columnList = "linked_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "email")
    private String email;

    @Column(name = "linked_id")
    private Long linkedId;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_precedence", nullable = false)
    private LinkPrecedence linkPrecedence;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * Indicates whether a contact is a {@code primary} (cluster root)
     * or a {@code secondary} (linked to a primary).
     */
    public enum LinkPrecedence {
        /** Cluster root — the authoritative identity record. */
        primary,
        /** Linked record — points to a primary via {@code linkedId}. */
        secondary
    }
}
