package se.iths.gateway_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Verifierar kundens JWT hos auth-service innan betalningsanrop skickas vidare internt. */
@Service
public class PaymentGatewayService {
    private final RestClient auth;
    private final RestClient payment;
    private final String token;
    public record User(String username, List<String> roles) { }
    public record Item(Long productId, int quantity) { }
    public record Cart(List<Item> items) { }

    // Skapar separata klienter för autentisering och betalning utan att exponera serverhemligheten.
    public PaymentGatewayService(@Value("${app.auth-service-url}") String authUrl,
                                 @Value("${app.payment-service-url}") String paymentUrl,
                                 @Value("${internal.service-token:}") String token) {
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.auth = RestClient.builder().baseUrl(authUrl).requestFactory(factory).build();
        this.payment = RestClient.builder().baseUrl(paymentUrl).requestFactory(factory).build();
        this.token = token;
    }

    // Hämtar kundidentiteten från verifierad JWT och skickar endast produkt-id:n och antal vidare.
    public Map<?, ?> create(UUID id, Cart cart, String authorization) {
        var user = verifyCustomer(authorization);
        checkToken();
        if (cart == null || cart.items() == null || cart.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty.");
        }
        return payment.post().uri("/internal/checkouts/{id}", id)
                .header("X-Internal-Service-Token", token)
                .body(Map.of("customerName", user.username(), "items", cart.items()))
                .retrieve().body(Map.class);
    }

    // Hämtar endast betalningsstatus för den verifierade kunden.
    public Map<?, ?> status(UUID id, String authorization) {
        var user = verifyCustomer(authorization);
        checkToken();
        return payment.get().uri(builder -> builder.path("/internal/checkouts/{id}/status")
                        .queryParam("customerName", user.username()).build(id))
                .header("X-Internal-Service-Token", token).retrieve().body(Map.class);
    }

    // Verifierar token hos auth-service i stället för att lita på kundnamn eller JWT-innehåll från klienten.
    private User verifyCustomer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        User user;
        try {
            user = auth.get().uri("/auth/me").header("Authorization", authorization).retrieve().body(User.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Authentication service unavailable.");
        }
        if (user == null || user.username() == null || user.username().isBlank() || user.roles() == null ||
                user.roles().stream().noneMatch(role -> role.equals("ROLE_USER") || role.equals("ROLE_ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return user;
    }

    // Nekar betalningsanrop om den interna serverhemligheten inte har konfigurerats.
    private void checkToken() {
        if (token.length() < 32) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Internal payment access is not configured.");
        }
    }
}
