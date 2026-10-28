package com.relay.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.relay.delivery.DeliveryOutcome.Kind;

class ResponsePolicyTest {

    @Test
    void successCodesAreSuccess() {
        assertThat(ResponsePolicy.classify(200)).isEqualTo(Kind.SUCCESS);
        assertThat(ResponsePolicy.classify(202)).isEqualTo(Kind.SUCCESS);
        assertThat(ResponsePolicy.classify(204)).isEqualTo(Kind.SUCCESS);
    }

    @Test
    void redirectsAndServerErrorsAreRetryable() {
        assertThat(ResponsePolicy.classify(301)).isEqualTo(Kind.RETRYABLE_FAILURE);
        assertThat(ResponsePolicy.classify(302)).isEqualTo(Kind.RETRYABLE_FAILURE);
        assertThat(ResponsePolicy.classify(500)).isEqualTo(Kind.RETRYABLE_FAILURE);
        assertThat(ResponsePolicy.classify(503)).isEqualTo(Kind.RETRYABLE_FAILURE);
    }

    @Test
    void timeoutAndRateLimitAreRetryable() {
        assertThat(ResponsePolicy.classify(408)).isEqualTo(Kind.RETRYABLE_FAILURE);
        assertThat(ResponsePolicy.classify(429)).isEqualTo(Kind.RETRYABLE_FAILURE);
    }

    @Test
    void otherClientErrorsArePermanent() {
        assertThat(ResponsePolicy.classify(400)).isEqualTo(Kind.PERMANENT_FAILURE);
        assertThat(ResponsePolicy.classify(401)).isEqualTo(Kind.PERMANENT_FAILURE);
        assertThat(ResponsePolicy.classify(404)).isEqualTo(Kind.PERMANENT_FAILURE);
        assertThat(ResponsePolicy.classify(410)).isEqualTo(Kind.PERMANENT_FAILURE);
    }

    @Test
    void unrecognizedCodesAreRetryable() {
        assertThat(ResponsePolicy.classify(100)).isEqualTo(Kind.RETRYABLE_FAILURE);
        assertThat(ResponsePolicy.classify(600)).isEqualTo(Kind.RETRYABLE_FAILURE);
    }
}