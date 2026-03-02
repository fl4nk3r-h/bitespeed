package com.fluxkart.bitespeed.exception;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxkart.bitespeed.config.SecurityConfig;
import com.fluxkart.bitespeed.controller.ContactController;
import com.fluxkart.bitespeed.service.ContactService;

/**
 * Tests for the {@link GlobalExceptionHandler}.
 * Verifies that validation errors and unexpected exceptions produce
 * properly structured JSON error responses.
 */
@WebMvcTest(ContactController.class)
@Import(SecurityConfig.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContactService contactService;

    @Test
    @DisplayName("validation failure → 400 with structured error body")
    void validationError_returns400WithStructuredBody() throws Exception {
        mockMvc.perform(post("/identify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":null,\"phoneNumber\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("validation failure → error message mentions required field")
    void validationError_messageIsDescriptive() throws Exception {
        MvcResult result = mockMvc.perform(post("/identify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("At least one of email or phoneNumber must be provided");
    }

    @Test
    @DisplayName("server error → 500 with structured error body")
    void serverError_returns500WithStructuredBody() throws Exception {
        org.mockito.Mockito.when(contactService.identify(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("Simulated DB failure"));

        mockMvc.perform(post("/identify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@test.com\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
