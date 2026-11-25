package com.ticket.inventory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

final class TopicReader {

    private static final Duration POLL = Duration.ofMillis(200);

    private TopicReader() {
    }

    static List<ConsumerRecord<String, String>> readFor(String bootstrapServers, String topic, Duration duration) {
        return read(bootstrapServers, topic, records -> false, duration);
    }

    static List<ConsumerRecord<String, String>> readUntil(
        String bootstrapServers,
        String topic,
        Predicate<List<ConsumerRecord<String, String>>> done,
        Duration timeout) {
        return read(bootstrapServers, topic, done, timeout);
    }

    private static List<ConsumerRecord<String, String>> read(
        String bootstrapServers,
        String topic,
        Predicate<List<ConsumerRecord<String, String>>> done,
        Duration timeout) {
        Map<String, Object> config = Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        long deadline = System.nanoTime() + timeout.toNanos();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            while (!done.test(records) && System.nanoTime() < deadline) {
                consumer.poll(POLL).forEach(records::add);
            }
        }
        return records;
    }
}
