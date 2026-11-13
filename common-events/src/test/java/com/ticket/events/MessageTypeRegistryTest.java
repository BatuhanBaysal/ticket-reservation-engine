package com.ticket.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MessageTypeRegistryTest {

    @Test
    void registersEveryTypeNameOfMessageTypes() {
        Set<String> expected = new HashSet<>(ConstantReader.stringConstantsOf(MessageTypes.class));

        Set<String> registered = MessageTypeRegistry.registeredTypes();

        assertThat(registered).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void namesEveryTypeAfterItsRecord() {
        Set<String> registered = MessageTypeRegistry.registeredTypes();

        assertThat(registered).isNotEmpty().allSatisfy(type ->
            assertThat(MessageTypeRegistry.payloadClassOf(type).getSimpleName()).isEqualTo(type));
    }

    @Test
    void mapsEveryTypeToARecord() {
        Set<String> registered = MessageTypeRegistry.registeredTypes();

        assertThat(registered).isNotEmpty().allSatisfy(type ->
            assertThat(MessageTypeRegistry.payloadClassOf(type).isRecord()).isTrue());
    }

    @Test
    void rejectsAnUnknownType() {
        assertThatThrownBy(() -> MessageTypeRegistry.payloadClassOf("NoSuchMessage"))
            .isInstanceOf(UnknownMessageTypeException.class)
            .hasMessage("Unknown message type: NoSuchMessage");
    }
}
