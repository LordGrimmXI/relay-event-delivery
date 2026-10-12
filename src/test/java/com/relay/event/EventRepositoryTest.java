package com.relay.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class EventRepositoryTest {

    @Autowired
    private EventRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void newKeyIsInserted() {
        String key = UUID.randomUUID().toString(); // unique on every run

        Optional<Long> id = repository.insertIfAbsent("order.created", key, "{\"orderId\": 1}");

        assertThat(id).isPresent();
    }

    @Test
    void duplicateKeyIsRejected() {
        String key = UUID.randomUUID().toString();

        Optional<Long> first = repository.insertIfAbsent("order.created", key, "{\"orderId\": 1}");
        Optional<Long> second = repository.insertIfAbsent("order.created", key, "{\"orderId\": 1}");

        assertThat(first).isPresent();   // first insert really happened
        assertThat(second).isEmpty();    // second was reported as duplicate

        Integer rowCount = jdbc.queryForObject(
                "SELECT count(*) FROM events WHERE idempotency_key = ?",
                Integer.class,
                key);

        assertThat(rowCount).isEqualTo(1); // the table really holds one row
    }

    @Test
    void findReturnsStoredEvent() {
        String key = UUID.randomUUID().toString();
        repository.insertIfAbsent("order.created", key, "{\"orderId\": 1}");

        Event event = repository.findByIdempotencyKey(key).orElseThrow();

        assertThat(event.eventType()).isEqualTo("order.created");
        assertThat(event.idempotencyKey()).isEqualTo(key);
        assertThat(event.payload()).contains("orderId");
        assertThat(event.createdAt()).isNotNull();
    }
}