package com.fluxkart.bitespeed.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fluxkart.bitespeed.dto.request.IdentifyRequest;
import com.fluxkart.bitespeed.dto.response.IdentifyResponse;
import com.fluxkart.bitespeed.service.ContactService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller exposing the identity reconciliation API.
 * <p>
 * Provides two endpoints:
 * <ul>
 * <li>{@code POST /identify} — reconciles and returns a consolidated contact
 * identity</li>
 * <li>{@code GET /health} — liveness probe for monitoring and deployment
 * platforms</li>
 * </ul>
 * </p>
 *
 * @author fl4nk3r
 * @version 1.0
 * @since 2026-03-01
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ContactController {

    private final ContactService contactService;

    /**
     * Identifies and reconciles a customer based on the provided email
     * and/or phone number.
     *
     * @param request the identification request containing email and/or phone
     *                number
     * @return a {@link ResponseEntity} wrapping the consolidated
     *         {@link IdentifyResponse}
     */
    @PostMapping("/identify")
    public ResponseEntity<IdentifyResponse> identify(@Valid @RequestBody IdentifyRequest request) {
        log.info("POST /identify received");
        IdentifyResponse response = contactService.identify(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Liveness probe endpoint.
     * <p>
     * Returns HTTP 200 with body {@code "OK"}. Used by Render.com
     * health checks and external monitoring.
     * </p>
     *
     * @return a {@link ResponseEntity} containing {@code "OK"}
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
