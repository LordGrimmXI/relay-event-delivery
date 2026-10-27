package com.relay.destination;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DestinationUrlValidatorTest {

    private DestinationUrlValidator validator;
    private DestinationUrlValidator loopbackValidator;

    @BeforeEach
    void setUp() {
        validator = new DestinationUrlValidator(false);
        loopbackValidator = new DestinationUrlValidator(true);
    }

    @Test
    void allowsPublicHttpAndHttpsUrls() {
        assertThat(validator.isAllowed(
                "https://example.com/webhook")).isTrue();

        assertThat(validator.isAllowed(
                "http://example.com:8080/events")).isTrue();
    }

    @Test
    void rejectsUnsupportedSchemes() {
        assertThat(validator.isAllowed(
                "ftp://example.com/file")).isFalse();

        assertThat(validator.isAllowed(
                "file:///etc/passwd")).isFalse();
    }

    @Test
    void rejectsMissingOrMalformedUrls() {
        assertThat(validator.isAllowed(null)).isFalse();
        assertThat(validator.isAllowed("")).isFalse();
        assertThat(validator.isAllowed("not-a-url")).isFalse();
        assertThat(validator.isAllowed("https:///webhook")).isFalse();
    }

    @Test
    void rejectsLocalHostnames() {
        assertThat(validator.isAllowed(
                "http://localhost:8080/admin")).isFalse();

        assertThat(validator.isAllowed(
                "http://service.localhost/webhook")).isFalse();

        assertThat(validator.isAllowed(
                "http://printer.local/webhook")).isFalse();
    }

    @Test
    void rejectsProhibitedIpv4Addresses() {
        assertThat(validator.isAllowed(
                "http://127.0.0.1/webhook")).isFalse();

        assertThat(validator.isAllowed(
                "http://10.0.0.5/webhook")).isFalse();

        assertThat(validator.isAllowed(
                "http://192.168.1.10/webhook")).isFalse();

        assertThat(validator.isAllowed(
                "http://169.254.169.254/")).isFalse();
    }

    @Test
    void rejectsProhibitedIpv6Addresses() {
        assertThat(validator.isAllowed(
                "http://[::1]/webhook")).isFalse();

        assertThat(validator.isAllowed(
                "http://[fc00::1]/webhook")).isFalse();
    }

    @Test
    void rejectsUrlsContainingCredentials() {
        assertThat(validator.isAllowed(
                "https://user:password@example.com/webhook"))
                .isFalse();
    }

    @Test
    void rejectsInvalidPorts() {
        assertThat(validator.isAllowed(
                "http://example.com:0/webhook")).isFalse();

        assertThat(validator.isAllowed(
                "http://example.com:99999/webhook")).isFalse();
    }

    @Test
    void rejectsTrailingDotLocalHostnames() {
        assertThat(validator.isAllowed(
                "http://localhost./hook")).isFalse();

        assertThat(validator.isAllowed(
                "http://printer.local./hook")).isFalse();
    }

    @Test
    void rejectsReservedIpv4Ranges() {
        assertThat(validator.isAllowed("http://0.1.2.3/")).isFalse();
        assertThat(validator.isAllowed("http://100.64.0.1/")).isFalse();
        assertThat(validator.isAllowed("http://198.18.0.1/")).isFalse();
        assertThat(validator.isAllowed("http://240.0.0.1/")).isFalse();
    }

    @Test
    void rejectsNonStandardIpv4Notation() {
        assertThat(validator.isAllowed("http://0x7f.0.0.1/")).isFalse();
        assertThat(validator.isAllowed("http://2130706433/")).isFalse();
    }

    @Test
    void rejectsIpv4MappedIpv6Loopback() {
        assertThat(validator.isAllowed("http://[::ffff:127.0.0.1]/")).isFalse();
    }

    @Test
    void loopbackIsAllowedWhenSettingIsOn() {
        assertThat(loopbackValidator.isAllowed("http://localhost:9000/hook")).isTrue();
        assertThat(loopbackValidator.isAllowed("http://localhost./hook")).isTrue();
        assertThat(loopbackValidator.isAllowed("http://127.0.0.1:9000/hook")).isTrue();
        assertThat(loopbackValidator.isAllowed("http://[::1]:9000/hook")).isTrue();
    }

    @Test
    void settingOnStillRejectsEverythingThatIsNotLoopback() {
        assertThat(loopbackValidator.isAllowed("http://10.0.0.5/hook")).isFalse(); // Private range
        assertThat(loopbackValidator.isAllowed("http://169.254.169.254/")).isFalse(); // Cloud metadata address
        assertThat(loopbackValidator.isAllowed("http://metadata.google.internal/")).isFalse(); // Metadata hostname
        assertThat(loopbackValidator.isAllowed("http://service.localhost/hook")).isFalse(); // Only plain "localhost" is allowed
    }

    @Test
    void settingOnStillAppliesTheNormalRules() {
        assertThat(loopbackValidator.isAllowed("ftp://localhost/file")).isFalse();
        assertThat(loopbackValidator.isAllowed("http://user:pw@localhost/hook")).isFalse();
        assertThat(loopbackValidator.isAllowed("https://example.com/webhook")).isTrue();
    }
}