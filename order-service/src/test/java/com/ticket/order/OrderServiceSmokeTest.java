package com.ticket.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OrderServiceSmokeTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Test
    void healthEndpointReportsUp() {
        RestClient client = RestClient.create("http://localhost:" + port);

        String body = client.get().uri("/actuator/health").retrieve().body(String.class);

        assertThat(body).contains("\"status\":\"UP\"");
    }

    @Test
    void producerSendsRecordToBroker() throws Exception {
        SendResult<String, String> result =
            kafkaTemplate.send("smoke-test", "key", "value").get(10, TimeUnit.SECONDS);

        assertThat(result.getRecordMetadata().topic()).isEqualTo("smoke-test");
    }

    @Test
    void producerIsConfiguredForIdempotentWrites() {
        Map<String, Object> properties = kafkaTemplate.getProducerFactory().getConfigurationProperties();

        assertThat(properties.get("acks")).hasToString("all");
        assertThat(properties.get("enable.idempotence")).hasToString("true");
    }

    @Test
    void consumerDoesNotAutoCommit() {
        Map<String, Object> properties = consumerFactory.getConfigurationProperties();

        assertThat(properties.get("enable.auto.commit")).hasToString("false");
    }
}
