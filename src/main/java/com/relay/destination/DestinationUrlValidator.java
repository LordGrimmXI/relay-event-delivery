package com.relay.destination;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class DestinationUrlValidator {

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost",
            "metadata",
            "metadata.google.internal",
            "metadata.google"
    );

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
            host = host.toLowerCase(Locale.ROOT);

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

            return true;

        } catch (URISyntaxException | IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean isIpLiteral(String host) {
        // IPv6 literals contain colons.
        // IPv4 literals contain only digits and dots.
        return host.contains(":") || host.matches("[0-9.]+");
    }

    private boolean isProhibitedIp(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);

            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()
                    || isIPv6UniqueLocalAddress(address);

        } catch (Exception ex) {
            // If a literal IP cannot be parsed, reject it.
            return true;
        }
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
