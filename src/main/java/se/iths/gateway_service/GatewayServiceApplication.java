package se.iths.gateway_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Startar gatewayns Spring Boot-applikation.
 */
@SpringBootApplication
public class GatewayServiceApplication {

	/**
	 * Startar applikationen.
	 *
	 * @param args argument från kommandoraden
	 */
	public static void main(String[] args) {
		SpringApplication.run(GatewayServiceApplication.class, args);
	}

}
