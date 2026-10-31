package se.iths.gateway_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import se.iths.gateway_service.service.PaymentGatewayService;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/gateway/checkouts")
public class PaymentGatewayController {
    private final PaymentGatewayService service;

    // Tar emot tjänsten som verifierar identitet och vidarebefordrar betalningsanrop.
    public PaymentGatewayController(PaymentGatewayService service) {
        this.service = service;
    }

    // Startar checkout utan att acceptera kundnamn eller priser från frontend.
    @PostMapping("/{id}")
    public Map<?, ?> create(@PathVariable UUID id, @RequestBody PaymentGatewayService.Cart cart,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return service.create(id, cart, authorization);
    }

    // Läser orderstatus efter att användaren återvänt från Stripe.
    @GetMapping("/{id}")
    public Map<?, ?> status(@PathVariable UUID id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return service.status(id, authorization);
    }

    // Bevarar relevanta klientfelsstatusar utan att läcka interna svar eller hemligheter.
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<String> remoteResponse(RestClientResponseException exception) {
        var status = exception.getStatusCode().is4xxClientError() ? exception.getStatusCode() : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body("Payment request could not be completed.");
    }

    // Returnerar ett tydligt fel om en beroende tjänst inte kan nås.
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<String> unavailable() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Payment service unavailable.");
    }
}
