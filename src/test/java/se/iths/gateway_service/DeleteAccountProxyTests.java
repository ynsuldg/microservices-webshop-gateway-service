package se.iths.gateway_service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import se.iths.gateway_service.service.GatewayProxyService;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class DeleteAccountProxyTests {
    // Vidarebefordrar JWT och lösenord till det egna kontots endpoint och bevarar auth-felet.
    @Test
    void forwardsAuthenticatedDeleteAndPreservesUnauthorized() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var method = new AtomicReference<String>();
        var body = new AtomicReference<String>();
        var token = new AtomicReference<String>();
        server.createContext("/appusers/me", exchange -> {
            method.set(exchange.getRequestMethod());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            token.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.start();
        try {
            var url = "http://127.0.0.1:" + server.getAddress().getPort();
            var proxy = new GatewayProxyService(url, url, url, HttpClient.newHttpClient());
            var response = proxy.deleteAccount("{\"password\":\"password123\"}", "Bearer owner-token", "application/json");
            assertEquals("DELETE", method.get());
            assertEquals("{\"password\":\"password123\"}", body.get());
            assertEquals("Bearer owner-token", token.get());
            assertEquals(401, response.getStatusCode().value());
        } finally {
            server.stop(0);
        }
    }
}
