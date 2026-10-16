package com.relay.destination;

public class InvalidDestinationUrlException extends RuntimeException {

    public InvalidDestinationUrlException() {
        super("Destination URL is not allowed");
    }
}
