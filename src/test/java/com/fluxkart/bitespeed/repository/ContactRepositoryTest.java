package com.fluxkart.bitespeed.repository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.fluxkart.bitespeed.model.Contact;
import com.fluxkart.bitespeed.model.Contact.LinkPrecedence;

/**
 * Repository-layer tests for {@link ContactRepository}.
 * Uses {@code @DataJpaTest} which auto-configures an embedded H2 database,
 * rolls back after each test, and only loads JPA-related beans.
 */
@DataJpaTest
class ContactRepositoryTest {

    @Autowired
    private ContactRepository contactRepository;

    @BeforeEach
    void cleanUp() {
        contactRepository.deleteAll();
    }

    // ─── findByEmailOrPhoneNumber ────────────────────────────────────────────

    @Nested
    @DisplayName("findByEmailOrPhoneNumber")
    class FindByEmailOrPhoneTests {

        @Test
        @DisplayName("returns contact matching by email only")
        void matchByEmail() {
            Contact c = Contact.builder()
                    .email("doc@hillvalley.edu")
                    .phoneNumber("555")
                    .linkPrecedence(LinkPrecedence.primary)
                    .build();
            contactRepository.save(c);

            List<Contact> result = contactRepository.findByEmailOrPhoneNumber("doc@hillvalley.edu", null);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getEmail()).isEqualTo("doc@hillvalley.edu");
        }

        @Test
        @DisplayName("returns contact matching by phone only")
        void matchByPhone() {
            Contact c = Contact.builder()
                    .email("doc@hillvalley.edu")
                    .phoneNumber("555")
                    .linkPrecedence(LinkPrecedence.primary)
                    .build();
            contactRepository.save(c);

            List<Contact> result = contactRepository.findByEmailOrPhoneNumber(null, "555");
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPhoneNumber()).isEqualTo("555");
        }

        @Test
        @DisplayName("returns contact matching by both email and phone")
        void matchByBoth() {
            Contact c = Contact.builder()
                    .email("doc@hillvalley.edu")
                    .phoneNumber("555")
                    .linkPrecedence(LinkPrecedence.primary)
                    .build();
            contactRepository.save(c);

            List<Contact> result = contactRepository.findByEmailOrPhoneNumber("doc@hillvalley.edu", "555");
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("returns empty list when nothing matches")
        void noMatch() {
            Contact c = Contact.builder()
                    .email("doc@hillvalley.edu")
                    .phoneNumber("555")
                    .linkPrecedence(LinkPrecedence.primary)
                    .build();
            contactRepository.save(c);

            List<Contact> result = contactRepository.findByEmailOrPhoneNumber("nobody@x.com", "999");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when both params are null")
        void bothNull() {
            Contact c = Contact.builder()
                    .email("doc@hillvalley.edu")
                    .phoneNumber("555")
                    .linkPrecedence(LinkPrecedence.primary)
                    .build();
            contactRepository.save(c);

            List<Contact> result = contactRepository.findByEmailOrPhoneNumber(null, null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns multiple contacts when email matches one and phone matches another")
        void crossMatch() {
            Contact c1 = Contact.builder()
                    .email("a@test.com")
                    .phoneNumber("111")
                    .linkPrecedence(LinkPrecedence.primary)
                    .build();
            Contact c2 = Contact.builder()
                    .email("b@test.com")
                    .phoneNumber("222")
                    .linkPrecedence(LinkPrecedence.primary)
                    .build();
            contactRepository.saveAll(List.of(c1, c2));

            List<Contact> result = contactRepository.findByEmailOrPhoneNumber("a@test.com", "222");
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("excludes soft-deleted contacts")
        void excludesSoftDeleted() {
            Contact active = Contact.builder()
                    .email("active@test.com")
                    .phoneNumber("111")
                    .linkPrecedence(LinkPrecedence.primary)
                    .build();
            Contact deleted = Contact.builder()
                    .email("deleted@test.com")
                    .phoneNumber("222")
                    .linkPrecedence(LinkPrecedence.primary)
                    .deletedAt(java.time.LocalDateTime.now())
                    .build();
            contactRepository.saveAll(List.of(active, deleted));

            List<Contact> result = contactRepository.findByEmailOrPhoneNumber("deleted@test.com", null);
            assertThat(result).isEmpty();
        }
    }

    // ─── findAllByPrimaryId ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findAllByPrimaryId")
    class FindAllByPrimaryIdTests {

        @Test
        @DisplayName("returns primary and all its secondaries")
        void returnsFullCluster() {
            Contact primary = contactRepository.save(Contact.builder()
                    .email("p@test.com")
                    .phoneNumber("111")
                    .linkPrecedence(LinkPrecedence.primary)
                    .build());

            contactRepository.save(Contact.builder()
                    .email("s1@test.com")
                    .phoneNumber("111")
                    .linkedId(primary.getId())
                    .linkPrecedence(LinkPrecedence.secondary)
                    .build());

            contactRepository.save(Contact.builder()
                    .email("s2@test.com")
                    .phoneNumber("222")
                    .linkedId(primary.getId())
                    .linkPrecedence(LinkPrecedence.secondary)
                    .build());

            List<Contact> cluster = contactRepository.findAllByPrimaryId(primary.getId());

            assertThat(cluster).hasSize(3);
            // Primary should be first (ordered by createdAt ASC)
            assertThat(cluster.get(0).getLinkPrecedence()).isEqualTo(LinkPrecedence.primary);
        }

        @Test
        @DisplayName("returns only the primary when no secondaries exist")
        void primaryOnly() {
            Contact primary = contactRepository.save(Contact.builder()
                    .email("solo@test.com")
                    .phoneNumber("333")
                    .linkPrecedence(LinkPrecedence.primary)
                    .build());

            List<Contact> cluster = contactRepository.findAllByPrimaryId(primary.getId());
            assertThat(cluster).hasSize(1);
            assertThat(cluster.get(0).getId()).isEqualTo(primary.getId());
        }

        @Test
        @DisplayName("excludes soft-deleted contacts from cluster")
        void excludesSoftDeletedFromCluster() {
            Contact primary = contactRepository.save(Contact.builder()
                    .email("p@test.com")
                    .phoneNumber("111")
                    .linkPrecedence(LinkPrecedence.primary)
                    .build());

            contactRepository.save(Contact.builder()
                    .email("alive@test.com")
                    .linkedId(primary.getId())
                    .linkPrecedence(LinkPrecedence.secondary)
                    .build());

            contactRepository.save(Contact.builder()
                    .email("dead@test.com")
                    .linkedId(primary.getId())
                    .linkPrecedence(LinkPrecedence.secondary)
                    .deletedAt(java.time.LocalDateTime.now())
                    .build());

            List<Contact> cluster = contactRepository.findAllByPrimaryId(primary.getId());
            assertThat(cluster).hasSize(2); // primary + 1 alive secondary
        }
    }

    // ─── findByLinkedIdAndDeletedAtIsNull ────────────────────────────────────

    @Nested
    @DisplayName("findByLinkedIdAndDeletedAtIsNull")
    class FindByLinkedIdTests {

        @Test
        @DisplayName("returns all active secondaries linked to a given primary")
        void returnsActiveSecondaries() {
            Contact primary = contactRepository.save(Contact.builder()
                    .email("p@test.com")
                    .phoneNumber("111")
                    .linkPrecedence(LinkPrecedence.primary)
                    .build());

            contactRepository.save(Contact.builder()
                    .email("s1@test.com")
                    .linkedId(primary.getId())
                    .linkPrecedence(LinkPrecedence.secondary)
                    .build());

            contactRepository.save(Contact.builder()
                    .email("s2@test.com")
                    .linkedId(primary.getId())
                    .linkPrecedence(LinkPrecedence.secondary)
                    .build());

            List<Contact> result = contactRepository.findByLinkedIdAndDeletedAtIsNull(primary.getId());
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("returns empty list when no secondaries exist")
        void noSecondaries() {
            Contact primary = contactRepository.save(Contact.builder()
                    .email("solo@test.com")
                    .linkPrecedence(LinkPrecedence.primary)
                    .build());

            List<Contact> result = contactRepository.findByLinkedIdAndDeletedAtIsNull(primary.getId());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("excludes soft-deleted secondaries")
        void excludesDeleted() {
            Contact primary = contactRepository.save(Contact.builder()
                    .email("p@test.com")
                    .linkPrecedence(LinkPrecedence.primary)
                    .build());

            contactRepository.save(Contact.builder()
                    .email("alive@test.com")
                    .linkedId(primary.getId())
                    .linkPrecedence(LinkPrecedence.secondary)
                    .build());

            contactRepository.save(Contact.builder()
                    .email("dead@test.com")
                    .linkedId(primary.getId())
                    .linkPrecedence(LinkPrecedence.secondary)
                    .deletedAt(java.time.LocalDateTime.now())
                    .build());

            List<Contact> result = contactRepository.findByLinkedIdAndDeletedAtIsNull(primary.getId());
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getEmail()).isEqualTo("alive@test.com");
        }
    }
}
