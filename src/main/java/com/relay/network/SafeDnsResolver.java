package com.relay.network;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

import org.apache.hc.client5.http.DnsResolver;

// Resolves names for the HTTP client and refuses to return blocked addresses,
// so the addresses we checked are the ones the client connects to.
public class SafeDnsResolver implements DnsResolver {

    @FunctionalInterface
    public interface HostLookup {
        InetAddress[] lookup(String host) throws UnknownHostException;
    }

    private final AddressPolicy addressPolicy;
    private final HostLookup hostLookup;

    public SafeDnsResolver(AddressPolicy addressPolicy) {
        this(addressPolicy, InetAddress::getAllByName);
    }

    public SafeDnsResolver(AddressPolicy addressPolicy, HostLookup hostLookup) {
        this.addressPolicy = addressPolicy;
        this.hostLookup = hostLookup;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        String normalized = normalizeHost(host);

        if (addressPolicy.isBlockedHostname(normalized)) {
            throw new BlockedAddressException();
        }

        InetAddress[] addresses = hostLookup.lookup(normalized);

        if (addresses == null || addresses.length == 0) {
            throw new UnknownHostException("no addresses found for destination");
        }

        // One blocked address rejects the whole answer.
        for (InetAddress address : addresses) {
            if (addressPolicy.isBlockedAddress(address)) {
                throw new BlockedAddressException();
            }
        }

        return addresses;
    }

    // Only used for canonical-name lookups (reverse DNS). We return the name
    // unchanged so Relay never performs a reverse lookup.
    @Override
    public String resolveCanonicalHostname(String host) {
        return normalizeHost(host);
    }

    private String normalizeHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);

        // "localhost." and "localhost" are the same host.
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }
}