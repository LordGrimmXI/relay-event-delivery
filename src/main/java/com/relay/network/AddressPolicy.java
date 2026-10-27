package com.relay.network;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// The one place that decides which hosts and addresses Relay refuses to contact.
// This is a first filter, not complete SSRF protection.
@Component
public class AddressPolicy {

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost",
            "metadata",
            "metadata.google.internal",
            "metadata.google"
    );

    private final boolean allowLoopback;

    public AddressPolicy(
            @Value("${relay.destinations.allow-loopback:false}") boolean allowLoopback) {
        this.allowLoopback = allowLoopback;
    }

    // Expects a host that is already lowercase with trailing dots removed.
    public boolean isBlockedHostname(String host) {
        // Dev-only: plain "localhost" is the single name the flag opens.
        if (allowLoopback && host.equals("localhost")) {
            return false;
        }

        return BLOCKED_HOSTS.contains(host)
                || host.endsWith(".localhost")
                || host.endsWith(".local");
    }

    public boolean isBlockedAddress(InetAddress address) {
        // Dev-only: loopback is the single address range the flag opens.
        if (allowLoopback && address.isLoopbackAddress()) {
            return false;
        }

        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isReservedIpv4(address)
                || isIpv6UniqueLocalAddress(address);
    }

    private boolean isReservedIpv4(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }

        byte[] bytes = address.getAddress();
        int first = bytes[0] & 0xFF;
        int second = bytes[1] & 0xFF;

        return first == 0                                           // 0.0.0.0/8
                || (first == 100 && second >= 64 && second <= 127)  // 100.64.0.0/10 (carrier-grade NAT)
                || (first == 198 && (second == 18 || second == 19)) // 198.18.0.0/15 (benchmarking)
                || first >= 240;                                    // 240.0.0.0/4 (reserved, incl. broadcast)
    }

    private boolean isIpv6UniqueLocalAddress(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }

        // IPv6 unique-local addresses use the fc00::/7 range.
        byte firstByte = address.getAddress()[0];

        return (firstByte & 0xFE) == 0xFC;
    }
}