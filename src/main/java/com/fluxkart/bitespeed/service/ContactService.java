package com.fluxkart.bitespeed.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fluxkart.bitespeed.dto.request.IdentifyRequest;
import com.fluxkart.bitespeed.dto.response.IdentifyResponse;
import com.fluxkart.bitespeed.model.Contact;
import com.fluxkart.bitespeed.model.Contact.LinkPrecedence;
import com.fluxkart.bitespeed.repository.ContactRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service encapsulating all identity reconciliation business logic.
 * <p>
 * Handles the four reconciliation cases described in the BiteSpeed spec:
 * <ol>
 * <li><b>No match</b> — creates a new primary contact.</li>
 * <li><b>Partial match</b> — creates a secondary linked to the existing
 * primary.</li>
 * <li><b>Exact match</b> — returns the existing cluster unchanged.</li>
 * <li><b>Two-primary merge</b> — demotes the newer primary and re-links its
 * cluster.</li>
 * </ol>
 * </p>
 *
 * @author fl4nk3r
 * @version 1.0
 * @since 2026-03-01
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContactService {

    private final ContactRepository contactRepository;

    /**
     * Core identity reconciliation logic.
     * <p>
     * Algorithm:
     * <ol>
     * <li>Find all contacts matching the incoming email or phoneNumber.</li>
     * <li>If none found → brand-new customer, create primary contact.</li>
     * <li>If found → resolve their primaries.
     * <ul>
     * <li>If two different primaries are found → <b>MERGE</b>: older wins,
     * newer is demoted to secondary, and all of the demoted primary's
     * secondaries are re-linked to the winner.</li>
     * <li>If new info (email/phone not seen before) → create a new secondary.</li>
     * </ul>
     * </li>
     * <li>Build and return the consolidated response.</li>
     * </ol>
     * </p>
     *
     * @param request the identification request containing email and/or phone
     * @return a consolidated {@link IdentifyResponse} for the matched cluster
     */
    @Transactional
    public IdentifyResponse identify(IdentifyRequest request) {
        String email = normalise(request.getEmail());
        String phone = normalise(request.getPhoneNumber());

        log.info("Identify request — email: {}, phone: {}", email, phone);

        // Step 1: Find all directly matching contacts
        List<Contact> matches = contactRepository.findByEmailOrPhoneNumber(email, phone);

        // Step 2: No matches → new customer
        if (matches.isEmpty()) {
            Contact primary = createPrimary(email, phone);
            log.info("No matches found. Created new primary contact id={}", primary.getId());
            return buildResponse(primary, Collections.emptyList());
        }

        // Step 3: Resolve all unique primaries from the matched contacts
        Set<Long> primaryIds = resolvePrimaryIds(matches);

        // Step 3a: Two separate primary clusters found → merge them
        if (primaryIds.size() > 1) {
            log.info("Multiple primaries found: {}. Merging clusters.", primaryIds);
            primaryIds = mergeAndReLinkClusters(primaryIds);
        }

        // At this point there is exactly one winning primary
        Long winningPrimaryId = primaryIds.iterator().next();

        // Step 3b: Check if the incoming request carries info not yet in the cluster
        List<Contact> clusterContacts = contactRepository.findAllByPrimaryId(winningPrimaryId);
        boolean isNewInfo = hasNewInformation(clusterContacts, email, phone);

        if (isNewInfo) {
            Contact secondary = createSecondary(email, phone, winningPrimaryId);
            log.info("New info detected. Created secondary contact id={}", secondary.getId());
            clusterContacts.add(secondary);
        }

        // Step 4: Build consolidated response
        Contact primary = clusterContacts.stream()
                .filter(c -> c.getLinkPrecedence() == LinkPrecedence.primary)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Primary contact not found for id=" + winningPrimaryId));

        List<Contact> secondaries = clusterContacts.stream()
                .filter(c -> c.getLinkPrecedence() == LinkPrecedence.secondary)
                .collect(Collectors.toList());

        return buildResponse(primary, secondaries);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Walks each matched contact up to its primary and collects all
     * unique primary IDs.
     * <p>
     * A matched contact is itself a primary if its {@code linkPrecedence}
     * is {@link LinkPrecedence#primary}; otherwise its {@code linkedId}
     * points to the primary.
     * </p>
     *
     * @param matches the list of contacts returned by the initial lookup
     * @return a set of unique primary contact IDs
     */
    private Set<Long> resolvePrimaryIds(List<Contact> matches) {
        Set<Long> ids = new HashSet<>();
        for (Contact c : matches) {
            if (c.getLinkPrecedence() == LinkPrecedence.primary) {
                ids.add(c.getId());
            } else {
                ids.add(c.getLinkedId());
            }
        }
        return ids;
    }

    /**
     * Merges two (or more) primary clusters into one.
     * <p>
     * <b>Edge case:</b> the older primary (earliest {@code createdAt}) wins.
     * Each losing primary is:
     * <ol>
     * <li>Demoted to secondary ({@code linkedId} set to the winner's ID).</li>
     * <li>All secondaries previously linked to the loser are re-linked
     * to the winner.</li>
     * </ol>
     * </p>
     *
     * @param primaryIds set of at least two primary contact IDs to merge
     * @return a singleton set containing the winning primary's ID
     */
    private Set<Long> mergeAndReLinkClusters(Set<Long> primaryIds) {
        // Fetch all primaries and sort by createdAt ascending → oldest first
        List<Contact> primaries = primaryIds.stream()
                .map(id -> contactRepository.findById(id)
                        .orElseThrow(() -> new IllegalStateException("Contact not found: " + id)))
                .sorted(Comparator.comparing(Contact::getCreatedAt))
                .collect(Collectors.toList());

        Contact winner = primaries.get(0);

        // Demote all other primaries
        for (int i = 1; i < primaries.size(); i++) {
            Contact loser = primaries.get(i);
            log.info("Demoting primary id={} → secondary under winner id={}", loser.getId(), winner.getId());

            // Re-link all secondaries that were pointing to the loser
            List<Contact> loserSecondaries = contactRepository
                    .findByLinkedIdAndDeletedAtIsNull(loser.getId());

            for (Contact s : loserSecondaries) {
                s.setLinkedId(winner.getId());
            }
            contactRepository.saveAll(loserSecondaries);

            // Demote the loser itself
            loser.setLinkPrecedence(LinkPrecedence.secondary);
            loser.setLinkedId(winner.getId());
            contactRepository.save(loser);
        }

        return Collections.singleton(winner.getId());
    }

    /**
     * Determines whether the incoming request carries information that
     * is not yet present anywhere in the cluster.
     *
     * @param cluster the full list of contacts in the cluster
     * @param email   the normalised email from the request (may be {@code null})
     * @param phone   the normalised phone from the request (may be {@code null})
     * @return {@code true} if at least one field is genuinely new
     */
    private boolean hasNewInformation(List<Contact> cluster, String email, String phone) {
        Set<String> existingEmails = cluster.stream()
                .map(Contact::getEmail)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> existingPhones = cluster.stream()
                .map(Contact::getPhoneNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        boolean newEmail = email != null && !existingEmails.contains(email);
        boolean newPhone = phone != null && !existingPhones.contains(phone);

        // Only consider it new if it introduces BOTH something unseen
        // AND won't cause a duplicate-only row (e.g. one field already exists).
        // We create a secondary whenever at least one field is new.
        return newEmail || newPhone;
    }

    /**
     * Creates and persists a new primary contact.
     *
     * @param email the customer's email (may be {@code null})
     * @param phone the customer's phone number (may be {@code null})
     * @return the saved {@link Contact} entity
     */
    private Contact createPrimary(String email, String phone) {
        Contact contact = Contact.builder()
                .email(email)
                .phoneNumber(phone)
                .linkedId(null)
                .linkPrecedence(LinkPrecedence.primary)
                .build();
        return contactRepository.save(contact);
    }

    /**
     * Creates and persists a new secondary contact linked to the given primary.
     *
     * @param email     the customer's email (may be {@code null})
     * @param phone     the customer's phone number (may be {@code null})
     * @param primaryId the ID of the primary contact to link to
     * @return the saved {@link Contact} entity
     */
    private Contact createSecondary(String email, String phone, Long primaryId) {
        Contact contact = Contact.builder()
                .email(email)
                .phoneNumber(phone)
                .linkedId(primaryId)
                .linkPrecedence(LinkPrecedence.secondary)
                .build();
        return contactRepository.save(contact);
    }

    /**
     * Builds the final consolidated {@link IdentifyResponse}.
     * <p>
     * The primary's email and phone always appear first in their
     * respective lists. Duplicates from secondaries are removed while
     * preserving insertion order.
     * </p>
     *
     * @param primary     the primary contact (cluster root)
     * @param secondaries all secondary contacts in the cluster
     * @return the fully built {@link IdentifyResponse}
     */
    private IdentifyResponse buildResponse(Contact primary, List<Contact> secondaries) {
        List<String> emails = new ArrayList<>();
        List<String> phones = new ArrayList<>();
        List<Long> secondaryIds = new ArrayList<>();

        // Primary fields come first
        if (primary.getEmail() != null)
            emails.add(primary.getEmail());
        if (primary.getPhoneNumber() != null)
            phones.add(primary.getPhoneNumber());

        // Then secondaries (deduplicated)
        for (Contact s : secondaries) {
            secondaryIds.add(s.getId());
            if (s.getEmail() != null && !emails.contains(s.getEmail())) {
                emails.add(s.getEmail());
            }
            if (s.getPhoneNumber() != null && !phones.contains(s.getPhoneNumber())) {
                phones.add(s.getPhoneNumber());
            }
        }

        return IdentifyResponse.builder()
                .contact(IdentifyResponse.ContactPayload.builder()
                        .primaryContactId(primary.getId())
                        .emails(emails)
                        .phoneNumbers(phones)
                        .secondaryContactIds(secondaryIds)
                        .build())
                .build();
    }

    /**
     * Normalises user input by trimming whitespace and converting blank
     * strings to {@code null} for consistent database storage.
     *
     * @param value the raw input string
     * @return the trimmed string, or {@code null} if blank
     */
    private String normalise(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
