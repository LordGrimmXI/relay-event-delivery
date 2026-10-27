package com.relay.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AddressPolicyTest {

    private AddressPolicy policy;
    private AddressPolicy loopbackPolicy;

    @BeforeEach
    void setUp() {
        policy = new AddressPolicy(false);
        loopbackPolicy = new AddressPolicy(true);
    }

    @Test
    void publicAddressIsNotBlocked() {
        assertThat(policy.isBlockedAddress(ip("93.184.216.34"))).isFalse();
    }

    @Test
    void loopbackAddressesAreBlockedByDefault() {
        assertThat(policy.isBlockedAddress(ip("127.0.0.1"))).isTrue();
        assertThat(policy.isBlockedAddress(ip("::1"))).isTrue();
    }

    @Test
    void loopbackAddressesAreAllowedWhenSettingIsOn() {
        assertThat(loopbackPolicy.isBlockedAddress(ip("127.0.0.1"))).isFalse();
        assertThat(loopbackPolicy.isBlockedAddress(ip("::1"))).isFalse();
    }

    @Test
    void settingOnStillBlocksNonLoopbackAddresses() throws UnknownHostException {
        assertThat(loopbackPolicy.isBlockedAddress(InetAddress.getByName("10.0.0.5"))).isTrue(); // Private range
        assertThat(loopbackPolicy.isBlockedAddress(InetAddress.getByName("169.254.169.254"))).isTrue(); // Cloud metadata address
        assertThat(loopbackPolicy.isBlockedAddress(InetAddress.getByName("100.64.0.1"))).isTrue(); // Carrier-grade NAT
        assertThat(loopbackPolicy.isBlockedAddress(InetAddress.getByName("fc00::1"))).isTrue(); // IPv6 unique-local
    }

    @Test
    void hostnamesFollowTheSameFlag() {
        assertThat(policy.isBlockedHostname("localhost")).isTrue();
        assertThat(loopbackPolicy.isBlockedHostname("localhost")).isFalse();

        assertThat(loopbackPolicy.isBlockedHostname("service.localhost")).isTrue();
        assertThat(loopbackPolicy.isBlockedHostname("metadata.google.internal")).isTrue();
        assertThat(loopbackPolicy.isBlockedHostname("example.com")).isFalse();
    }

    private InetAddress ip(String literal) {
        try {
            return InetAddress.getByName(literal);
        } catch (UnknownHostException ex) {
            throw new IllegalStateException(ex);
        }
    }
}