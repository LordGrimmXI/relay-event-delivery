package com.relay.destination;

public class DuplicateSubscriptionException extends RuntimeException {
    
    public DuplicateSubscriptionException() {
        super("Destination is already subscribed to this event type");
    }
}
