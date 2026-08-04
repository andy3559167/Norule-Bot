package com.norule.musicbot.domain.music;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioUrlSafetyValidatorTest {
    @Test
    void blocksPrivateSpecialAndReservedAddresses() {
        AudioUrlSafetyValidator validator = validator(Set.of("localhost", "metadata.google.internal"));

        for (String input : List.of(
                "http://localhost/",
                "http://127.0.0.1/",
                "http://127.0.0.1:8080/",
                "http://10.0.0.1/",
                "http://172.16.0.1/",
                "http://192.168.1.1/",
                "http://169.254.169.254/",
                "http://[::1]/",
                "http://[fe80::1]/",
                "http://[::ffff:127.0.0.1]/"
        )) {
            assertFalse(validator.validate(URI.create(input)).allowed(), input);
        }
    }

    @Test
    void rejectsBlockedIpRangesEvenWhenTheLiteralAddressIsAllowlisted() {
        for (String input : List.of(
                "http://127.0.0.1/file.mp3",
                "http://10.0.0.1/file.mp3",
                "http://172.16.0.1/file.mp3",
                "http://192.168.1.1/file.mp3",
                "http://169.254.169.254/file.mp3",
                "http://[::1]/file.mp3",
                "http://[fe80::1]/file.mp3",
                "http://[::ffff:192.168.1.1]/file.mp3"
        )) {
            URI uri = URI.create(input);
            String host = uri.getHost().replace("[", "").replace("]", "");
            AudioUrlSafetyValidator validator = validator(Set.of(host));
            assertEquals(AudioUrlSafetyValidator.Status.BLOCKED_ADDRESS, validator.validate(uri).status(), input);
        }
    }

    @Test
    void identifiesBlockedLiteralBeforeApplyingTheAllowlist() {
        AudioUrlSafetyValidator validator = validator(Set.of("cdn.example.com"));

        assertEquals(
                AudioUrlSafetyValidator.Status.BLOCKED_ADDRESS,
                validator.validateNetworkBoundary(URI.create("http://127.0.0.1:8080/")).status()
        );
    }

    @Test
    void blocksPublicHostnameWhenDnsResolvesToPrivateAddress() throws Exception {
        AudioUrlSafetyValidator validator = new AudioUrlSafetyValidator(
                Set.of("cdn.example.com"),
                host -> List.of(InetAddress.getByName("192.168.10.20"))
        );

        assertEquals(
                AudioUrlSafetyValidator.Status.BLOCKED_ADDRESS,
                validator.validate(URI.create("https://cdn.example.com/file.mp3")).status()
        );
    }

    @Test
    void reusesTheSameRulesForRedirectDestinations() throws Exception {
        Map<String, String> addresses = Map.of(
                "cdn.example.com", "8.8.8.8",
                "redirect.example.com", "127.0.0.1"
        );
        AudioUrlSafetyValidator validator = new AudioUrlSafetyValidator(
                Set.of("cdn.example.com", "redirect.example.com"),
                host -> List.of(InetAddress.getByName(addresses.get(host)))
        );

        assertTrue(validator.validate(URI.create("https://cdn.example.com/file.mp3")).allowed());
        assertEquals(
                AudioUrlSafetyValidator.Status.BLOCKED_ADDRESS,
                validator.validateRedirect(URI.create("https://redirect.example.com/file.mp3")).status()
        );
    }

    @Test
    void rejectsCredentialsNonHttpSchemesUnexpectedPortsAndUnicodeHostConfusion() {
        AudioUrlSafetyValidator validator = validator(Set.of("cdn.example.com"));

        assertEquals(AudioUrlSafetyValidator.Status.CREDENTIALS_NOT_ALLOWED,
                validator.validate(URI.create("https://user:pass@cdn.example.com/file.mp3")).status());
        assertEquals(AudioUrlSafetyValidator.Status.UNSUPPORTED_SCHEME,
                validator.validate(URI.create("ftp://cdn.example.com/file.mp3")).status());
        assertEquals(AudioUrlSafetyValidator.Status.PORT_NOT_ALLOWED,
                validator.validate(URI.create("https://cdn.example.com:8443/file.mp3")).status());
        assertEquals(AudioUrlSafetyValidator.Status.INVALID_HOST,
                validator.validate(URI.create("https://\u0435xample.com/file.mp3")).status());
    }

    @Test
    void reportsDnsFailureWithoutAllowingTheUrl() {
        AudioUrlSafetyValidator validator = new AudioUrlSafetyValidator(Set.of("cdn.example.com"), host -> {
            throw new UnknownHostException(host);
        });

        assertEquals(AudioUrlSafetyValidator.Status.DNS_FAILURE,
                validator.validate(URI.create("https://cdn.example.com/file.mp3")).status());
    }

    private AudioUrlSafetyValidator validator(Set<String> allowedHosts) {
        return new AudioUrlSafetyValidator(allowedHosts, host -> List.of(InetAddress.getAllByName(host)));
    }
}
