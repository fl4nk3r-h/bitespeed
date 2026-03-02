package com.fluxkart.bitespeed.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fluxkart.bitespeed.model.Contact;

/**
 * Spring Data JPA repository for {@link Contact} entities.
 * <p>
 * Provides custom JPQL queries used during identity reconciliation,
 * as well as the standard CRUD operations inherited from
 * {@link JpaRepository}.
 * </p>
 *
 * @author fl4nk3r
 * @version 1.0
 * @since 2026-03-01
 */
@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {

        /**
         * Finds all non-deleted contacts whose email or phone number matches
         * the given values.
         * <p>
         * This is the primary lookup used by the {@code /identify} endpoint
         * to seed the reconciliation algorithm.
         * </p>
         *
         * @param email       the email to match, or {@code null} to skip email matching
         * @param phoneNumber the phone number to match, or {@code null} to skip phone
         *                    matching
         * @return a list of matching {@link Contact} entities (never {@code null})
         */
        @Query("""
                        SELECT c FROM Contact c
                        WHERE c.deletedAt IS NULL
                          AND (
                              (:email IS NOT NULL AND c.email = :email)
                              OR (:phoneNumber IS NOT NULL AND c.phoneNumber = :phoneNumber)
                          )
                        """)
        List<Contact> findByEmailOrPhoneNumber(
                        @Param("email") String email,
                        @Param("phoneNumber") String phoneNumber);

        /**
         * Fetches every contact belonging to a primary cluster — i.e. the
         * primary itself plus all secondaries linked to it.
         * <p>
         * Results are ordered by {@code createdAt} ascending so the primary
         * always appears first.
         * </p>
         *
         * @param primaryId the ID of the primary contact (cluster root)
         * @return an ordered list of contacts in the cluster
         */
        @Query("""
                        SELECT c FROM Contact c
                        WHERE c.deletedAt IS NULL
                          AND (c.id = :primaryId OR c.linkedId = :primaryId)
                        ORDER BY c.createdAt ASC
                        """)
        List<Contact> findAllByPrimaryId(@Param("primaryId") Long primaryId);

        /**
         * Finds all non-deleted secondary contacts whose {@code linkedId}
         * equals the given value.
         * <p>
         * Used during a primary merge to re-parent secondaries from the
         * losing primary to the winning primary.
         * </p>
         *
         * @param linkedId the ID of the (soon-to-be-demoted) primary
         * @return a list of secondary contacts linked to {@code linkedId}
         */
        List<Contact> findByLinkedIdAndDeletedAtIsNull(Long linkedId);
}
