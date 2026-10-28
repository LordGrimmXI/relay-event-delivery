package com.relay.delivery;

public record DeliveryOutcome(Kind kind, Integer httpStatus, String error) {

    public enum Kind {
        SUCCESS,
        RETRYABLE_FAILURE,
        PERMANENT_FAILURE
    }

    public static DeliveryOutcome ofStatus(int status) {
        Kind kind = ResponsePolicy.classify(status);
        String error = kind == Kind.SUCCESS ? null : "HTTP " + status;

        return new DeliveryOutcome(kind, status, error);
    }

    public static DeliveryOutcome retryable(String error) {
        return new DeliveryOutcome(Kind.RETRYABLE_FAILURE, null, error);
    }

    public static DeliveryOutcome permanent(String error) {
        return new DeliveryOutcome(Kind.PERMANENT_FAILURE, null, error);
    }
}