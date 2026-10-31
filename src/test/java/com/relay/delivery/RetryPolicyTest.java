package com.relay.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    @Test
    void delaysDoubleEachAttempt() {
        RetryPolicy policy = policy(8, 1.0);

        assertThat(policy.delaySeconds(1)).isEqualTo(30);
        assertThat(policy.delaySeconds(2)).isEqualTo(60);
        assertThat(policy.delaySeconds(3)).isEqualTo(120);
        assertThat(policy.delaySeconds(4)).isEqualTo(240);
    }

    @Test
    void delayIsCappedAtOneHour() {
        RetryPolicy policy = policy(100, 1.0);

        assertThat(policy.delaySeconds(10)).isEqualTo(3600);
        assertThat(policy.delaySeconds(50)).isEqualTo(3600);
    }

    @Test
    void jitterShrinksTheDelayByUpToHalf() {
        assertThat(policy(8, 0.0).delaySeconds(1)).isEqualTo(15);

        assertThat(policy(8, 0.0).delaySeconds(3)).isEqualTo(60);
        assertThat(policy(8, 0.5).delaySeconds(3)).isEqualTo(90);
    }

    @Test
    void exhaustedAtTheLimitAndAfter() {
        RetryPolicy policy = policy(3, 1.0);

        assertThat(policy.isExhausted(2)).isFalse();
        assertThat(policy.isExhausted(3)).isTrue();
        assertThat(policy.isExhausted(4)).isTrue();
    }

    private RetryPolicy policy(int maxAttempts, double random) {
        return new RetryPolicy(maxAttempts, () -> random);
    }
}