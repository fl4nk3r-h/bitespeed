package com.fluxkart.bitespeed.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

/**
 * Request DTO for the {@code POST /identify} endpoint.
 * <p>
 * At least one of {@code email} or {@code phoneNumber} must be provided.
 * The bean-validation constraint {@link #isAtLeastOneFieldPresent()} enforces
 * this rule automatically when the request is annotated with {@code @Valid}.
 * </p>
 *
 * @author fl4nk3r
 * @version 1.0
 * @since 2026-03-01
 */
@Data
public class IdentifyRequest {

    /** Customer email address (optional if {@code phoneNumber} is present). */
    @JsonProperty("email")
    private String email;

    /** Customer phone number (optional if {@code email} is present). */
    @JsonProperty("phoneNumber")
    private String phoneNumber;

    /**
     * Custom bean-validation constraint ensuring at least one contact
     * field is present and non-blank.
     *
     * @return {@code true} if either email or phoneNumber is non-blank
     */
    @AssertTrue(message = "At least one of email or phoneNumber must be provided")
    public boolean isAtLeastOneFieldPresent() {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasPhone = phoneNumber != null && !phoneNumber.isBlank();
        return hasEmail || hasPhone;
    }
}
