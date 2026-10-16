package com.relay.destination;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/destinations/{destinationId}/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    public ResponseEntity<Void> subscribe(
            @PathVariable Long destinationId,
            @Valid @RequestBody CreateSubscriptionRequest request) {

        subscriptionService.subscribe(destinationId, request.eventType());

        return ResponseEntity.noContent().build();
    }
}
