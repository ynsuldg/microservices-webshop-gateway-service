package se.iths.gateway_service.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import se.iths.gateway_service.service.GatewayProxyService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Enda publika ingångspunkten mot systemets mikrotjänster. Tar emot anrop från frontend
 * och vidarebefordrar dem via {@link GatewayProxyService} till auth-service, product-service
 * eller order-service. Hanterar även själva autentiseringscykeln mot frontend sätter en
 * HttpOnly-cookie med JWT vid inloggning, läser den vid {@code /auth/me}, och raderar den
 * vid utloggning.
 */
@RestController
@RequestMapping("/gateway")
public class GatewayController {
    private final GatewayProxyService proxyService;
    private final ObjectMapper objectMapper;

    /**
     * Skapar GatewayController med de beroenden och inställningar som tjänsten behöver.
     *
     * @param proxyService vidarebefordrar API-anrop till mikrotjänsterna
     * @param objectMapper omvandlar JSON till och från Java-objekt
     */
    public GatewayController(GatewayProxyService proxyService, ObjectMapper objectMapper) {
        this.proxyService = proxyService;
        this.objectMapper = objectMapper;
    }

    /**
     * Loggar in en användare via auth-service och sätter JWT:n som en HttpOnly-cookie
     * i svaret till klienten. Om auth-service svarar med ett fel (icke-2xx) eller
     * en tom body vidarebefordras det svaret oförändrat, utan att någon cookie sätts.
     *
     * @param requestBody inloggningsuppgifter från klienten
     * @param request     används för att läsa klientens Content-Type-header
     * @return {@code 200 OK} med cookie satt vid lyckad inloggning, annars auth-services felsvar rakt av
     */
    @PostMapping("/auth/login")
    public ResponseEntity<String> login(@RequestBody String requestBody, HttpServletRequest request) {
        ResponseEntity<String> response = proxyService.login(
                requestBody, request.getHeader(HttpHeaders.CONTENT_TYPE));

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return response;
        }

        JsonNode json = objectMapper.readTree(response.getBody());
        ResponseCookie cookie = ResponseCookie.from("token", json.get("accessToken").asText())
                .httpOnly(true)
                .secure(false)       // true på https
                .sameSite("Lax")
                .path("/")
                .maxAge(json.get("expiresIn").asLong())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.getBody());
    }

    /**
     * Vidarebefordrar registrering utan att skapa en inloggningscookie.
     *
     * @param requestBody e-postadress och lösenord för det nya kontot
     * @param request används för att läsa Content-Type
     * @return auth-services registreringssvar eller felsvar
     */
    @PostMapping("/auth/register")
    public ResponseEntity<String> register(@RequestBody String requestBody, HttpServletRequest request) {
        return proxyService.register(requestBody, request.getHeader(HttpHeaders.CONTENT_TYPE));
    }

    /**
     * Returnerar information om den inloggade användaren, baserat på JWT läst ur gatewayns HttpOnly-cookie.
     *
     * @param token JWT från cookien {@code token}
     * @return {@code 401 Unauthorized} om ingen cookie finns, annars auth-services svar
     */
    @GetMapping("/auth/me")
    public ResponseEntity<String> me(@CookieValue(name = "token", required = false) String token) {
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return proxyService.me(token);
    }

    /**
     * Loggar ut användaren genom att radera gatewayns JWT-cookie.
     *
     * @return {@code 204 No Content} med en cookie satt att utgå omedelbart
     */
    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = ResponseCookie.from("token", "")
                .httpOnly(true).path("/").maxAge(0).build();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    /**
     * Hämtar en specifik produkt via product-service.
     *
     * @param id      produktens id
     * @param request används för att läsa klientens Authorization-header
     * @return product-services svar
     */
    @GetMapping("/products/{id}")
    public ResponseEntity<String> getSingleProduct(@PathVariable String id, HttpServletRequest request) {
        return proxyService.getProductById(id, request.getHeader(HttpHeaders.AUTHORIZATION));
    }

    /**
     * Hämtar samtliga produkter via product-service.
     *
     * @param request används för att läsa klientens Authorization-header
     * @return product-services svar
     */
    @GetMapping("/products")
    public ResponseEntity<String> getProducts(HttpServletRequest request) {
        return proxyService.getAllProducts(request.getHeader(HttpHeaders.AUTHORIZATION));
    }

    /**
     * Skapar en ny produkt via product-service.
     *
     * @param requestBody produktdata från klienten
     * @param request     används för att läsa klientens Authorization- och Content-Type-header
     * @return product-services svar
     */
    @PostMapping("/products")
    public ResponseEntity<String> createProduct(
            @RequestBody String requestBody,
            HttpServletRequest request) {
        return proxyService.createProduct(
                requestBody,
                request.getHeader(HttpHeaders.AUTHORIZATION),
                request.getHeader(HttpHeaders.CONTENT_TYPE));
    }

    /**
     * Skapar en ny order via order-service.
     *
     * @param requestBody orderdata från klienten
     * @param request     används för att läsa klientens Authorization- och Content-Type-header
     * @return order-services svar
     */
    @PostMapping("/orders")
    public ResponseEntity<String> createOrder(
            @RequestBody String requestBody,
            HttpServletRequest request) {
        return proxyService.createOrder(
                requestBody,
                request.getHeader(HttpHeaders.AUTHORIZATION),
                request.getHeader(HttpHeaders.CONTENT_TYPE));
    }
}
