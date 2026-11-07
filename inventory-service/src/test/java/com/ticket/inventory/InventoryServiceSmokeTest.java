package com.ticket.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class InventoryServiceSmokeTest {

    @Value("${local.server.port}")
    private int port;

    @Test
    void healthEndpointReportsUp() {
        RestClient client = RestClient.create("http://localhost:" + port);

        String body = client.get().uri("/actuator/health").retrieve().body(String.class);

        assertThat(body).contains("\"status\":\"UP\"");
    }
}
