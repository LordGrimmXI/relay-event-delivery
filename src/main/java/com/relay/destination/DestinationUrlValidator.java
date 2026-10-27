package com.relay.destination;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DestinationUrlValidator {

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost",
            "metadata",
            "metadata.google.internal",
            "metadata.google"
    );

    private static final Pattern IPV4_LITERAL = Pattern.compile("[0-9.]+");

    private final boolean allowLoopback;

    public DestinationUrlValidator(
            @Value("${relay.destinations.allow-loopback:false}") boolean allowLoopback) {
        this.allowLoopback = allowLoopback;
    }

    public boolean isAllowed(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return false;
        }

        try {
            URI uri = new URI(rawUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();

            // Require an absolute HTTP(S) URL with a hostname.
            if (!uri.isAbsolute() || scheme == null || host == null || uri.isOpaque()) {
                return false;
            }

            scheme = scheme.toLowerCase(Locale.ROOT);
            host = normalizeHost(host);

            if (host.isEmpty()) {
                return false;
            }

            if (!scheme.equals("http") && !scheme.equals("https")) {
                return false;
            }

            // Credentials inside URLs can conceal the real destination.
            if (uri.getRawUserInfo() != null) {
                return false;
            }

            // Reject invalid explicit ports.
            if (uri.getPort() == 0 || uri.getPort() > 65535) {
                return false;
            }

            // Dev-only: when enabled, loopback hosts skip the local-host checks below.
            // Everything else (private ranges, metadata hosts) is still checked.
            boolean loopbackAllowed = allowLoopback && isLoopbackHost(host);

            if (!loopbackAllowed) {
                // Reject localhost and local hostnames.
                if (BLOCKED_HOSTS.contains(host)
                        || host.endsWith(".localhost")
                        || host.endsWith(".local")) {
                    return false;
                }

                // Check IP literals without resolving ordinary DNS names.
                if (isIpLiteral(host) && isProhibitedIp(host)) {
                    return false;
                }
            }

            return true;

        } catch (URISyntaxException | IllegalArgumentException ex) {
            return false;
        }
    }

    private String normalizeHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);

        // "localhost." and "localhost" are the same host.
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    private boolean isLoopbackHost(String host) {
        return host.equals("localhost") || (isIpLiteral(host) && isLoopbackIp(host));
    }

    private boolean isLoopbackIp(String host) {
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (Exception ex) {
            // An unparseable literal is not loopback; the normal checks will reject it.
            return false;
        }
    }

    private boolean isIpLiteral(String host) {
        // IPv6 literals contain colons.
        // IPv4 literals contain only digits and dots.
        return host.contains(":") || IPV4_LITERAL.matcher(host).matches();
    }

    private boolean isProhibitedIp(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);

            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()
                    || isReservedIpv4(address)
                    || isIPv6UniqueLocalAddress(address);

        } catch (Exception ex) {
            // If a literal IP cannot be parsed, reject it.
            return true;
        }
    }

    private boolean isReservedIpv4(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }

        byte[] bytes = address.getAddress();
        int first = bytes[0] & 0xFF;
        int second = bytes[1] & 0xFF;

        return first == 0                                      // 0.0.0.0/8
                || (first == 100 && second >= 64 && second <= 127)  // 100.64.0.0/10 (carrier-grade NAT)
                || (first == 198 && (second == 18 || second == 19)) // 198.18.0.0/15 (benchmarking)
                || first >= 240;                               // 240.0.0.0/4 (reserved, incl. broadcast)
    }

    private boolean isIPv6UniqueLocalAddress(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }

        // IPv6 unique-local addresses use the fc00::/7 range.
        byte firstByte = address.getAddress()[0];

        return (firstByte & 0xFE) == 0xFC;
    }
}