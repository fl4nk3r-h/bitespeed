package com.fluxkart.bitespeed.dto.response;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fluxkart.bitespeed.dto.response.IdentifyResponse.ContactPayload;

/**
 * Unit tests for the {@link IdentifyResponse} DTO.
 * Verifies builder, getter/setter, and nested payload structure.
 */
class IdentifyResponseTest {

    // ─── Builder ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should build a complete IdentifyResponse")
        void testFullBuild() {
            ContactPayload payload = ContactPayload.builder()
                    .primaryContactId(1L)
                    .emails(List.of("lorraine@hillvalley.edu", "mcfly@hillvalley.edu"))
                    .phoneNumbers(List.of("123456"))
                    .secondaryContactIds(List.of(23L))
                    .build();

            IdentifyResponse response = IdentifyResponse.builder()
                    .contact(payload)
                    .build();

            assertThat(response.getContact()).isNotNull();
            assertThat(response.getContact().getPrimaryContactId()).isEqualTo(1L);
            assertThat(response.getContact().getEmails()).containsExactly(
                    "lorraine@hillvalley.edu", "mcfly@hillvalley.edu");
            assertThat(response.getContact().getPhoneNumbers()).containsExactly("123456");
            assertThat(response.getContact().getSecondaryContactIds()).containsExactly(23L);
        }

        @Test
        @DisplayName("should build a response for a brand-new primary (no secondaries)")
        void testNewPrimaryBuild() {
            ContactPayload payload = ContactPayload.builder()
                    .primaryContactId(42L)
                    .emails(List.of("new@example.com"))
                    .phoneNumbers(List.of("999"))
                    .secondaryContactIds(Collections.emptyList())
                    .build();

            IdentifyResponse response = IdentifyResponse.builder()
                    .contact(payload)
                    .build();

            assertThat(response.getContact().getSecondaryContactIds()).isEmpty();
        }
    }

    // ─── ContactPayload ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("ContactPayload")
    class PayloadTests {

        @Test
        @DisplayName("primaryContactId round-trips via getter/setter")
        void testPrimaryContactId() {
            ContactPayload p = ContactPayload.builder()
                    .primaryContactId(5L)
                    .emails(Collections.emptyList())
                    .phoneNumbers(Collections.emptyList())
                    .secondaryContactIds(Collections.emptyList())
                    .build();

            assertThat(p.getPrimaryContactId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("emails list preserves order — primary email first")
        void testEmailsOrder() {
            ContactPayload p = ContactPayload.builder()
                    .primaryContactId(1L)
                    .emails(List.of("primary@test.com", "secondary@test.com"))
                    .phoneNumbers(Collections.emptyList())
                    .secondaryContactIds(Collections.emptyList())
                    .build();

            assertThat(p.getEmails().get(0)).isEqualTo("primary@test.com");
        }

        @Test
        @DisplayName("phoneNumbers list preserves order — primary phone first")
        void testPhonesOrder() {
            ContactPayload p = ContactPayload.builder()
                    .primaryContactId(1L)
                    .emails(Collections.emptyList())
                    .phoneNumbers(List.of("111", "222"))
                    .secondaryContactIds(Collections.emptyList())
                    .build();

            assertThat(p.getPhoneNumbers().get(0)).isEqualTo("111");
        }
    }

    // ─── Equals, HashCode, ToString ──────────────────────────────────────────

    @Nested
    @DisplayName("Equals & HashCode")
    class EqualityTests {

        @Test
        @DisplayName("equal responses have same hashCode")
        void testEqualsAndHashCode() {
            ContactPayload p1 = ContactPayload.builder()
                    .primaryContactId(1L)
                    .emails(List.of("a@b.com"))
                    .phoneNumbers(List.of("111"))
                    .secondaryContactIds(Collections.emptyList())
                    .build();
            ContactPayload p2 = ContactPayload.builder()
                    .primaryContactId(1L)
                    .emails(List.of("a@b.com"))
                    .phoneNumbers(List.of("111"))
                    .secondaryContactIds(Collections.emptyList())
                    .build();

            IdentifyResponse r1 = IdentifyResponse.builder().contact(p1).build();
            IdentifyResponse r2 = IdentifyResponse.builder().contact(p2).build();

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        }

        @Test
        @DisplayName("toString contains class data")
        void testToString() {
            ContactPayload p = ContactPayload.builder()
                    .primaryContactId(1L)
                    .emails(List.of("x@y.com"))
                    .phoneNumbers(List.of("555"))
                    .secondaryContactIds(Collections.emptyList())
                    .build();

            IdentifyResponse r = IdentifyResponse.builder().contact(p).build();
            assertThat(r.toString()).contains("x@y.com");
        }
    }
}
