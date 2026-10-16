package com.relay.destination;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/destinations")
public class DestinationController {

    private final DestinationService destinationService;

    public DestinationController(DestinationService destinationService) {
        this.destinationService = destinationService;
    }

    @PostMapping
    public ResponseEntity<Destination> create(@Valid @RequestBody CreateDestinationRequest request) {

        Destination destination = destinationService.create(
                request.name(),
                request.url()
        );

        return ResponseEntity.created(URI.create("/destinations/" + destination.id()))
                .body(destination);
    }
    
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        destinationService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
