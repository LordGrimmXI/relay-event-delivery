package com.relay.delivery;

import com.relay.delivery.DeliveryOutcome.Kind;

// The Day 14 success policy: which HTTP status codes mean "delivered",
// "try again later" and "never try again".
public final class ResponsePolicy {

    private ResponsePolicy() {
    }

    public static Kind classify(int status) {
        if (status >= 200 && status < 300) {
            return Kind.SUCCESS;
        }

        // 4xx means "your request is wrong", so retrying the same request
        // is pointless. 408 (timeout) and 429 (slow down) are the exceptions.
        if (status >= 400 && status < 500 && status != 408 && status != 429) {
            return Kind.PERMANENT_FAILURE;
        }

        // 3xx (we don't follow redirects), 5xx, 408, 429, and any odd code
        // we don't recognize: try again later.
        return Kind.RETRYABLE_FAILURE;
    }
}