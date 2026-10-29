package com.relay.delivery;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.relay.network.AddressPolicy;
import com.sun.net.httpserver.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class DeliveryWorkerTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DeliveryRepository deliveries;

    private DeliveryWorker worker;
    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger hits = new AtomicInteger();

    @BeforeEach
    public void setUp() throws IOException {
        String sql = """
                UPDATE deliveries
                SET status = 'PERMANENTLY_FAILED'
                WHERE status = 'PENDING'
                """;
        jdbc.update(sql);

        server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        WebhookClient client = new WebhookClient(
                new AddressPolicy(true), Duration.ofSeconds(2), Duration.ofSeconds(2));
        worker = new DeliveryWorker(deliveries, client);
    }

    @AfterEach
    public void stopServer() {
        server.stop(0);
    }

    @Test
    public void successfulSendMarksDelivered() {
        respondWith("/ok", 200);
        long id = createDelivery(baseUrl + "/ok", true);

        int processed = worker.runOnce();

        assertThat(processed).isEqualTo(1);
        assertThat(hits.get()).isEqualTo(1);
        assertThat(findStatus(id)).isEqualTo("DELIVERED");
        assertThat(findAttemptCount(id)).isEqualTo(1);
        assertThat(findLastError(id)).isNull();
    }

    @Test
    public void serverErrorGoesBackToPendingForALaterRetry() {
        respondWith("/boom", 500);
        long id = createDelivery(baseUrl + "/boom", true);

        worker.runOnce();

        assertThat(findStatus(id)).isEqualTo("PENDING");
        assertThat(findAttemptCount(id)).isEqualTo(1);
        assertThat(findLastError(id)).isEqualTo("HTTP 500");
        assertThat(isDueInTheFuture(id)).isTrue();
    }

    @Test
    public void notFoundIsAPermanentFailure() {
        respondWith("/gone", 404);
        long id = createDelivery(baseUrl + "/gone", true);

        worker.runOnce();

        assertThat(findStatus(id)).isEqualTo("PERMANENTLY_FAILED");
        assertThat(findAttemptCount(id)).isEqualTo(1);
        assertThat(findLastError(id)).isEqualTo("HTTP 404");
    }

    @Test
    public void inactiveDestinationIsNeverSent() {
        respondWith("/ok", 200);
        long id = createDelivery(baseUrl + "/ok", false);

        int processed = worker.runOnce();

        assertThat(processed).isZero();
        assertThat(hits.get()).isZero();
        assertThat(findStatus(id)).isEqualTo("PERMANENTLY_FAILED");
        assertThat(findLastError(id)).isEqualTo("destination deactivated");
    }

    @Test
    public void nothingDueMeansNothingSent() {
        int processed = worker.runOnce();

        assertThat(processed).isZero();
        assertThat(hits.get()).isZero();
    }

    private void respondWith(String path, int status) {
        server.createContext(path, exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
    }

    private String findStatus(long id) {
        String sql = "SELECT status FROM deliveries WHERE id = ?";

        return jdbc.queryForObject(sql, String.class, id);
    }

    private String findLastError(long id) {
        String sql = "SELECT last_error FROM deliveries WHERE id = ?";

        return jdbc.queryForObject(sql, String.class, id);
    }

    private Integer findAttemptCount(long id) {
        String sql = "SELECT attempt_count FROM deliveries WHERE id = ?";

        return jdbc.queryForObject(sql, Integer.class, id);
    }

    private Boolean isDueInTheFuture(long id) {
        String sql = "SELECT next_attempt_at > NOW() FROM deliveries WHERE id = ?";

        return jdbc.queryForObject(sql, Boolean.class, id);
    }

    @SuppressWarnings("null")
    private long createDelivery(String url, boolean destinationActive) {
        String suffix = UUID.randomUUID().toString();

        String eventSql = """
                INSERT INTO events (event_type, idempotency_key, payload)
                VALUES ('worker.test', ?, '{"a":1}'::jsonb)
                RETURNING id
                """;
        Long eventId = jdbc.queryForObject(eventSql, Long.class, "worker-" + suffix);

        String destinationSql = """
                INSERT INTO destinations (name, url, is_active)
                VALUES (?, ?, ?)
                RETURNING id
                """;
        Long destinationId = jdbc.queryForObject(
                destinationSql, Long.class, "dest-" + suffix, url, destinationActive);

        String deliverySql = """
                INSERT INTO deliveries (event_id, destination_id)
                VALUES (?, ?)
                RETURNING id
                """;
        return jdbc.queryForObject(deliverySql, Long.class, eventId, destinationId);
    }
}