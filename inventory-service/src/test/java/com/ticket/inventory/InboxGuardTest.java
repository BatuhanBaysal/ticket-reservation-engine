package com.ticket.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.ticket.inventory.messaging.inbox.InboxGuard;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class InboxGuardTest {

    private static final String CONSUMER = "inventory-service";

    @Autowired
    private InboxGuard guard;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;

    @BeforeEach
    void emptyTheTable() {
        jdbc.sql("DELETE FROM processed_message").update();
        transaction = new TransactionTemplate(transactionManager);
    }

    @Test
    void returnsTrueForTheFirstDelivery() {
        UUID messageId = UUID.randomUUID();

        Boolean first = transaction.execute(status -> guard.firstDelivery(messageId, CONSUMER));

        assertThat(first).isTrue();
    }

    @Test
    void returnsFalseForARepeat() {
        UUID messageId = UUID.randomUUID();
        transaction.execute(status -> guard.firstDelivery(messageId, CONSUMER));

        Boolean second = transaction.execute(status -> guard.firstDelivery(messageId, CONSUMER));

        assertThat(second).isFalse();
    }

    @Test
    void storesTheConsumer() {
        UUID messageId = UUID.randomUUID();
        transaction.execute(status -> guard.firstDelivery(messageId, CONSUMER));

        String consumer = jdbc.sql("SELECT consumer FROM processed_message").query(String.class).single();

        assertThat(consumer).isEqualTo(CONSUMER);
    }

    @Test
    void failsWithoutATransaction() {
        UUID messageId = UUID.randomUUID();

        assertThatThrownBy(() -> guard.firstDelivery(messageId, CONSUMER))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void rejectsAMissingMessageId() {
        assertThatThrownBy(() -> transaction.execute(status -> guard.firstDelivery(null, CONSUMER)))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("messageId");
    }

    @Test
    void rejectsABlankConsumer() {
        UUID messageId = UUID.randomUUID();

        assertThatThrownBy(() -> transaction.execute(status -> guard.firstDelivery(messageId, " ")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("consumer must not be blank");
    }

    @Test
    void takesTheRowBackWhenTheTransactionRollsBack() {
        UUID messageId = UUID.randomUUID();
        Consumer<TransactionStatus> insertThenFail = status -> {
            guard.firstDelivery(messageId, CONSUMER);
            throw new IllegalStateException("boom");
        };
        catchThrowable(() -> transaction.executeWithoutResult(insertThenFail));

        Boolean again = transaction.execute(status -> guard.firstDelivery(messageId, CONSUMER));

        assertThat(again).isTrue();
    }

    @Test
    void hasAPrimaryKey() {
        Long keys = jdbc.sql("""
                SELECT count(*) FROM information_schema.table_constraints
                WHERE table_name = 'processed_message' AND constraint_type = 'PRIMARY KEY'
                """)
            .query(Long.class)
            .single();

        assertThat(keys).isEqualTo(1L);
    }

    @RepeatedTest(value = 20, name = "round {currentRepetition} of {totalRepetitions}")
    void twoTransactionsWithTheSameMessageIdInsertOneRow() {
        UUID messageId = UUID.randomUUID();
        Callable<Boolean> insert = () -> transaction.execute(status -> guard.firstDelivery(messageId, CONSUMER));

        List<Boolean> results = ConcurrentRunner.runTogether(List.of(insert, insert));

        assertThat(results).containsExactlyInAnyOrder(true, false);
    }
}
