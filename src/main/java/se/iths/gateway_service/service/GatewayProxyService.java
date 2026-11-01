package se.iths.gateway_service.service;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Vidarebefordrar HTTP-anrop från gatewayn till rätt mikrotjänst. Varje publik metod motsvarar
 * ett specifikt anrop mot en tjänst och bygger vidare på de generiska hjälpmetoderna
 * {@link #forwardGet} och {@link #forwardPost}, som i sin tur delar logik via {@link #send}.
 */
@Service
public class GatewayProxyService {

    private final String authServiceUrl;
    private final String productServiceUrl;
    private final String orderServiceUrl;
    private final HttpClient httpClient;


    /**
     * Skapar GatewayProxyService med de beroenden och inställningar som tjänsten behöver.
     *
     * @param authServerUrl basadress till auth-service
     * @param productServiceUrl basadress till product-service
     * @param orderServiceUrl basadress till order-service
     * @param httpClient HTTP-klient för vidarebefordrade anrop
     */
    public GatewayProxyService(
            @Value("${app.auth-service-url}") String authServerUrl,
            @Value("${app.product-service-url}") String productServiceUrl,
            @Value("${app.order-service-url}") String orderServiceUrl,
            HttpClient httpClient) {
        this.authServiceUrl = authServerUrl;
        this.productServiceUrl = productServiceUrl;
        this.orderServiceUrl = orderServiceUrl;
        this.httpClient = httpClient;
    }

    /**
     * Skapar ett användarkonto via auth-service utan att vidarebefordra någon JWT.
     *
     * @param requestBody registreringsuppgifter från klienten
     * @param contentType klientens Content-Type
     * @return auth-services svar med samma HTTP-status
     */
    public ResponseEntity<String> register(String requestBody, String contentType) {
        return forwardPost(authServiceUrl, "/appusers", requestBody, null, contentType);
    }

    /**
     * Skickar DELETE till den egna användarens konto hos auth-service.
     *
     * @param requestBody lösenordet för att bekräfta radering
     * @param authorization användarens Bearer-token
     * @param contentType klientens Content-Type
     * @return auth-services svar eller ett anslutningsfel
     */
    public ResponseEntity<String> deleteAccount(String requestBody, String authorization, String contentType) {
        var builder = HttpRequest.newBuilder(URI.create(authServiceUrl.replaceAll("/+$", "") + "/appusers/me"))
                .method("DELETE", HttpRequest.BodyPublishers.ofString(requestBody));
        if (contentType != null && !contentType.isBlank()) {
            builder.header(HttpHeaders.CONTENT_TYPE, contentType);
        }
        return send(builder, authorization);
    }

    /**
     * Vidarebefordrar ett inloggningsförsök till auth-service.
     *
     * @param requestBody oförändrad request-body från klienten
     * @param contentType klientens ursprungliga Content-Type-header
     * @return auth-services svar
     */
    public ResponseEntity<String> login(String requestBody, String contentType) {
        return forwardPost(
                authServiceUrl,
                "/auth/login",
                requestBody,
                null,
                contentType
        );
    }

    /**
     * Hämtar en specifik produkt från product-service.
     *
     * @param id            produktens id
     * @param authorization klientens Authorization-header
     * @return product-services svar
     */
    public ResponseEntity<String> getProductById(String id, String authorization) {
        return forwardGet(productServiceUrl, "/products/" + id, authorization);
    }

    /**
     * Hämtar samtliga produkter från product-service.
     *
     * @param authorization klientens Authorization-header
     * @return product-services svar
     */
    public ResponseEntity<String> getAllProducts(String authorization) {
        return forwardGet(productServiceUrl, "/products", authorization);
    }

    /**
     * Vidarebefordrar skapande av en produkt till product-service.
     *
     * @param requestBody   oförändrad produktdata från klienten
     * @param authorization klientens Authorization-header
     * @param contentType   klientens ursprungliga Content-Type-header
     * @return product-services svar
     */
    public ResponseEntity<String> createProduct(
            String requestBody,
            String authorization,
            String contentType) {
        return forwardPost(productServiceUrl, "/products", requestBody, authorization, contentType);
    }

    /**
     * Vidarebefordrar skapande av en order till order-service.
     *
     * @param requestBody   oförändrad orderdata från klienten
     * @param authorization klientens Authorization-header
     * @param contentType   klientens ursprungliga Content-Type-header
     * @return order-services svar
     */
    public ResponseEntity<String> createOrder(
            String requestBody,
            String authorization,
            String contentType) {
        return forwardPost(orderServiceUrl, "/orders", requestBody, authorization, contentType);
    }

    /**
     * Hämtar information om den inloggade användaren från auth-service, baserat på
     * JWT läst ur gatewayns HttpOnly-cookie.
     *
     * @param token JWT token
     * @return auth-services svar
     */
    public ResponseEntity<String> me(String token) {
        return forwardGet(authServiceUrl, "/auth/me", "Bearer " + token);
    }

    /**
     * Bygger och skickar en POST-request mot angiven tjänst.
     *
     * @param baseUrl       bas-URL till tjänsten
     * @param path          path på tjänsten som anropas
     * @param requestBody   body som skickas oförändrad vidare
     * @param authorization Authorization-header att vidarebefordra
     * @param contentType   Content-Type-header att vidarebefordra
     * @return svaret från tjänsten
     */
    private ResponseEntity<String> forwardPost(
            String baseUrl,
            String path,
            String requestBody,
            String authorization,
            String contentType) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create(baseUrl.replaceAll("/+$", "") + path))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));

        if (contentType != null && !contentType.isBlank()) {
            builder.header(HttpHeaders.CONTENT_TYPE, contentType);
        }

        return send(builder, authorization);
    }

    /**
     * Bygger och skickar en GET-request mot angiven tjänst.
     *
     * @param baseUrl       bas-URL till tjänsten
     * @param path          path på tjänsten som anropas
     * @param authorization Authorization-header att vidarebefordra
     * @return svaret från tjänsten
     */
    private ResponseEntity<String> forwardGet(
            String baseUrl,
            String path,
            String authorization) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/+$", "") + path)).GET();

        return send(builder, authorization);
    }

    /**
     * Skickar en förberedd request mot en tjänst och översätter svaret till en {@link ResponseEntity}.
     *
     * @param builder       requesten som ska skickas, eventuellt utan Authorization-header satt ännu
     * @param authorization Authorization-header att sätta innan requesten skickas
     * @return svaret från tjänsten
     */
    private ResponseEntity<String> send(HttpRequest.Builder builder, String authorization) {
        if (authorization != null && !authorization.isBlank()) {
            builder.header(
                    HttpHeaders.AUTHORIZATION,
                    authorization
            );
        }
        try {
            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            ResponseEntity.BodyBuilder result =
                    ResponseEntity.status(response.statusCode());

            response.headers().firstValue(HttpHeaders.CONTENT_TYPE)
                    .ifPresent(value -> result.header(
                            HttpHeaders.CONTENT_TYPE, value
                    ));
            return result.body(response.body());
        } catch (IOException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("Could not reach service");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Interrupted");
        }

    }
}
