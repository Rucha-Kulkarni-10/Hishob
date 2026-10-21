package com.hishob.discovery;

import com.netflix.eureka.EurekaServerContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ManagementEndpointSecurityIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private EurekaServerContext eurekaServerContext;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(baseUri(path))
                .header("Accept", "application/json")
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI baseUri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    @ParameterizedTest(name = "{0} is not reachable anonymously")
    @ValueSource(strings = {
            "/actuator/env",
            "/actuator/configprops",
            "/actuator/beans",
            "/actuator/mappings",
            "/actuator/heapdump",
            "/actuator/threaddump",
            "/actuator/loggers",
            "/actuator/conditions",
            "/actuator/scheduledtasks",
            "/actuator/httptrace"
    })
    @DisplayName("Sensitive management endpoints are not exposed")
    void sensitiveEndpointsAreNotExposed(String path) throws Exception {
        assertThat(get(path).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Actuator discovery index does not publish an endpoint inventory")
    void actuatorIndexIsNotPublished() throws Exception {
        HttpResponse<String> response = get("/actuator");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("_links");
    }

    @Test
    @DisplayName("Health endpoint reports status only")
    void healthEndpointIsStatusOnly() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("status");
        assertThat(response.body())
                .doesNotContain("components")
                .doesNotContain("details")
                .doesNotContain("dataSource")
                .doesNotContain("diskSpace");
    }

    @Test
    @DisplayName("Liveness and readiness health groups stay reachable")
    void healthGroupsStayReachable() throws Exception {
        assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
        assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Shutdown endpoint is not exposed")
    void shutdownIsNotExposed() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(baseUri("/actuator/shutdown"))
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Eureka registry API still answers discovery clients")
    void eurekaRegistryApiStillWorks() throws Exception {
        HttpResponse<String> response = get("/eureka/apps");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(List.of("applications", "versions__delta", "apps__hashcode").stream()
                .anyMatch(response.body()::contains)).isTrue();
    }

    @Test
    @DisplayName("Eureka server context is still active")
    void eurekaServerContextIsLoaded() {
        assertThat(eurekaServerContext).isNotNull();
        assertThat(eurekaServerContext.getRegistry()).isNotNull();
    }
}
