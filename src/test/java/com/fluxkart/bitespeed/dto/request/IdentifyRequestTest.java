package com.fluxkart.bitespeed.dto.request;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Unit tests for the {@link IdentifyRequest} DTO.
 * Validates getters/setters, equality, and the custom bean-validation
 * constraint requiring at least one of email or phoneNumber.
 */
class IdentifyRequestTest {

    private final Validator validator;

    IdentifyRequestTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            this.validator = factory.getValidator();
        }
    }

    // ─── Getters & Setters ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Getters & Setters")
    class GetterSetterTests {

        @Test
        @DisplayName("getEmail / setEmail round-trips correctly")
        void testEmail() {
            IdentifyRequest req = new IdentifyRequest();
            req.setEmail("doc@hillvalley.edu");
            assertThat(req.getEmail()).isEqualTo("doc@hillvalley.edu");
        }

        @Test
        @DisplayName("getPhoneNumber / setPhoneNumber round-trips correctly")
        void testPhoneNumber() {
            IdentifyRequest req = new IdentifyRequest();
            req.setPhoneNumber("123456");
            assertThat(req.getPhoneNumber()).isEqualTo("123456");
        }

        @Test
        @DisplayName("default values are null")
        void testDefaults() {
            IdentifyRequest req = new IdentifyRequest();
            assertThat(req.getEmail()).isNull();
            assertThat(req.getPhoneNumber()).isNull();
        }
    }

    // ─── Validation Logic ────────────────────────────────────────────────────

    @Nested
    @DisplayName("isAtLeastOneFieldPresent() validation")
    class ValidationTests {

        @Test
        @DisplayName("both email and phone present → valid")
        void bothPresent_shouldBeValid() {
            IdentifyRequest req = new IdentifyRequest();
            req.setEmail("test@example.com");
            req.setPhoneNumber("555");

            assertThat(req.isAtLeastOneFieldPresent()).isTrue();
            Set<ConstraintViolation<IdentifyRequest>> violations = validator.validate(req);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("only email present → valid")
        void onlyEmail_shouldBeValid() {
            IdentifyRequest req = new IdentifyRequest();
            req.setEmail("test@example.com");

            assertThat(req.isAtLeastOneFieldPresent()).isTrue();
            Set<ConstraintViolation<IdentifyRequest>> violations = validator.validate(req);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("only phone present → valid")
        void onlyPhone_shouldBeValid() {
            IdentifyRequest req = new IdentifyRequest();
            req.setPhoneNumber("555");

            assertThat(req.isAtLeastOneFieldPresent()).isTrue();
            Set<ConstraintViolation<IdentifyRequest>> violations = validator.validate(req);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("both null → invalid")
        void bothNull_shouldBeInvalid() {
            IdentifyRequest req = new IdentifyRequest();

            assertThat(req.isAtLeastOneFieldPresent()).isFalse();
            Set<ConstraintViolation<IdentifyRequest>> violations = validator.validate(req);
            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage())
                    .isEqualTo("At least one of email or phoneNumber must be provided");
        }

        @Test
        @DisplayName("blank email and null phone → invalid")
        void blankEmailNullPhone_shouldBeInvalid() {
            IdentifyRequest req = new IdentifyRequest();
            req.setEmail("   ");

            assertThat(req.isAtLeastOneFieldPresent()).isFalse();
        }

        @Test
        @DisplayName("null email and blank phone → invalid")
        void nullEmailBlankPhone_shouldBeInvalid() {
            IdentifyRequest req = new IdentifyRequest();
            req.setPhoneNumber("");

            assertThat(req.isAtLeastOneFieldPresent()).isFalse();
        }

        @Test
        @DisplayName("both blank → invalid")
        void bothBlank_shouldBeInvalid() {
            IdentifyRequest req = new IdentifyRequest();
            req.setEmail("");
            req.setPhoneNumber("  ");

            assertThat(req.isAtLeastOneFieldPresent()).isFalse();
        }
    }

    // ─── Equals, HashCode, ToString ──────────────────────────────────────────

    @Nested
    @DisplayName("Equals, HashCode, ToString")
    class EqualityTests {

        @Test
        @DisplayName("equal objects have same hashCode")
        void testEqualsAndHashCode() {
            IdentifyRequest a = new IdentifyRequest();
            a.setEmail("a@b.com");
            a.setPhoneNumber("111");

            IdentifyRequest b = new IdentifyRequest();
            b.setEmail("a@b.com");
            b.setPhoneNumber("111");

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("different objects are not equal")
        void testNotEquals() {
            IdentifyRequest a = new IdentifyRequest();
            a.setEmail("a@b.com");

            IdentifyRequest b = new IdentifyRequest();
            b.setEmail("z@y.com");

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("toString includes field values")
        void testToString() {
            IdentifyRequest req = new IdentifyRequest();
            req.setEmail("test@test.com");
            req.setPhoneNumber("555");

            String str = req.toString();
            assertThat(str).contains("test@test.com");
            assertThat(str).contains("555");
        }
    }
}
