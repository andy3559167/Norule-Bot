package com.norule.musicbot.domain.music;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class AudioUrlSafetyValidator {
    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost",
            "localhost.localdomain",
            "metadata.google.internal"
    );

    @FunctionalInterface
    public interface HostResolver {
        List<InetAddress> resolve(String host) throws UnknownHostException;
    }

    public enum Status {
        ALLOWED,
        INVALID_URL,
        UNSUPPORTED_SCHEME,
        INVALID_HOST,
        CREDENTIALS_NOT_ALLOWED,
        PORT_NOT_ALLOWED,
        HOST_NOT_ALLOWED,
        BLOCKED_HOST,
        BLOCKED_ADDRESS,
        DNS_FAILURE
    }

    public record Validation(Status status, String detail) {
        public boolean allowed() {
            return status == Status.ALLOWED;
        }
    }

    private final Set<String> allowedHosts;
    private final HostResolver resolver;

    public AudioUrlSafetyValidator(Set<String> allowedHosts, HostResolver resolver) {
        this.allowedHosts = normalizeAllowedHosts(allowedHosts);
        this.resolver = resolver == null ? systemResolver() : resolver;
    }

    public static HostResolver systemResolver() {
        return host -> List.of(InetAddress.getAllByName(host));
    }

    public Validation validate(URI uri) {
        Validation structure = validateStructure(uri);
        if (!structure.allowed()) {
            return structure;
        }
        String host = normalizeHost(uri.getHost());
        try {
            return validateResolvedHost(host, resolver.resolve(host));
        } catch (UnknownHostException ignored) {
            return new Validation(Status.DNS_FAILURE, "host resolution failed");
        }
    }

    public Validation validateRedirect(URI uri) {
        return validate(uri);
    }

    public Validation validateStructure(URI uri) {
        Validation boundary = validateNetworkBoundary(uri);
        if (!boundary.allowed()) {
            return boundary;
        }
        String host = normalizeHost(uri.getHost());
        if (!isAllowedHost(host)) {
            return new Validation(Status.HOST_NOT_ALLOWED, "host is not allowlisted");
        }
        return new Validation(Status.ALLOWED, "allowed");
    }

    public Validation validateNetworkBoundary(URI uri) {
        if (uri == null || uri.getScheme() == null) {
            return new Validation(Status.INVALID_URL, "missing URI or scheme");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!("http".equals(scheme) || "https".equals(scheme))) {
            return new Validation(Status.UNSUPPORTED_SCHEME, "only HTTP and HTTPS are allowed");
        }
        if (uri.getRawUserInfo() != null) {
            return new Validation(Status.CREDENTIALS_NOT_ALLOWED, "URL credentials are not allowed");
        }
        String host = normalizeHost(uri.getHost());
        if (host == null || host.isBlank() || !isUnambiguousAsciiHost(host)) {
            return new Validation(Status.INVALID_HOST, "invalid or ambiguous host");
        }
        if (isBlockedHostname(host)) {
            return new Validation(Status.BLOCKED_HOST, "blocked hostname");
        }
        if (isIpLiteral(host)) {
            try {
                if (isBlockedAddress(InetAddress.getByName(host))) {
                    return new Validation(Status.BLOCKED_ADDRESS, "literal address is not public");
                }
            } catch (UnknownHostException ignored) {
                return new Validation(Status.INVALID_HOST, "invalid IP literal");
            }
        }
        int port = uri.getPort();
        if (port != -1
                && !("http".equals(scheme) && port == 80)
                && !("https".equals(scheme) && port == 443)) {
            return new Validation(Status.PORT_NOT_ALLOWED, "non-standard port");
        }
        return new Validation(Status.ALLOWED, "allowed");
    }

    public Validation validateResolvedHost(String host, List<InetAddress> addresses) {
        String normalizedHost = normalizeHost(host);
        if (normalizedHost == null || !isUnambiguousAsciiHost(normalizedHost)) {
            return new Validation(Status.INVALID_HOST, "invalid or ambiguous host");
        }
        if (isBlockedHostname(normalizedHost)) {
            return new Validation(Status.BLOCKED_HOST, "blocked hostname");
        }
        if (!isAllowedHost(normalizedHost)) {
            return new Validation(Status.HOST_NOT_ALLOWED, "host is not allowlisted");
        }
        if (addresses == null || addresses.isEmpty()) {
            return new Validation(Status.DNS_FAILURE, "host resolved without addresses");
        }
        for (InetAddress address : addresses) {
            if (address == null || isBlockedAddress(address)) {
                return new Validation(Status.BLOCKED_ADDRESS, "resolved address is not public");
            }
        }
        return new Validation(Status.ALLOWED, "allowed");
    }

    static boolean isBlockedAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return isBlockedIpv4(bytes);
        }
        if (bytes.length != 16) {
            return true;
        }
        if (isIpv4Mapped(bytes)) {
            return isBlockedIpv4(new byte[] {bytes[12], bytes[13], bytes[14], bytes[15]});
        }
        if (isIpv4Compatible(bytes)) {
            return isBlockedIpv4(new byte[] {bytes[12], bytes[13], bytes[14], bytes[15]});
        }
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        boolean unspecified = true;
        for (byte value : bytes) {
            if (value != 0) {
                unspecified = false;
                break;
            }
        }
        boolean loopback = unspecifiedExceptLast(bytes, 1);
        boolean uniqueLocal = (first & 0xfe) == 0xfc;
        boolean linkLocal = first == 0xfe && (second & 0xc0) == 0x80;
        boolean multicast = first == 0xff;
        return unspecified || loopback || uniqueLocal || linkLocal || multicast
                || address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress();
    }

    private static boolean isBlockedIpv4(byte[] bytes) {
        int a = Byte.toUnsignedInt(bytes[0]);
        int b = Byte.toUnsignedInt(bytes[1]);
        int c = Byte.toUnsignedInt(bytes[2]);
        int d = Byte.toUnsignedInt(bytes[3]);
        return a == 0
                || a == 10
                || (a == 100 && b >= 64 && b <= 127)
                || a == 127
                || (a == 169 && b == 254)
                || (a == 172 && b >= 16 && b <= 31)
                || (a == 192 && b == 0 && c == 0)
                || (a == 192 && b == 0 && c == 2)
                || (a == 192 && b == 168)
                || (a == 198 && (b == 18 || b == 19))
                || (a == 198 && b == 51 && c == 100)
                || (a == 203 && b == 0 && c == 113)
                || a >= 224
                || (a == 255 && b == 255 && c == 255 && d == 255);
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    private static boolean isIpv4Compatible(byte[] bytes) {
        for (int i = 0; i < 12; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean unspecifiedExceptLast(byte[] bytes, int lastValue) {
        for (int i = 0; i < bytes.length - 1; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return Byte.toUnsignedInt(bytes[bytes.length - 1]) == lastValue;
    }

    private boolean isAllowedHost(String host) {
        if (allowedHosts.isEmpty()) {
            return false;
        }
        for (String allowed : allowedHosts) {
            if (host.equals(allowed) || host.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlockedHostname(String host) {
        return BLOCKED_HOSTS.contains(host) || host.endsWith(".localhost") || host.endsWith(".localhost.localdomain");
    }

    private boolean isUnambiguousAsciiHost(String host) {
        if (host.indexOf(':') >= 0) {
            return host.chars().allMatch(character -> Character.digit(character, 16) >= 0
                    || character == ':'
                    || character == '.');
        }
        try {
            String ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            return ascii.equals(host) && !ascii.isBlank();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.matches("[0-9]{1,3}(?:\\.[0-9]{1,3}){3}");
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return null;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            return normalized.substring(1, normalized.length() - 1);
        }
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private Set<String> normalizeAllowedHosts(Set<String> hosts) {
        if (hosts == null || hosts.isEmpty()) {
            return Set.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String host : hosts) {
            if (host == null || host.isBlank()) {
                continue;
            }
            String value = normalizeHost(host.replaceFirst("^\\*\\.", ""));
            if (value != null && isUnambiguousAsciiHost(value) && !isBlockedHostname(value)) {
                normalized.add(value);
            }
        }
        return normalized.stream().collect(Collectors.toUnmodifiableSet());
    }
}
