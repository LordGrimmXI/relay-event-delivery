package com.relay.event;

public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException() {
        super("Event not found");
    }
}