package com.ticket.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class MessageContractTest {

    private final MessageCodec codec = new MessageCodec();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    static Stream<Arguments> samples() {
        return MessageSamples.byType().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(sample -> Arguments.of(Named.of(sample.getKey(), sample.getValue())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    void roundTripReturnsTheSameMessage(MessageEnvelope<Object> sample) {
        String json = codec.toJson(sample);

        MessageEnvelope<?> read = codec.fromJson(json);

        assertThat(read).isEqualTo(sample);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    void writesTheStoredJson(MessageEnvelope<Object> sample) {
        JsonNode stored = jsonMapper.readTree(MessageSamples.storedJson(sample.type()));

        JsonNode written = jsonMapper.readTree(codec.toJson(sample));

        assertThat((Object) written).isEqualTo(stored);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    void readsTheStoredJson(MessageEnvelope<Object> sample) {
        String stored = MessageSamples.storedJson(sample.type());

        MessageEnvelope<?> read = codec.fromJson(stored);

        assertThat(read).isEqualTo(sample);
    }

    @Test
    void samplesCoverEveryTypeName() {
        List<String> typeNames = ConstantReader.stringConstantsOf(MessageTypes.class);

        assertThat(MessageSamples.byType().keySet()).containsExactlyInAnyOrderElementsOf(typeNames);
    }

    @Test
    void everyTypeNameHasAStoredJsonFile() {
        List<String> typeNames = ConstantReader.stringConstantsOf(MessageTypes.class);

        assertThat(typeNames).isNotEmpty().allSatisfy(type ->
            assertThat(MessageSamples.hasStoredJson(type)).as(type).isTrue());
    }

    @Test
    void everySampleCarriesItsOwnTypeName() {
        Map<String, MessageEnvelope<Object>> samples = MessageSamples.byType();

        assertThat(samples.entrySet()).isNotEmpty().allSatisfy(sample ->
            assertThat(sample.getValue().type()).isEqualTo(sample.getKey()));
    }
}
