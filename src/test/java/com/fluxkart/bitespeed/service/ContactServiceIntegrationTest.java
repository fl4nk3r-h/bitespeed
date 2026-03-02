package com.fluxkart.bitespeed.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxkart.bitespeed.dto.request.IdentifyRequest;
import com.fluxkart.bitespeed.dto.response.IdentifyResponse;
import com.fluxkart.bitespeed.repository.ContactRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Full-stack integration tests for the Identity Reconciliation Service.
 * <p>
 * Uses {@code @SpringBootTest} with a real application context and an
 * in-memory H2 database. Tests all four reconciliation cases end-to-end
 * through the HTTP layer.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class ContactServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        contactRepository.deleteAll();
    }

    // ─── Case 1: Brand New Contact ───────────────────────────────────────────

    @Nested
    @DisplayName("Case 1: No existing contact")
    class NewContactTests {

        @Test
        @DisplayName("new email+phone → creates primary, returns consolidated response")
        void newContact_shouldCreatePrimary() throws Exception {
            IdentifyRequest req = new IdentifyRequest();
            req.setEmail("lorraine@hillvalley.edu");
            req.setPhoneNumber("123456");

            IdentifyResponse response = doIdentify(req);

            assertThat(response.getContact().getPrimaryContactId()).isNotNull();
            assertThat(response.getContact().getEmails()).containsExactly("lorraine@hillvalley.edu");
            assertThat(response.getContact().getPhoneNumbers()).containsExactly("123456");
            assertThat(response.getContact().getSecondaryContactIds()).isEmpty();
            assertThat(contactRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("email-only → creates primary with null phone")
        void emailOnly_shouldCreatePrimary() throws Exception {
            IdentifyRequest req = new IdentifyRequest();
            req.setEmail("solo@test.com");

            IdentifyResponse response = doIdentify(req);

            assertThat(response.getContact().getEmails()).containsExactly("solo@test.com");
            assertThat(response.getContact().getPhoneNumbers()).isEmpty();
            assertThat(contactRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("phone-only → creates primary with null email")
        void phoneOnly_shouldCreatePrimary() throws Exception {
            IdentifyRequest req = new IdentifyRequest();
            req.setPhoneNumber("999888");

            IdentifyResponse response = doIdentify(req);

            assertThat(response.getContact().getPhoneNumbers()).containsExactly("999888");
            assertThat(response.getContact().getEmails()).isEmpty();
            assertThat(contactRepository.count()).isEqualTo(1);
        }
    }

    // ─── Case 2: Partial Match → Secondary Created ───────────────────────────

    @Nested
    @DisplayName("Case 2: Partial match → create secondary")
    class PartialMatchTests {

        @Test
        @DisplayName("shared phone, new email → creates secondary contact")
        void partialMatch_shouldCreateSecondary() throws Exception {
            // First: create primary
            IdentifyRequest first = new IdentifyRequest();
            first.setEmail("lorraine@hillvalley.edu");
            first.setPhoneNumber("123456");
            IdentifyResponse primaryResponse = doIdentify(first);
            Long primaryId = primaryResponse.getContact().getPrimaryContactId();

            // Second: same phone, different email
            IdentifyRequest second = new IdentifyRequest();
            second.setEmail("mcfly@hillvalley.edu");
            second.setPhoneNumber("123456");
            IdentifyResponse response = doIdentify(second);

            assertThat(response.getContact().getPrimaryContactId()).isEqualTo(primaryId);
            assertThat(response.getContact().getEmails())
                    .containsExactly("lorraine@hillvalley.edu", "mcfly@hillvalley.edu");
            assertThat(response.getContact().getPhoneNumbers()).containsExactly("123456");
            assertThat(response.getContact().getSecondaryContactIds()).hasSize(1);
            assertThat(contactRepository.count()).isEqualTo(2);
        }

        @Test
        @DisplayName("shared email, new phone → creates secondary contact")
        void sharedEmail_newPhone() throws Exception {
            IdentifyRequest first = new IdentifyRequest();
            first.setEmail("doc@hillvalley.edu");
            first.setPhoneNumber("111");
            doIdentify(first);

            IdentifyRequest second = new IdentifyRequest();
            second.setEmail("doc@hillvalley.edu");
            second.setPhoneNumber("222");
            IdentifyResponse response = doIdentify(second);

            assertThat(response.getContact().getPhoneNumbers()).containsExactly("111", "222");
            assertThat(contactRepository.count()).isEqualTo(2);
        }
    }

    // ─── Case 3: Exact Match → No New Rows ───────────────────────────────────

    @Nested
    @DisplayName("Case 3: Exact match → no new contact")
    class ExactMatchTests {

        @Test
        @DisplayName("identical request twice → no new contact created")
        void exactMatch_shouldNotCreateNewRow() throws Exception {
            IdentifyRequest req = new IdentifyRequest();
            req.setEmail("lorraine@hillvalley.edu");
            req.setPhoneNumber("123456");

            doIdentify(req);
            doIdentify(req);

            assertThat(contactRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("query by email-only when full record exists → correct response")
        void emailOnlyQuery() throws Exception {
            IdentifyRequest full = new IdentifyRequest();
            full.setEmail("lorraine@hillvalley.edu");
            full.setPhoneNumber("123456");
            IdentifyResponse created = doIdentify(full);

            IdentifyRequest emailOnly = new IdentifyRequest();
            emailOnly.setEmail("lorraine@hillvalley.edu");
            IdentifyResponse response = doIdentify(emailOnly);

            assertThat(response.getContact().getPrimaryContactId())
                    .isEqualTo(created.getContact().getPrimaryContactId());
            assertThat(contactRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("query by phone-only when full record exists → correct response")
        void phoneOnlyQuery() throws Exception {
            IdentifyRequest full = new IdentifyRequest();
            full.setEmail("lorraine@hillvalley.edu");
            full.setPhoneNumber("123456");
            IdentifyResponse created = doIdentify(full);

            IdentifyRequest phoneOnly = new IdentifyRequest();
            phoneOnly.setPhoneNumber("123456");
            IdentifyResponse response = doIdentify(phoneOnly);

            assertThat(response.getContact().getPrimaryContactId())
                    .isEqualTo(created.getContact().getPrimaryContactId());
        }
    }

    // ─── Case 4: Two-Primary Merge ───────────────────────────────────────────

    @Nested
    @DisplayName("Case 4: Two primaries → merge, older wins")
    class MergeTests {

        @Test
        @DisplayName("two independent primaries linked → older wins, newer demoted")
        void twoPrimaries_shouldMerge_olderWins() throws Exception {
            // Create first primary
            IdentifyRequest req1 = new IdentifyRequest();
            req1.setEmail("george@hillvalley.edu");
            req1.setPhoneNumber("919191");
            IdentifyResponse r1 = doIdentify(req1);
            Long olderPrimaryId = r1.getContact().getPrimaryContactId();

            // Create second primary
            IdentifyRequest req2 = new IdentifyRequest();
            req2.setEmail("biffsucks@hillvalley.edu");
            req2.setPhoneNumber("717171");
            doIdentify(req2);

            assertThat(contactRepository.count()).isEqualTo(2);

            // Linking request: email from first, phone from second
            IdentifyRequest linkingReq = new IdentifyRequest();
            linkingReq.setEmail("george@hillvalley.edu");
            linkingReq.setPhoneNumber("717171");
            IdentifyResponse merged = doIdentify(linkingReq);

            assertThat(merged.getContact().getPrimaryContactId()).isEqualTo(olderPrimaryId);
            assertThat(merged.getContact().getEmails())
                    .contains("george@hillvalley.edu", "biffsucks@hillvalley.edu");
            assertThat(merged.getContact().getPhoneNumbers())
                    .contains("919191", "717171");
            assertThat(merged.getContact().getSecondaryContactIds()).isNotEmpty();
        }

        @Test
        @DisplayName("secondaries of demoted primary re-linked to winner")
        void mergedPrimary_secondariesShouldBeReLinked() throws Exception {
            // Cluster A: primary + secondary
            IdentifyRequest a1 = new IdentifyRequest();
            a1.setEmail("a@test.com");
            a1.setPhoneNumber("111");
            IdentifyResponse ra1 = doIdentify(a1);
            Long clusterAId = ra1.getContact().getPrimaryContactId();

            IdentifyRequest a2 = new IdentifyRequest();
            a2.setEmail("a2@test.com");
            a2.setPhoneNumber("111");
            doIdentify(a2);

            // Cluster B: standalone primary
            IdentifyRequest b1 = new IdentifyRequest();
            b1.setEmail("b@test.com");
            b1.setPhoneNumber("222");
            doIdentify(b1);

            // Link clusters
            IdentifyRequest link = new IdentifyRequest();
            link.setEmail("a@test.com");
            link.setPhoneNumber("222");
            IdentifyResponse merged = doIdentify(link);

            assertThat(merged.getContact().getPrimaryContactId()).isEqualTo(clusterAId);
            assertThat(merged.getContact().getSecondaryContactIds()).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    // ─── Validation ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Request Validation")
    class ValidationTests {

        @Test
        @DisplayName("both fields null → 400 Bad Request")
        void bothFieldsNull_shouldReturn400() throws Exception {
            String body = "{\"email\": null, \"phoneNumber\": null}";

            mockMvc.perform(post("/identify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("empty body → 400 Bad Request")
        void emptyBody_shouldReturn400() throws Exception {
            mockMvc.perform(post("/identify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("blank email and blank phone → 400 Bad Request")
        void blankFields_shouldReturn400() throws Exception {
            String body = "{\"email\": \"   \", \"phoneNumber\": \"\"}";

            mockMvc.perform(post("/identify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── Health Endpoint ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /health → 200 OK")
    void healthEndpoint_shouldReturn200() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    // ─── Spec Example: Full Walk-Through ─────────────────────────────────────

    @Test
    @DisplayName("Spec Example: lorraine → mcfly → query by phone → consistent response")
    void specExample_fullWalkthrough() throws Exception {
        // Step 1: lorraine@hillvalley.edu + 123456
        IdentifyRequest req1 = new IdentifyRequest();
        req1.setEmail("lorraine@hillvalley.edu");
        req1.setPhoneNumber("123456");
        IdentifyResponse r1 = doIdentify(req1);
        Long primaryId = r1.getContact().getPrimaryContactId();

        // Step 2: mcfly@hillvalley.edu + 123456
        IdentifyRequest req2 = new IdentifyRequest();
        req2.setEmail("mcfly@hillvalley.edu");
        req2.setPhoneNumber("123456");
        IdentifyResponse r2 = doIdentify(req2);

        assertThat(r2.getContact().getPrimaryContactId()).isEqualTo(primaryId);
        assertThat(r2.getContact().getEmails())
                .containsExactly("lorraine@hillvalley.edu", "mcfly@hillvalley.edu");
        assertThat(r2.getContact().getPhoneNumbers()).containsExactly("123456");
        assertThat(r2.getContact().getSecondaryContactIds()).hasSize(1);

        // Step 3: Query by phone only → same cluster
        IdentifyRequest req3 = new IdentifyRequest();
        req3.setPhoneNumber("123456");
        IdentifyResponse r3 = doIdentify(req3);

        assertThat(r3.getContact().getPrimaryContactId()).isEqualTo(primaryId);
        assertThat(r3.getContact().getEmails())
                .containsExactly("lorraine@hillvalley.edu", "mcfly@hillvalley.edu");

        // Step 4: Query by lorraine email only → same cluster
        IdentifyRequest req4 = new IdentifyRequest();
        req4.setEmail("lorraine@hillvalley.edu");
        IdentifyResponse r4 = doIdentify(req4);

        assertThat(r4.getContact().getPrimaryContactId()).isEqualTo(primaryId);

        // Step 5: Query by mcfly email only → same cluster
        IdentifyRequest req5 = new IdentifyRequest();
        req5.setEmail("mcfly@hillvalley.edu");
        IdentifyResponse r5 = doIdentify(req5);

        assertThat(r5.getContact().getPrimaryContactId()).isEqualTo(primaryId);
    }

    // ─── JSON Response Shape ─────────────────────────────────────────────────

    @Test
    @DisplayName("JSON response matches BiteSpeed spec format")
    void responseShape_matchesSpec() throws Exception {
        String body = "{\"email\":\"doc@time.com\",\"phoneNumber\":\"8888\"}";

        mockMvc.perform(post("/identify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact").exists())
                .andExpect(jsonPath("$.contact.primaryContatctId").isNumber())
                .andExpect(jsonPath("$.contact.emails").isArray())
                .andExpect(jsonPath("$.contact.phoneNumbers").isArray())
                .andExpect(jsonPath("$.contact.secondaryContactIds").isArray());
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private IdentifyResponse doIdentify(IdentifyRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/identify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(
                result.getResponse().getContentAsString(),
                IdentifyResponse.class);
    }
}
