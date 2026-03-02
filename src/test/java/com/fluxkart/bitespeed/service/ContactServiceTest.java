package com.fluxkart.bitespeed.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fluxkart.bitespeed.dto.request.IdentifyRequest;
import com.fluxkart.bitespeed.dto.response.IdentifyResponse;
import com.fluxkart.bitespeed.model.Contact;
import com.fluxkart.bitespeed.model.Contact.LinkPrecedence;
import com.fluxkart.bitespeed.repository.ContactRepository;

/**
 * Unit tests for {@link ContactService} using Mockito.
 * Each reconciliation case is tested in isolation without a database.
 */
@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock
    private ContactRepository contactRepository;

    @InjectMocks
    private ContactService contactService;

    // ─── Helper: build a Contact with realistic fields ───────────────────────

    private Contact makePrimary(Long id, String email, String phone, LocalDateTime created) {
        return Contact.builder()
                .id(id)
                .email(email)
                .phoneNumber(phone)
                .linkedId(null)
                .linkPrecedence(LinkPrecedence.primary)
                .createdAt(created)
                .updatedAt(created)
                .build();
    }

    private Contact makeSecondary(Long id, String email, String phone, Long primaryId, LocalDateTime created) {
        return Contact.builder()
                .id(id)
                .email(email)
                .phoneNumber(phone)
                .linkedId(primaryId)
                .linkPrecedence(LinkPrecedence.secondary)
                .createdAt(created)
                .updatedAt(created)
                .build();
    }

    // ─── Case 1: No Match → New Primary ──────────────────────────────────────

    @Nested
    @DisplayName("Case 1: No existing match → create primary")
    class NoMatchTests {

        @Test
        @DisplayName("creates a new primary contact when no matches found")
        void shouldCreatePrimary() {
            when(contactRepository.findByEmailOrPhoneNumber(anyString(), anyString()))
                    .thenReturn(Collections.emptyList());

            Contact saved = makePrimary(1L, "doc@hillvalley.edu", "555", LocalDateTime.now());
            when(contactRepository.save(any(Contact.class))).thenReturn(saved);

            IdentifyRequest req = new IdentifyRequest();
            req.setEmail("doc@hillvalley.edu");
            req.setPhoneNumber("555");

            IdentifyResponse response = contactService.identify(req);

            assertThat(response.getContact().getPrimaryContactId()).isEqualTo(1L);
            assertThat(response.getContact().getEmails()).containsExactly("doc@hillvalley.edu");
            assertThat(response.getContact().getPhoneNumbers()).containsExactly("555");
            assertThat(response.getContact().getSecondaryContactIds()).isEmpty();

            // Verify a save was made
            ArgumentCaptor<Contact> captor = ArgumentCaptor.forClass(Contact.class);
            verify(contactRepository).save(captor.capture());
            assertThat(captor.getValue().getLinkPrecedence()).isEqualTo(LinkPrecedence.primary);
        }

        @Test
        @DisplayName("handles email-only request (no phone)")
        void emailOnly() {
            when(contactRepository.findByEmailOrPhoneNumber(anyString(), any()))
                    .thenReturn(Collections.emptyList());

            Contact saved = makePrimary(2L, "solo@test.com", null, LocalDateTime.now());
            when(contactRepository.save(any(Contact.class))).thenReturn(saved);

            IdentifyRequest req = new IdentifyRequest();
            req.setEmail("solo@test.com");

            IdentifyResponse response = contactService.identify(req);

            assertThat(response.getContact().getEmails()).containsExactly("solo@test.com");
            assertThat(response.getContact().getPhoneNumbers()).isEmpty();
        }

        @Test
        @DisplayName("handles phone-only request (no email)")
        void phoneOnly() {
            when(contactRepository.findByEmailOrPhoneNumber(any(), anyString()))
                    .thenReturn(Collections.emptyList());

            Contact saved = makePrimary(3L, null, "999", LocalDateTime.now());
            when(contactRepository.save(any(Contact.class))).thenReturn(saved);

            IdentifyRequest req = new IdentifyRequest();
            req.setPhoneNumber("999");

            IdentifyResponse response = contactService.identify(req);

            assertThat(response.getContact().getEmails()).isEmpty();
            assertThat(response.getContact().getPhoneNumbers()).containsExactly("999");
        }
    }

    // ─── Case 2: Partial Match → Create Secondary ────────────────────────────

    @Nested
    @DisplayName("Case 2: Partial match → create secondary")
    class PartialMatchTests {

        @Test
        @DisplayName("creates secondary when new email is provided for existing phone")
        void newEmailForExistingPhone() {
            LocalDateTime t1 = LocalDateTime.of(2023, 4, 1, 0, 0);
            Contact primary = makePrimary(1L, "lorraine@hillvalley.edu", "123456", t1);

            when(contactRepository.findByEmailOrPhoneNumber(anyString(), anyString()))
                    .thenReturn(List.of(primary));

            when(contactRepository.findAllByPrimaryId(1L))
                    .thenReturn(new ArrayList<>(List.of(primary)));

            Contact secondary = makeSecondary(23L, "mcfly@hillvalley.edu", "123456", 1L,
                    LocalDateTime.of(2023, 4, 20, 5, 30));
            when(contactRepository.save(any(Contact.class))).thenReturn(secondary);

            IdentifyRequest req = new IdentifyRequest();
            req.setEmail("mcfly@hillvalley.edu");
            req.setPhoneNumber("123456");

            IdentifyResponse response = contactService.identify(req);

            assertThat(response.getContact().getPrimaryContactId()).isEqualTo(1L);
            assertThat(response.getContact().getEmails())
                    .containsExactly("lorraine@hillvalley.edu", "mcfly@hillvalley.edu");
            assertThat(response.getContact().getPhoneNumbers()).containsExactly("123456");
            assertThat(response.getContact().getSecondaryContactIds()).containsExactly(23L);
        }

        @Test
        @DisplayName("creates secondary when new phone is provided for existing email")
        void newPhoneForExistingEmail() {
            LocalDateTime t1 = LocalDateTime.of(2023, 4, 1, 0, 0);
            Contact primary = makePrimary(1L, "test@test.com", "111", t1);

            when(contactRepository.findByEmailOrPhoneNumber(anyString(), anyString()))
                    .thenReturn(List.of(primary));

            when(contactRepository.findAllByPrimaryId(1L))
                    .thenReturn(new ArrayList<>(List.of(primary)));

            Contact secondary = makeSecondary(2L, "test@test.com", "222", 1L, LocalDateTime.now());
            when(contactRepository.save(any(Contact.class))).thenReturn(secondary);

            IdentifyRequest req = new IdentifyRequest();
            req.setEmail("test@test.com");
            req.setPhoneNumber("222");

            IdentifyResponse response = contactService.identify(req);

            assertThat(response.getContact().getPrimaryContactId()).isEqualTo(1L);
            assertThat(response.getContact().getPhoneNumbers()).contains("111", "222");
        }
    }

    // ─── Case 3: Exact Match → No New Rows ───────────────────────────────────

    @Nested
    @DisplayName("Case 3: Exact match → no new contact created")
    class ExactMatchTests {

        @Test
        @DisplayName("does not create any new contact when all info already exists")
        void noNewRows() {
            LocalDateTime t1 = LocalDateTime.of(2023, 4, 1, 0, 0);
            Contact primary = makePrimary(1L, "lorraine@hillvalley.edu", "123456", t1);

            when(contactRepository.findByEmailOrPhoneNumber("lorraine@hillvalley.edu", "123456"))
                    .thenReturn(List.of(primary));

            when(contactRepository.findAllByPrimaryId(1L))
                    .thenReturn(new ArrayList<>(List.of(primary)));

            IdentifyRequest req = new IdentifyRequest();
            req.setEmail("lorraine@hillvalley.edu");
            req.setPhoneNumber("123456");

            IdentifyResponse response = contactService.identify(req);

            assertThat(response.getContact().getPrimaryContactId()).isEqualTo(1L);
            assertThat(response.getContact().getSecondaryContactIds()).isEmpty();

            // No save should have been made (no new/modified rows)
            verify(contactRepository, never()).save(any(Contact.class));
        }

        @Test
        @DisplayName("returns consolidated response including existing secondaries")
        void returnsFullCluster() {
            LocalDateTime t1 = LocalDateTime.of(2023, 4, 1, 0, 0);
            Contact primary = makePrimary(1L, "lorraine@hillvalley.edu", "123456", t1);
            Contact sec = makeSecondary(23L, "mcfly@hillvalley.edu", "123456", 1L,
                    LocalDateTime.of(2023, 4, 20, 5, 30));

            when(contactRepository.findByEmailOrPhoneNumber("mcfly@hillvalley.edu", "123456"))
                    .thenReturn(List.of(primary, sec));

            when(contactRepository.findAllByPrimaryId(1L))
                    .thenReturn(new ArrayList<>(List.of(primary, sec)));

            IdentifyRequest req = new IdentifyRequest();
            req.setEmail("mcfly@hillvalley.edu");
            req.setPhoneNumber("123456");

            IdentifyResponse response = contactService.identify(req);

            assertThat(response.getContact().getPrimaryContactId()).isEqualTo(1L);
            assertThat(response.getContact().getEmails())
                    .containsExactly("lorraine@hillvalley.edu", "mcfly@hillvalley.edu");
            assertThat(response.getContact().getSecondaryContactIds()).containsExactly(23L);
        }
    }

    // ─── Case 4: Two-Primary Merge ───────────────────────────────────────────

    @Nested
    @DisplayName("Case 4: Two separate primaries → merge, older wins")
    class MergeTests {

        @Test
        @DisplayName("older primary wins; newer is demoted to secondary")
        void olderWins() {
            LocalDateTime t1 = LocalDateTime.of(2023, 4, 11, 0, 0);
            LocalDateTime t2 = LocalDateTime.of(2023, 4, 21, 5, 30);
            Contact older = makePrimary(11L, "george@hillvalley.edu", "919191", t1);
            Contact newer = makePrimary(27L, "biffsucks@hillvalley.edu", "717171", t2);

            when(contactRepository.findByEmailOrPhoneNumber("george@hillvalley.edu", "717171"))
                    .thenReturn(List.of(older, newer));

            when(contactRepository.findById(11L)).thenReturn(Optional.of(older));
            when(contactRepository.findById(27L)).thenReturn(Optional.of(newer));

            // newer has no existing secondaries
            when(contactRepository.findByLinkedIdAndDeletedAtIsNull(27L))
                    .thenReturn(Collections.emptyList());

            when(contactRepository.save(newer)).thenReturn(newer);

            // After merge, cluster fetched with the winner
            when(contactRepository.findAllByPrimaryId(11L))
                    .thenReturn(new ArrayList<>(List.of(older, newer)));

            IdentifyRequest req = new IdentifyRequest();
            req.setEmail("george@hillvalley.edu");
            req.setPhoneNumber("717171");

            IdentifyResponse response = contactService.identify(req);

            assertThat(response.getContact().getPrimaryContactId()).isEqualTo(11L);
            assertThat(response.getContact().getEmails())
                    .contains("george@hillvalley.edu", "biffsucks@hillvalley.edu");
            assertThat(response.getContact().getPhoneNumbers())
                    .contains("919191", "717171");
            assertThat(response.getContact().getSecondaryContactIds()).contains(27L);

            // Verify the newer primary was demoted
            assertThat(newer.getLinkPrecedence()).isEqualTo(LinkPrecedence.secondary);
            assertThat(newer.getLinkedId()).isEqualTo(11L);
        }

        @Test
        @DisplayName("secondaries of demoted primary are re-linked to winner")
        void secondariesReLinked() {
            LocalDateTime t1 = LocalDateTime.of(2023, 4, 1, 0, 0);
            LocalDateTime t2 = LocalDateTime.of(2023, 4, 10, 0, 0);
            Contact older = makePrimary(1L, "a@test.com", "111", t1);
            Contact newer = makePrimary(5L, "b@test.com", "222", t2);
            Contact newerSec = makeSecondary(6L, "b2@test.com", "222", 5L, t2.plusDays(1));

            when(contactRepository.findByEmailOrPhoneNumber("a@test.com", "222"))
                    .thenReturn(List.of(older, newer));

            when(contactRepository.findById(1L)).thenReturn(Optional.of(older));
            when(contactRepository.findById(5L)).thenReturn(Optional.of(newer));

            when(contactRepository.findByLinkedIdAndDeletedAtIsNull(5L))
                    .thenReturn(new ArrayList<>(List.of(newerSec)));

            when(contactRepository.saveAll(any())).thenReturn(List.of(newerSec));
            when(contactRepository.save(newer)).thenReturn(newer);

            when(contactRepository.findAllByPrimaryId(1L))
                    .thenReturn(new ArrayList<>(List.of(older, newer, newerSec)));

            IdentifyRequest req = new IdentifyRequest();
            req.setEmail("a@test.com");
            req.setPhoneNumber("222");

            IdentifyResponse response = contactService.identify(req);

            assertThat(response.getContact().getPrimaryContactId()).isEqualTo(1L);
            // newerSec should now point to older primary
            assertThat(newerSec.getLinkedId()).isEqualTo(1L);
        }
    }

    // ─── Edge Cases ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("whitespace-only email is treated as null (normalised)")
        void whitespaceEmail() {
            when(contactRepository.findByEmailOrPhoneNumber(any(), anyString()))
                    .thenReturn(Collections.emptyList());

            Contact saved = makePrimary(1L, null, "555", LocalDateTime.now());
            when(contactRepository.save(any(Contact.class))).thenReturn(saved);

            IdentifyRequest req = new IdentifyRequest();
            req.setEmail("   ");
            req.setPhoneNumber("555");

            IdentifyResponse response = contactService.identify(req);

            assertThat(response.getContact().getEmails()).isEmpty();
            assertThat(response.getContact().getPhoneNumbers()).containsExactly("555");
        }

        @Test
        @DisplayName("existing secondary contact resolves to its primary correctly")
        void secondaryResolvesToPrimary() {
            LocalDateTime t1 = LocalDateTime.of(2023, 4, 1, 0, 0);
            Contact primary = makePrimary(1L, "primary@test.com", "111", t1);
            Contact secondary = makeSecondary(2L, "secondary@test.com", "111", 1L, t1.plusDays(1));

            // Query returns the secondary
            when(contactRepository.findByEmailOrPhoneNumber(eq("secondary@test.com"), any()))
                    .thenReturn(List.of(secondary));

            when(contactRepository.findAllByPrimaryId(1L))
                    .thenReturn(new ArrayList<>(List.of(primary, secondary)));

            IdentifyRequest req = new IdentifyRequest();
            req.setEmail("secondary@test.com");

            IdentifyResponse response = contactService.identify(req);

            assertThat(response.getContact().getPrimaryContactId()).isEqualTo(1L);
            assertThat(response.getContact().getEmails())
                    .containsExactly("primary@test.com", "secondary@test.com");
        }
    }
}
