package com.fluxkart.bitespeed;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Bitespeed Identity Reconciliation Service.
 * <p>
 * This Spring Boot application exposes a REST API that links customer
 * identities across multiple purchases, even when different contact
 * details are used.
 * </p>
 *
 * @author fl4nk3r
 * @version 1.0
 * @since 2026-03-01
 */
@SpringBootApplication
public class BitespeedApplication {

	/**
	 * Application entry point. Bootstraps the Spring context and starts
	 * the embedded web server.
	 *
	 * @param args command-line arguments passed to the application
	 */
	public static void main(String[] args) {
		SpringApplication.run(BitespeedApplication.class, args);
	}

}
