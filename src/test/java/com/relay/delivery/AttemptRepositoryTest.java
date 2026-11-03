package com.relay.delivery;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class AttemptRepositoryTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AttemptRepository attempts;

    @SuppressWarnings("null")
    @Test
    public void attemptsComeBackInAttemptOrder() {
        long eventId = createEvent();
        long destinationId = createDestination();
        long deliveryId = createDelivery(eventId, destinationId);
        // Inserted out of order on purpose.
        attempts.insert(deliveryId, 2, DeliveryOutcome.ofStatus(200), 20, Instant.now());
        attempts.insert(deliveryId, 1, DeliveryOutcome.ofStatus(500), 10, Instant.now());

        Map<Long, List<AttemptStatus>> result = attempts.findByEventId(eventId);

        assertThat(result.get(destinationId))
                .extracting(AttemptStatus::attemptNumber)
                .containsExactly(1, 2);
    }

    @Test
    public void attemptsAreGroupedByDestination() {
        long eventId = createEvent();
        long destinationA = createDestination();
        long destinationB = createDestination();
        long deliveryA = createDelivery(eventId, destinationA);
        long deliveryB = createDelivery(eventId, destinationB);
        attempts.insert(deliveryA, 1, DeliveryOutcome.ofStatus(200), 5, Instant.now());
        attempts.insert(deliveryB, 1, DeliveryOutcome.ofStatus(500), 5, Instant.now());
        attempts.insert(deliveryB, 2, DeliveryOutcome.ofStatus(200), 5, Instant.now());

        Map<Long, List<AttemptStatus>> result = attempts.findByEventId(eventId);

        assertThat(result.keySet()).containsExactlyInAnyOrder(destinationA, destinationB);
        assertThat(result.get(destinationA)).hasSize(1);
        assertThat(result.get(destinationB)).hasSize(2);
    }

    @Test
    public void attemptWithoutAResponseHasANullHttpStatus() {
        long eventId = createEvent();
        long destinationId = createDestination();
        long deliveryId = createDelivery(eventId, destinationId);
        attempts.insert(deliveryId, 1, DeliveryOutcome.retryable("timeout"), 2000, Instant.now());

        AttemptStatus attempt = attempts.findByEventId(eventId).get(destinationId).get(0);

        assertThat(attempt.httpStatus()).isNull();
        assertThat(attempt.errorMessage()).isEqualTo("timeout");
        assertThat(attempt.outcome()).isEqualTo("FAILURE");
    }

    @Test
    public void eventWithoutAttemptsGivesAnEmptyMap() {
        long eventId = createEvent();

        assertThat(attempts.findByEventId(eventId)).isEmpty();
    }

    @SuppressWarnings("null")
    private long createEvent() {
        String sql = """
                INSERT INTO events (event_type, idempotency_key, payload)
                VALUES ('attempts.test', ?, '{}'::jsonb)
                RETURNING id
                """;

        return jdbc.queryForObject(sql, Long.class, "attempts-" + UUID.randomUUID());
    }

    @SuppressWarnings("null")
    private long createDestination() {
        String sql = """
                INSERT INTO destinations (name, url)
                VALUES (?, 'https://example.com/hook')
                RETURNING id
                """;

        return jdbc.queryForObject(sql, Long.class, "dest-" + UUID.randomUUID());
    }

    @SuppressWarnings("null")
    private long createDelivery(long eventId, long destinationId) {
        String sql = """
                INSERT INTO deliveries (event_id, destination_id)
                VALUES (?, ?)
                RETURNING id
                """;

        return jdbc.queryForObject(sql, Long.class, eventId, destinationId);
    }
}