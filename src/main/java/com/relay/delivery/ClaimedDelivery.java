package com.relay.delivery;

public record ClaimedDelivery(long id, long eventId, long destinationId, int attemptCount) {}