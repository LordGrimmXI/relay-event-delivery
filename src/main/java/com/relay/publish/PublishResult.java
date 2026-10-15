package com.relay.publish;

public record PublishResult(Status status, long eventId) {

    public enum Status {
        CREATED,
        DUPLICATE_SAME_PAYLOAD,
        DUPLICATE_DIFFERENT_PAYLOAD
    }
}