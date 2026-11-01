package se.iths.gateway_service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import se.iths.gateway_service.service.GatewayProxyService;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class RegistrationProxyTests {
    // Verifierar registreringens sökväg, body och att auth-services status bevaras.
    @Test
    void forwardsRegistrationAndPreservesConflict() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var received = new java.util.concurrent.atomic.AtomicReference<String>();
        var authorization = new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/appusers", exchange -> {
            received.set(exchange.getRequestMethod() + " " + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "Username is already registered.".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(409, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var url = "http://127.0.0.1:" + server.getAddress().getPort();
            var proxy = new GatewayProxyService(url, url, url, HttpClient.newHttpClient());
            var response = proxy.register("{\"username\":\"new@example.com\",\"password\":\"password123\"}", "application/json");
            assertEquals(409, response.getStatusCode().value());
            assertEquals("Username is already registered.", response.getBody());
            assertTrue(received.get().startsWith("POST {"));
            assertTrue(received.get().contains("new@example.com"));
            assertNull(authorization.get());
        } finally {
            server.stop(0);
        }
    }
}
