package com.fluxkart.bitespeed.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for the {@code POST /identify} endpoint.
 * <p>
 * Wraps a single {@link ContactPayload} that contains the consolidated
 * identity cluster — the primary contact ID, all known emails and phone
 * numbers, and the IDs of every secondary contact.
 * </p>
 *
 * @author fl4nk3r
 * @version 1.0
 * @since 2026-03-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentifyResponse {

    /** The consolidated contact payload. */
    @JsonProperty("contact")
    private ContactPayload contact;

    /**
     * Inner payload representing the consolidated identity cluster.
     * <p>
     * Note: the JSON key {@code primaryContatctId} intentionally mirrors
     * the typo in the BiteSpeed specification.
     * </p>
     *
     * @author fl4nk3r
     * @version 1.0
     * @since 2026-03-01
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContactPayload {

        /** ID of the primary (root) contact in the cluster. */
        @JsonProperty("primaryContatctId") // Keeping the typo from the spec intentionally
        private Long primaryContactId;

        /** Deduplicated list of emails; primary's email appears first. */
        @JsonProperty("emails")
        private List<String> emails;

        /** Deduplicated list of phone numbers; primary's phone appears first. */
        @JsonProperty("phoneNumbers")
        private List<String> phoneNumbers;

        /** IDs of all secondary contacts linked to the primary. */
        @JsonProperty("secondaryContactIds")
        private List<Long> secondaryContactIds;
    }
}
