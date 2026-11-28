package com.ticket.inventory.messaging.kafka;

import com.ticket.events.InvalidMessageException;
import com.ticket.events.MessageCodec;
import com.ticket.events.MessageEnvelope;
import com.ticket.events.Topics;
import com.ticket.inventory.service.SeatCommandHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class InventoryCommandListener {

    static final String GROUP = "inventory-service";

    private static final String CORRELATION_ID = "correlationId";
    private static final Logger LOGGER = LoggerFactory.getLogger(InventoryCommandListener.class);

    private final MessageCodec codec;
    private final SeatCommandHandler handler;

    public InventoryCommandListener(MessageCodec codec, SeatCommandHandler handler) {
        this.codec = codec;
        this.handler = handler;
    }

    @KafkaListener(
        topics = Topics.INVENTORY_COMMANDS,
        groupId = GROUP,
        autoStartup = "${inventory.consumer.enabled:true}")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        if (record.value() == null) {
            throw new InvalidMessageException("The record has no value");
        }
        MessageEnvelope<?> envelope = codec.fromJson(record.value());
        MDC.put(CORRELATION_ID, envelope.correlationId());
        try {
            handler.handle(envelope);
            LOGGER.info("Handled {} {} from partition {} at offset {}",
                envelope.type(), envelope.messageId(), record.partition(), record.offset());
            acknowledgment.acknowledge();
        } finally {
            MDC.remove(CORRELATION_ID);
        }
    }
}
