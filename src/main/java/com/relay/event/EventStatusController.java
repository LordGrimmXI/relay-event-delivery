package com.relay.event;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
public class EventStatusController {

    private final EventStatusService eventStatusService;

    public EventStatusController(EventStatusService eventStatusService) {
        this.eventStatusService = eventStatusService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventStatusResponse> get(@PathVariable long id) {
        return ResponseEntity.ok(eventStatusService.get(id));
    }
}