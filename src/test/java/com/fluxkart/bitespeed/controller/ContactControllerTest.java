package com.fluxkart.bitespeed.controller;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxkart.bitespeed.config.SecurityConfig;
import com.fluxkart.bitespeed.dto.response.IdentifyResponse;
import com.fluxkart.bitespeed.dto.response.IdentifyResponse.ContactPayload;
import com.fluxkart.bitespeed.service.ContactService;

/**
 * Web-layer tests for {@link ContactController}.
 * Uses {@code @WebMvcTest} to load only the controller slice and mocks
 * the service layer.
 */
@WebMvcTest(ContactController.class)
@Import(SecurityConfig.class)
class ContactControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContactService contactService;

    // ─── POST /identify ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /identify")
    class IdentifyEndpointTests {

        @Test
        @DisplayName("valid request → 200 OK with consolidated contact")
        void validRequest_returns200() throws Exception {
            IdentifyResponse mockResponse = IdentifyResponse.builder()
                    .contact(ContactPayload.builder()
                            .primaryContactId(1L)
                            .emails(List.of("doc@hillvalley.edu"))
                            .phoneNumbers(List.of("555"))
                            .secondaryContactIds(Collections.emptyList())
                            .build())
                    .build();

            when(contactService.identify(any())).thenReturn(mockResponse);

            mockMvc.perform(post("/identify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"doc@hillvalley.edu\",\"phoneNumber\":\"555\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contact.primaryContatctId").value(1))
                    .andExpect(jsonPath("$.contact.emails[0]").value("doc@hillvalley.edu"))
                    .andExpect(jsonPath("$.contact.phoneNumbers[0]").value("555"))
                    .andExpect(jsonPath("$.contact.secondaryContactIds").isEmpty());
        }

        @Test
        @DisplayName("email-only request → 200 OK")
        void emailOnly_returns200() throws Exception {
            IdentifyResponse mockResponse = IdentifyResponse.builder()
                    .contact(ContactPayload.builder()
                            .primaryContactId(2L)
                            .emails(List.of("test@example.com"))
                            .phoneNumbers(Collections.emptyList())
                            .secondaryContactIds(Collections.emptyList())
                            .build())
                    .build();

            when(contactService.identify(any())).thenReturn(mockResponse);

            mockMvc.perform(post("/identify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"test@example.com\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contact.primaryContatctId").value(2));
        }

        @Test
        @DisplayName("phone-only request → 200 OK")
        void phoneOnly_returns200() throws Exception {
            IdentifyResponse mockResponse = IdentifyResponse.builder()
                    .contact(ContactPayload.builder()
                            .primaryContactId(3L)
                            .emails(Collections.emptyList())
                            .phoneNumbers(List.of("999"))
                            .secondaryContactIds(Collections.emptyList())
                            .build())
                    .build();

            when(contactService.identify(any())).thenReturn(mockResponse);

            mockMvc.perform(post("/identify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phoneNumber\":\"999\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contact.primaryContatctId").value(3));
        }

        @Test
        @DisplayName("both fields null → 400 Bad Request")
        void bothNull_returns400() throws Exception {
            mockMvc.perform(post("/identify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":null,\"phoneNumber\":null}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("empty body → 400 Bad Request")
        void emptyBody_returns400() throws Exception {
            mockMvc.perform(post("/identify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("missing Content-Type header → error response")
        void missingContentType_returnsError() throws Exception {
            mockMvc.perform(post("/identify")
                    .content("{\"email\":\"x@y.com\"}"))
                    .andExpect(status().is5xxServerError());
        }

        @Test
        @DisplayName("response has correct JSON structure matching spec")
        void responseStructureMatchesSpec() throws Exception {
            IdentifyResponse mockResponse = IdentifyResponse.builder()
                    .contact(ContactPayload.builder()
                            .primaryContactId(1L)
                            .emails(List.of("a@b.com", "c@d.com"))
                            .phoneNumbers(List.of("111", "222"))
                            .secondaryContactIds(List.of(2L, 3L))
                            .build())
                    .build();

            when(contactService.identify(any())).thenReturn(mockResponse);

            mockMvc.perform(post("/identify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"a@b.com\",\"phoneNumber\":\"111\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contact").exists())
                    .andExpect(jsonPath("$.contact.primaryContatctId").isNumber())
                    .andExpect(jsonPath("$.contact.emails").isArray())
                    .andExpect(jsonPath("$.contact.emails.length()").value(2))
                    .andExpect(jsonPath("$.contact.phoneNumbers").isArray())
                    .andExpect(jsonPath("$.contact.phoneNumbers.length()").value(2))
                    .andExpect(jsonPath("$.contact.secondaryContactIds").isArray())
                    .andExpect(jsonPath("$.contact.secondaryContactIds.length()").value(2));
        }
    }

    // ─── GET /health ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /health")
    class HealthEndpointTests {

        @Test
        @DisplayName("returns 200 OK with body 'OK'")
        void healthCheck_returns200() throws Exception {
            mockMvc.perform(get("/health"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("OK"));
        }
    }
}
