package com.telecom.orders.integration;

import com.telecom.orders.dto.OrderResponse;
import com.telecom.orders.dto.PlaceOrderRequest;
import com.telecom.orders.entity.OrderStatus;
import com.telecom.orders.repository.OrderRepository;
import com.telecom.orders.service.OrderWorkflowService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** PROD profile on MySQL 8.4: Flyway schema, BINARY(16) ids, @Version, full order lifecycle. Skipped without Docker. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("prod")
@Testcontainers(disabledWithoutDocker = true)
class MySqlFlywayIT {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void fastProvisioning(DynamicPropertyRegistry registry) {
        registry.add("telecom.simulation.provisioning-latency", () -> "200ms");
    }

    @Autowired
    private Flyway flyway;
    @Autowired
    private OrderWorkflowService workflow;
    @Autowired
    private OrderRepository orders;

    @Test
    void orderLifecycleOnMySql() throws Exception {
        assertThat(Arrays.stream(flyway.info().applied()).map(m -> m.getVersion().getVersion())).containsExactly("1");

        OrderResponse created = workflow.placeOrder(
                new PlaceOrderRequest("MySQL IT", "mysql-it@example.com", "UNL-599", "KA")).get();

        assertThat(created.status()).isEqualTo(OrderStatus.PROVISIONING);
        await().atMost(Duration.ofSeconds(10))
               .until(() -> orders.findById(created.id()).orElseThrow().getStatus() == OrderStatus.ACTIVE);
        assertThat(orders.findById(created.id()).orElseThrow().getVersion()).isEqualTo(1L);
    }
}
