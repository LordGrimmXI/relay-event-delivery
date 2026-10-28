package com.relay.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.relay.delivery.DeliveryOutcome.Kind;
import com.relay.network.AddressPolicy;
import com.sun.net.httpserver.HttpServer;

class WebhookClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger hits = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void status200IsSuccess() {
        respondWith("/ok", 200);

        DeliveryOutcome outcome = loopbackClient().send(baseUrl + "/ok", 1, "{}");

        assertThat(outcome.kind()).isEqualTo(Kind.SUCCESS);
        assertThat(outcome.httpStatus()).isEqualTo(200);
        assertThat(outcome.error()).isNull();
    }

    @Test
    void status500IsRetryable() {
        respondWith("/boom", 500);

        DeliveryOutcome outcome = loopbackClient().send(baseUrl + "/boom", 1, "{}");

        assertThat(outcome.kind()).isEqualTo(Kind.RETRYABLE_FAILURE);
        assertThat(outcome.httpStatus()).isEqualTo(500);
        assertThat(outcome.error()).isEqualTo("HTTP 500");
    }

    @Test
    void status429IsRetryable() {
        respondWith("/slow-down", 429);

        DeliveryOutcome outcome = loopbackClient().send(baseUrl + "/slow-down", 1, "{}");

        assertThat(outcome.kind()).isEqualTo(Kind.RETRYABLE_FAILURE);
    }

    @Test
    void status404IsPermanent() {
        respondWith("/gone", 404);

        DeliveryOutcome outcome = loopbackClient().send(baseUrl + "/gone", 1, "{}");
        assertThat(outcome.kind()).isEqualTo(Kind.PERMANENT_FAILURE);
        assertThat(outcome.httpStatus()).isEqualTo(404);
    }

    @Test
    void redirectIsNotFollowed() {
        server.createContext("/moved", exchange -> {
            exchange.getResponseHeaders().add("Location", baseUrl + "/target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        respondWith("/target", 200);

        DeliveryOutcome outcome = loopbackClient().send(baseUrl + "/moved", 1, "{}");

        assertThat(outcome.kind()).isEqualTo(Kind.RETRYABLE_FAILURE);
        assertThat(outcome.httpStatus()).isEqualTo(302);
        assertThat(hits.get()).isZero();
    }

    @Test
    void slowResponseTimesOut() {
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        WebhookClient client = new WebhookClient(
                new AddressPolicy(true), Duration.ofSeconds(2), Duration.ofMillis(300));

        DeliveryOutcome outcome = client.send(baseUrl + "/slow", 1, "{}");

        assertThat(outcome.kind()).isEqualTo(Kind.RETRYABLE_FAILURE);
        assertThat(outcome.httpStatus()).isNull();
        assertThat(outcome.error()).isEqualTo("timeout");
    }

    @Test
    void refusedConnectionIsRetryable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }

        DeliveryOutcome outcome =
                loopbackClient().send("http://127.0.0.1:" + closedPort + "/hook", 1, "{}");

        assertThat(outcome.kind()).isEqualTo(Kind.RETRYABLE_FAILURE);
        assertThat(outcome.httpStatus()).isNull();
        assertThat(outcome.error()).isEqualTo("connection error");
    }

    @Test
    void blockedAddressIsRetryableAndNeverContacted() {
        respondWith("/ok", 200);
        WebhookClient strictClient = new WebhookClient(new AddressPolicy(false));

        DeliveryOutcome outcome = strictClient.send(baseUrl + "/ok", 1, "{}");

        assertThat(outcome.kind()).isEqualTo(Kind.RETRYABLE_FAILURE);
        assertThat(outcome.error()).isEqualTo("destination address is blocked");
        assertThat(hits.get()).isZero();
    }

    @Test
    void requestCarriesPayloadAndEventId() {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> eventId = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        server.createContext("/echo", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            eventId.set(exchange.getRequestHeaders().getFirst("Relay-Event-Id"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        loopbackClient().send(baseUrl + "/echo", 42, "{\"a\":1}");

        assertThat(body.get()).isEqualTo("{\"a\":1}");
        assertThat(eventId.get()).isEqualTo("42");
        assertThat(contentType.get()).startsWith("application/json");
    }

    @Test
    void invalidUrlIsPermanent() {
        DeliveryOutcome outcome = loopbackClient().send("not a url", 1, "{}");

        assertThat(outcome.kind()).isEqualTo(Kind.PERMANENT_FAILURE);
        assertThat(outcome.error()).isEqualTo("invalid destination URL");
    }

    private WebhookClient loopbackClient() {
        return new WebhookClient(
                new AddressPolicy(true), Duration.ofSeconds(2), Duration.ofSeconds(2));
    }

    private void respondWith(String path, int status) {
        server.createContext(path, exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
    }
}