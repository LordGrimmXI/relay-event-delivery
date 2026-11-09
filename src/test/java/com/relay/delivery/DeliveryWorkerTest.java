package com.relay.delivery;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
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

    @Autowired
    private AttemptRepository attempts;

    private DeliveryWorker worker;
    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger hits = new AtomicInteger();

    @BeforeEach
    public void setUp() throws IOException {
        // RUNNING rows are parked too: leftover leases would be recovered and sent.
        String sql = """
                UPDATE deliveries
                SET status = 'PERMANENTLY_FAILED'
                WHERE status IN ('PENDING', 'RUNNING')
                """;
        jdbc.update(sql);

        server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        WebhookClient client = new WebhookClient(
                new AddressPolicy(true), Duration.ofSeconds(2), Duration.ofSeconds(2));
        RetryPolicy retryPolicy = new RetryPolicy(8, () -> 1.0);
        DeliveryRecorder recorder = new DeliveryRecorder(deliveries, attempts, retryPolicy);
        worker = new DeliveryWorker(deliveries, client, recorder, retryPolicy);
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
        assertThat(isDueInAboutThirtySeconds(id)).isTrue();
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

    @Test
    public void failureOnTheLastAllowedAttemptGivesUp() {
        respondWith("/boom", 500);
        long id = createDelivery(baseUrl + "/boom", true);
        setAttemptCount(id, 7);

        worker.runOnce();

        assertThat(hits.get()).isEqualTo(1);
        assertThat(findStatus(id)).isEqualTo("PERMANENTLY_FAILED");
        assertThat(findAttemptCount(id)).isEqualTo(8);
        assertThat(findLastError(id)).isEqualTo("retry limit reached: HTTP 500");
    }

    @Test
    public void failureOneBeforeTheLimitStillRetries() {
        respondWith("/boom", 500);
        long id = createDelivery(baseUrl + "/boom", true);
        setAttemptCount(id, 6);

        worker.runOnce();

        assertThat(findStatus(id)).isEqualTo("PENDING");
        assertThat(findAttemptCount(id)).isEqualTo(7);
        assertThat(findLastError(id)).isEqualTo("HTTP 500");
    }

    @Test
    public void everySendWritesOneAttemptRow() {
        respondWith("/boom", 500);
        long id = createDelivery(baseUrl + "/boom", true);

        worker.runOnce();

        Map<String, Object> attempt = findOnlyAttempt(id);
        assertThat(attempt.get("attempt_number")).isEqualTo(1);
        assertThat(attempt.get("outcome")).isEqualTo("FAILURE");
        assertThat(attempt.get("http_status")).isEqualTo(500);
        assertThat(attempt.get("error_message")).isEqualTo("HTTP 500");
        assertThat((Integer) attempt.get("duration_ms")).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void aCrashedRowIsRecoveredAndSent() {
        respondWith("/ok", 200);
        long id = createDelivery(baseUrl + "/ok", true);
        // A worker claimed the row and then "crashed": the lease expires unused.
        deliveries.claimDue(10, 30);
        expireLease(id);

        int processed = worker.runOnce();

        assertThat(processed).isEqualTo(1);
        assertThat(hits.get()).isEqualTo(1);
        assertThat(findStatus(id)).isEqualTo("DELIVERED");
        // One attempt for the recovery, one for the send that succeeded.
        assertThat(findAttemptCount(id)).isEqualTo(2);
        // The history has a gap: attempt 1 (the crashed one) was never recorded.
        assertThat(findOnlyAttempt(id).get("attempt_number")).isEqualTo(2);
    }

    @Test
    public void aCrashedRowAtTheLimitIsNeverSent() {
        respondWith("/ok", 200);
        long id = createDelivery(baseUrl + "/ok", true);
        setAttemptCount(id, 7);
        deliveries.claimDue(10, 30);
        expireLease(id);

        int processed = worker.runOnce();

        assertThat(processed).isEqualTo(0);
        assertThat(hits.get()).isEqualTo(0);
        assertThat(findStatus(id)).isEqualTo("PERMANENTLY_FAILED");
        assertThat(findAttemptCount(id)).isEqualTo(8);
        assertThat(findLastError(id)).isEqualTo("retry limit reached: lease expired");
    }

    private void respondWith(String path, int status) {
        server.createContext(path, exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
    }

    private void setAttemptCount(long id, int count) {
        String sql = "UPDATE deliveries SET attempt_count = ? WHERE id = ?";

        jdbc.update(sql, count, id);
    }

    // Simulates time passing: the lease ended a second ago.
    private void expireLease(long id) {
        String sql = """
                UPDATE deliveries
                SET lease_expires_at = NOW() - INTERVAL '1 second'
                WHERE id = ?
                """;

        jdbc.update(sql, id);
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

    private Boolean isDueInAboutThirtySeconds(long id) {
        String sql = """
                SELECT next_attempt_at > NOW() + INTERVAL '20 seconds'
                   AND next_attempt_at <= NOW() + INTERVAL '31 seconds'
                FROM deliveries
                WHERE id = ?
                """;

        return jdbc.queryForObject(sql, Boolean.class, id);
    }

    // queryForMap fails unless there is exactly one row, which is what we want here.
    private Map<String, Object> findOnlyAttempt(long id) {
        String sql = """
                SELECT attempt_number, outcome, http_status, error_message, duration_ms
                FROM delivery_attempts
                WHERE delivery_id = ?
                """;

        return jdbc.queryForMap(sql, id);
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