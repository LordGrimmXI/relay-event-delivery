package com.relay.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class SafeDnsResolverTest {

    @Test
    void publicAddressIsReturned() throws Exception {
        SafeDnsResolver resolver = new SafeDnsResolver(
                new AddressPolicy(false),
                host -> new InetAddress[] {ip("93.184.216.34")});

        InetAddress[] result = resolver.resolve("hooks.example.com");

        assertThat(result).containsExactly(ip("93.184.216.34"));
    }

    @Test
    void blockedAddressRejectsTheName() {
        SafeDnsResolver resolver = new SafeDnsResolver(
                new AddressPolicy(false),
                host -> new InetAddress[] {ip("10.0.0.5")});

        assertThatThrownBy(() -> resolver.resolve("hooks.example.com"))
                .isInstanceOf(BlockedAddressException.class);
    }

    @Test
    void oneBlockedAddressAmongPublicOnesRejectsTheWholeAnswer() {
        SafeDnsResolver resolver = new SafeDnsResolver(
            new AddressPolicy(false),
            host -> new InetAddress[] {ip("93.184.216.34"), ip("127.0.0.1")});

        assertThatThrownBy(() -> resolver.resolve("hooks.example.com"))
            .isInstanceOf(BlockedAddressException.class);
    }

    @Test
    void blockedHostnameIsRejectedWithoutAnyLookup() {
        AtomicInteger lookups = new AtomicInteger();
        SafeDnsResolver resolver = new SafeDnsResolver(
                new AddressPolicy(false),
                host -> {
                    lookups.incrementAndGet();
                    return new InetAddress[] {ip("93.184.216.34")};
                });

        assertThatThrownBy(() -> resolver.resolve("localhost"))
                .isInstanceOf(BlockedAddressException.class);
        assertThat(lookups.get()).isZero();
    }

    @Test
    void trailingDotDoesNotBypassTheHostnameRule() {
        SafeDnsResolver resolver = new SafeDnsResolver(
                new AddressPolicy(false),
                host -> new InetAddress[] {ip("93.184.216.34")});

        assertThatThrownBy(() -> resolver.resolve("localhost."))
                .isInstanceOf(BlockedAddressException.class);
    }

    @Test
    void loopbackIsAllowedWhenSettingIsOn() throws Exception {
        SafeDnsResolver resolver = new SafeDnsResolver(
                new AddressPolicy(true),
                host -> new InetAddress[] {ip("127.0.0.1")});

        InetAddress[] result = resolver.resolve("localhost");

        assertThat(result).containsExactly(ip("127.0.0.1"));
    }

    @Test
    void emptyAnswerIsAnOrdinaryLookupFailure() {
        SafeDnsResolver resolver = new SafeDnsResolver(
                new AddressPolicy(false),
                host -> new InetAddress[0]);

        assertThatThrownBy(() -> resolver.resolve("hooks.example.com"))
                .isInstanceOf(UnknownHostException.class)
                .isNotInstanceOf(BlockedAddressException.class);
    }

    private InetAddress ip(String literal) {
        try {
            return InetAddress.getByName(literal);
        } catch (UnknownHostException ex) {
            throw new IllegalStateException(ex);
        }
    }
}