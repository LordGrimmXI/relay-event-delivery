package com.relay.destination;

public class DestinationAlreadyExistsException extends RuntimeException {

    public DestinationAlreadyExistsException() {
        super("A destination with this name already exists");
    }
}
