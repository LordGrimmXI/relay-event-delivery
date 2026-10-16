package com.relay.destination;

public class DestinationNotFoundException extends RuntimeException {
    
    public DestinationNotFoundException() {
        super("Destination not found");
    }
}
