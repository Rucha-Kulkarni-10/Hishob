package com.hishob.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=native",
                "eureka.client.enabled=false",
                "eureka.client.register-with-eureka=false",
                "eureka.client.fetch-registry=false",
                "spring.cloud.config.server.native.search-locations=classpath:/config-test/"
        })
class ManagementEndpointSecurityIntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Accept", "application/json")
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("Actuator is not on the Config Server classpath, so no management index exists")
    void actuatorIndexIsAbsent() throws Exception {
        HttpResponse<String> response = get("/actuator");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("_links");
    }

    @ParameterizedTest(name = "{0} carries no management document")
    @ValueSource(strings = {
            "/actuator/env",
            "/actuator/configprops",
            "/actuator/beans",
            "/actuator/mappings",
            "/actuator/heapdump",
            "/actuator/loggers",
            "/actuator/conditions",
            "/actuator/threaddump",
            "/actuator/scheduledtasks",
            "/actuator/httptrace"
    })
    @DisplayName("Actuator-shaped paths never return an environment, bean or mapping inventory")
    void actuatorShapedPathsCarryNoManagementDocument(String path) throws Exception {
        String body = get(path).body();

        // Profile names are echoed back verbatim (e.g. /actuator/beans -> "profiles":["beans"]),
        // so only management-document markers that cannot collide with a profile name are checked.
        assertThat(body)
                .doesNotContain("activeProfiles")
                .doesNotContain("\"contexts\"")
                .doesNotContain("dispatcherServlet")
                .doesNotContain("\"levels\"");
        assertThat(body).doesNotContain("sample.feature-enabled");
    }

    @Test
    @DisplayName("Actuator-shaped paths resolve as /{name}/{profile} and expose only the shared defaults")
    void lookalikePathsServeOnlySharedDefaults() throws Exception {
        HttpResponse<String> response = get("/actuator/env");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("propertySources")
                .contains("application.yml")
                .doesNotContain("sample-service.yml");
    }

    @Test
    @DisplayName("A legitimate client still retrieves its own configuration")
    void legitimateRetrievalStillWorks() throws Exception {
        HttpResponse<String> response = get("/sample-service/default");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("sample-service")
                .contains("sample.feature-enabled")
                .contains("config-test/sample-service.yml");
    }
}
