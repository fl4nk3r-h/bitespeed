package com.fluxkart.bitespeed.model;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fluxkart.bitespeed.model.Contact.LinkPrecedence;

/**
 * Unit tests for the {@link Contact} JPA entity.
 * Verifies builder, getters, setters, and enum behaviour.
 */
class ContactTest {

    // ─── Builder ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should build a Contact with all fields set")
        void testBuilder_allFields() {
            LocalDateTime now = LocalDateTime.now();
            Contact contact = Contact.builder()
                    .id(1L)
                    .email("doc@hillvalley.edu")
                    .phoneNumber("555-0100")
                    .linkedId(null)
                    .linkPrecedence(LinkPrecedence.primary)
                    .createdAt(now)
                    .updatedAt(now)
                    .deletedAt(null)
                    .build();

            assertThat(contact.getId()).isEqualTo(1L);
            assertThat(contact.getEmail()).isEqualTo("doc@hillvalley.edu");
            assertThat(contact.getPhoneNumber()).isEqualTo("555-0100");
            assertThat(contact.getLinkedId()).isNull();
            assertThat(contact.getLinkPrecedence()).isEqualTo(LinkPrecedence.primary);
            assertThat(contact.getCreatedAt()).isEqualTo(now);
            assertThat(contact.getUpdatedAt()).isEqualTo(now);
            assertThat(contact.getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("should build a Contact with minimal fields")
        void testBuilder_minimalFields() {
            Contact contact = Contact.builder()
                    .linkPrecedence(LinkPrecedence.primary)
                    .build();

            assertThat(contact.getId()).isNull();
            assertThat(contact.getEmail()).isNull();
            assertThat(contact.getPhoneNumber()).isNull();
            assertThat(contact.getLinkPrecedence()).isEqualTo(LinkPrecedence.primary);
        }

        @Test
        @DisplayName("should build a secondary Contact with linkedId")
        void testBuilder_secondary() {
            Contact contact = Contact.builder()
                    .email("marty@hillvalley.edu")
                    .phoneNumber("555-0200")
                    .linkedId(1L)
                    .linkPrecedence(LinkPrecedence.secondary)
                    .build();

            assertThat(contact.getLinkedId()).isEqualTo(1L);
            assertThat(contact.getLinkPrecedence()).isEqualTo(LinkPrecedence.secondary);
        }
    }

    // ─── Getters & Setters ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Getters & Setters")
    class GetterSetterTests {

        @Test
        @DisplayName("setId / getId")
        void testId() {
            Contact c = new Contact();
            c.setId(42L);
            assertThat(c.getId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("setEmail / getEmail")
        void testEmail() {
            Contact c = new Contact();
            c.setEmail("test@example.com");
            assertThat(c.getEmail()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("setPhoneNumber / getPhoneNumber")
        void testPhoneNumber() {
            Contact c = new Contact();
            c.setPhoneNumber("9876543210");
            assertThat(c.getPhoneNumber()).isEqualTo("9876543210");
        }

        @Test
        @DisplayName("setLinkedId / getLinkedId")
        void testLinkedId() {
            Contact c = new Contact();
            c.setLinkedId(7L);
            assertThat(c.getLinkedId()).isEqualTo(7L);
        }

        @Test
        @DisplayName("setLinkPrecedence / getLinkPrecedence")
        void testLinkPrecedence() {
            Contact c = new Contact();
            c.setLinkPrecedence(LinkPrecedence.secondary);
            assertThat(c.getLinkPrecedence()).isEqualTo(LinkPrecedence.secondary);
        }

        @Test
        @DisplayName("setCreatedAt / getCreatedAt")
        void testCreatedAt() {
            Contact c = new Contact();
            LocalDateTime ts = LocalDateTime.of(2023, 4, 1, 0, 0);
            c.setCreatedAt(ts);
            assertThat(c.getCreatedAt()).isEqualTo(ts);
        }

        @Test
        @DisplayName("setUpdatedAt / getUpdatedAt")
        void testUpdatedAt() {
            Contact c = new Contact();
            LocalDateTime ts = LocalDateTime.of(2023, 4, 20, 5, 30);
            c.setUpdatedAt(ts);
            assertThat(c.getUpdatedAt()).isEqualTo(ts);
        }

        @Test
        @DisplayName("setDeletedAt / getDeletedAt — default is null")
        void testDeletedAt() {
            Contact c = new Contact();
            assertThat(c.getDeletedAt()).isNull();
            LocalDateTime ts = LocalDateTime.now();
            c.setDeletedAt(ts);
            assertThat(c.getDeletedAt()).isEqualTo(ts);
        }
    }

    // ─── LinkPrecedence Enum ─────────────────────────────────────────────────

    @Nested
    @DisplayName("LinkPrecedence Enum")
    class LinkPrecedenceTests {

        @Test
        @DisplayName("enum has exactly two values: primary and secondary")
        void testEnumValues() {
            LinkPrecedence[] values = LinkPrecedence.values();
            assertThat(values).hasSize(2);
            assertThat(values).containsExactly(LinkPrecedence.primary, LinkPrecedence.secondary);
        }

        @Test
        @DisplayName("valueOf works for 'primary'")
        void testValueOfPrimary() {
            assertThat(LinkPrecedence.valueOf("primary")).isEqualTo(LinkPrecedence.primary);
        }

        @Test
        @DisplayName("valueOf works for 'secondary'")
        void testValueOfSecondary() {
            assertThat(LinkPrecedence.valueOf("secondary")).isEqualTo(LinkPrecedence.secondary);
        }
    }

    // ─── Constructors ────────────────────────────────────────────────────────

    @Test
    @DisplayName("No-arg constructor creates an instance with all fields null")
    void testNoArgConstructor() {
        Contact c = new Contact();
        assertThat(c.getId()).isNull();
        assertThat(c.getEmail()).isNull();
        assertThat(c.getPhoneNumber()).isNull();
        assertThat(c.getLinkedId()).isNull();
        assertThat(c.getLinkPrecedence()).isNull();
        assertThat(c.getCreatedAt()).isNull();
        assertThat(c.getUpdatedAt()).isNull();
        assertThat(c.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("All-args constructor sets every field correctly")
    void testAllArgConstructor() {
        LocalDateTime now = LocalDateTime.now();
        Contact c = new Contact(1L, "555", "e@x.com", 2L,
                LinkPrecedence.secondary, now, now, null);

        assertThat(c.getId()).isEqualTo(1L);
        assertThat(c.getPhoneNumber()).isEqualTo("555");
        assertThat(c.getEmail()).isEqualTo("e@x.com");
        assertThat(c.getLinkedId()).isEqualTo(2L);
        assertThat(c.getLinkPrecedence()).isEqualTo(LinkPrecedence.secondary);
        assertThat(c.getCreatedAt()).isEqualTo(now);
        assertThat(c.getUpdatedAt()).isEqualTo(now);
        assertThat(c.getDeletedAt()).isNull();
    }
}
