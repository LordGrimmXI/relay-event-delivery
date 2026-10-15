package com.relay.publish;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.relay.common.ApiError;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/events")
public class EventController {
    private final PublishService publishService;

    public EventController(PublishService publishService) {
        this.publishService = publishService;
    }

    @PostMapping
    public ResponseEntity<?> publish(@Valid @RequestBody PublishEventRequest request) {
        PublishResult result = publishService.publish(
                request.eventType(),
                request.idempotencyKey(),
                request.payload().toString());

        return switch (result.status()) {
            case CREATED -> ResponseEntity.status(HttpStatus.CREATED)
                    .body(new PublishEventResponse(result.eventId()));

            case DUPLICATE_SAME_PAYLOAD -> ResponseEntity.ok(
                    new PublishEventResponse(result.eventId()));
            
            case DUPLICATE_DIFFERENT_PAYLOAD -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("Idempotency key already used with a different payload", List.of()));
        };
    }
}