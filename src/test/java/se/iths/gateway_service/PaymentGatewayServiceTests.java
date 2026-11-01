package se.iths.gateway_service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import se.iths.gateway_service.service.PaymentGatewayService;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PaymentGatewayServiceTests {
    private HttpServer server;
    private PaymentGatewayService service;
    private int authStatus;
    private int paymentCalls;
    private String paymentBody;
    private String forwardedToken;
    private String paymentPath;
    private String paymentMethod;
    private final String secret = "internal-test-token-not-for-real-use-123456";

    // Startar lokala HTTP-svar för att kontrollera gatewayns faktiska anrop utan riktiga tjänster.
    @BeforeEach
    void setUp() throws Exception {
        authStatus = 200;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/me", exchange -> {
            var body = "{\"username\":\"verified-customer\",\"roles\":[\"ROLE_USER\"]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(authStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/internal/checkouts", exchange -> {
            paymentCalls++;
            paymentPath = exchange.getRequestURI().getPath();
            paymentMethod = exchange.getRequestMethod();
            paymentBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            forwardedToken = exchange.getRequestHeaders().getFirst("X-Internal-Service-Token");
            var body = "{\"orderId\":1,\"status\":\"PENDING_PAYMENT\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        var url = "http://127.0.0.1:" + server.getAddress().getPort();
        service = new PaymentGatewayService(url, url, secret);
    }

    // Stoppar testservern efter varje kontroll.
    @AfterEach
    void tearDown() { server.stop(0); }

    // Nekar anrop utan JWT innan någon betalning kan skapas.
    @Test
    void rejectsMissingAuthentication() {
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> service.create(UUID.randomUUID(), cart(), null)).getStatusCode().value());
        assertEquals(0, paymentCalls);
    }

    // Nekar en token som auth-service inte godkänner.
    @Test
    void rejectsInvalidAuthentication() {
        authStatus = 401;
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> service.create(UUID.randomUUID(), cart(), "Bearer invalid")).getStatusCode().value());
        assertEquals(0, paymentCalls);
    }

    // Kontrollerar att kundnamnet hämtas från auth och att endast gatewayns interna hemlighet skickas vidare.
    @Test
    void forwardsVerifiedCustomerAndServerSecret() {
        service.create(UUID.randomUUID(), cart(), "Bearer customer-token");
        assertEquals(1, paymentCalls);
        assertTrue(paymentBody.contains("verified-customer"));
        assertTrue(paymentBody.contains("productId"));
        assertFalse(paymentBody.contains("customer-token"));
        assertEquals(secret, forwardedToken);
    }

    // Skickar avbrottet som POST med endast verifierat kundnamn och den interna serverhemligheten.
    @Test
    void cancelsOnlyForVerifiedCustomer() {
        var id = UUID.randomUUID();
        service.cancel(id, "Bearer customer-token");
        assertEquals("POST", paymentMethod);
        assertEquals("/internal/checkouts/" + id + "/cancel", paymentPath);
        assertEquals("{\"customerName\":\"verified-customer\"}", paymentBody);
        assertEquals(secret, forwardedToken);
    }

    // Nekar avbrott innan payment-service nås om kundens token saknas eller är ogiltig.
    @Test
    void rejectsUnauthenticatedCancellation() {
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> service.cancel(UUID.randomUUID(), null)).getStatusCode().value());
        authStatus = 401;
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> service.cancel(UUID.randomUUID(), "Bearer invalid")).getStatusCode().value());
        assertEquals(0, paymentCalls);
    }

    // Skapar en varukorg utan kundnamn eller klientstyrt pris.
    private PaymentGatewayService.Cart cart() {
        return new PaymentGatewayService.Cart(List.of(new PaymentGatewayService.Item(42L, 2)));
    }
}
