package com.fluxkart.bitespeed;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test — verifies the Spring Boot application context loads
 * successfully with all beans wired correctly.
 */
@SpringBootTest
class BitespeedApplicationTests {

    @Test
    @DisplayName("Application context loads without errors")
    void contextLoads() {
        // If the context fails to load, this test will fail with a clear error.
    }
}
