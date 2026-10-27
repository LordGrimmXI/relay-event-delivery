package com.relay.destination;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.relay.network.AddressPolicy;

@Component
public class DestinationUrlValidator {

    private static final Pattern IPV4_LITERAL = Pattern.compile("[0-9.]+");

    private final AddressPolicy addressPolicy;

    public DestinationUrlValidator(AddressPolicy addressPolicy) {
        this.addressPolicy = addressPolicy;
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

            // Reject localhost, metadata and local hostnames.
            if (addressPolicy.isBlockedHostname(host)) {
                return false;
            }

            // Check IP literals without resolving ordinary DNS names.
            if (isIpLiteral(host) && isBlockedLiteral(host)) {
                return false;
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

    private boolean isIpLiteral(String host) {
        // IPv6 literals contain colons.
        // IPv4 literals contain only digits and dots.
        return host.contains(":") || IPV4_LITERAL.matcher(host).matches();
    }

    private boolean isBlockedLiteral(String host) {
        try {
            return addressPolicy.isBlockedAddress(InetAddress.getByName(host));
        } catch (Exception ex) {
            // If a literal IP cannot be parsed, reject it.
            return true;
        }
    }
}