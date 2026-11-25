package com.ticket.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticket.events.Topics;
import com.ticket.inventory.messaging.outbox.OutboxCleanup;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OutboxCleanupTest {

    @Autowired
    private OutboxCleanup cleanup;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyTheTable() {
        jdbc.sql("DELETE FROM outbox_event").update();
    }

    @Test
    void deletesAPublishedRowOlderThanSevenDays() {
        insert(ago(Duration.ofDays(9)), ago(Duration.ofDays(8)));

        cleanup.deleteOldPublishedRows();

        assertThat(rowCount()).isZero();
    }

    @Test
    void keepsAPublishedRowYoungerThanSevenDays() {
        insert(ago(Duration.ofDays(7)), ago(Duration.ofDays(6)));

        cleanup.deleteOldPublishedRows();

        assertThat(rowCount()).isEqualTo(1L);
    }

    @Test
    void keepsAnUnpublishedRowEvenWhenItIsOld() {
        insert(ago(Duration.ofDays(30)), null);

        cleanup.deleteOldPublishedRows();

        assertThat(rowCount()).isEqualTo(1L);
    }

    @Test
    void keepsARowThatWasPublishedJustNow() {
        insert(ago(Duration.ofMinutes(1)), Instant.now());

        cleanup.deleteOldPublishedRows();

        assertThat(rowCount()).isEqualTo(1L);
    }

    @Test
    void returnsTheNumberOfDeletedRows() {
        insert(ago(Duration.ofDays(9)), ago(Duration.ofDays(8)));
        insert(ago(Duration.ofDays(10)), ago(Duration.ofDays(9)));
        insert(ago(Duration.ofDays(1)), ago(Duration.ofHours(1)));

        int deleted = cleanup.deleteOldPublishedRows();

        assertThat(deleted).isEqualTo(2);
    }

    private void insert(Instant createdAt, Instant publishedAt) {
        UUID id = UUID.randomUUID();
        OutboxFixtures.insert(jdbc, id, Topics.INVENTORY_EVENTS, id.toString(), "{}", createdAt, publishedAt);
    }

    private Long rowCount() {
        return jdbc.sql("SELECT count(*) FROM outbox_event").query(Long.class).single();
    }

    private static Instant ago(Duration duration) {
        return Instant.now().minus(duration);
    }
}
