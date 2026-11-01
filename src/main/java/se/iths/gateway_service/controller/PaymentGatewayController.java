package se.iths.gateway_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import se.iths.gateway_service.service.PaymentGatewayService;
import java.util.Map;
import java.util.UUID;

/**
 * Exponerar anrop för att skapa, hämta status för och avbryta kundens checkout.
 */
@RestController
@RequestMapping("/gateway/checkouts")
public class PaymentGatewayController {
    private final PaymentGatewayService service;

    /**
     * Tar emot tjänsten som verifierar identitet och vidarebefordrar betalningsanrop.
     *
     * Ingår i betalningsflödet.
     *
     * @param service hanterar kundens betalningsanrop
     */
    public PaymentGatewayController(PaymentGatewayService service) {
        this.service = service;
    }

    /**
     * Startar checkout utan att acceptera kundnamn eller priser från frontend.
     *
     * Ingår i betalningsflödet.
     *
     * @param id id för den efterfrågade posten eller reservationen
     * @param cart varukorgens produkter och antal
     * @param authorization Authorization-header med kundens Bearer-token
     * @return svaret från den skapade checkout-sessionen
     */
    @PostMapping("/{id}")
    public Map<?, ?> create(@PathVariable UUID id, @RequestBody PaymentGatewayService.Cart cart,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return service.create(id, cart, authorization);
    }

    /**
     * Läser orderstatus efter att användaren återvänt från Stripe.
     *
     * Ingår i betalningsflödet.
     *
     * @param id id för den efterfrågade posten eller reservationen
     * @param authorization Authorization-header med kundens Bearer-token
     * @return checkout-sessionens aktuella status
     */
    @GetMapping("/{id}")
    public Map<?, ?> status(@PathVariable UUID id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return service.status(id, authorization);
    }

    /**
     * Vidarebefordrar ett uttryckligt avbrott efter att tjänsten verifierat kundens identitet.
     *
     * Ingår i betalningsflödet.
     *
     * @param id id för den efterfrågade posten eller reservationen
     * @param authorization Authorization-header med kundens Bearer-token
     * @return checkout-sessionens aktuella status
     */
    @PostMapping("/{id}/cancel")
    public Map<?, ?> cancel(@PathVariable UUID id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return service.cancel(id, authorization);
    }

    /**
     * Bevarar relevanta klientfelsstatusar utan att läcka interna svar eller hemligheter.
     *
     * Ingår i betalningsflödet.
     *
     * @param exception felet som ska översättas till ett HTTP-svar
     * @return HTTP-svar med statuskod och eventuell svarskropp
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<String> remoteResponse(RestClientResponseException exception) {
        var status = exception.getStatusCode().is4xxClientError() ? exception.getStatusCode() : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body("Payment request could not be completed.");
    }

    /**
     * Returnerar ett tydligt fel om en beroende tjänst inte kan nås.
     * @return HTTP-svar med statuskod och eventuell svarskropp
     *
     * Ingår i betalningsflödet.
     */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<String> unavailable() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Payment service unavailable.");
    }
}
