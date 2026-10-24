package com.hishob.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=native",
                "eureka.client.enabled=false",
                "eureka.client.register-with-eureka=false",
                "eureka.client.fetch-registry=false",
                // Mirrors the production native repository, including the loose-file copy next to
                // the module POM, so both served locations are covered.
                "spring.cloud.config.server.native.search-locations=classpath:/config/,file:./config/"
        })
class ServedRepositoryContentTest {

    private static final Pattern CREDENTIAL_SHAPED_KEY = Pattern.compile(
            "(?i)(password|passwd|secret|token|credential|api[-_]?key|access[-_]?key|private[-_]?key)");

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private JsonNode servedDocument(String path) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Accept", "application/json")
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    @ParameterizedTest(name = "anonymous retrieval of {0}/default serves no credential-shaped key")
    @ValueSource(strings = {
            "application",
            "api-gateway",
            "auth-service",
            "group-service",
            "expense-service",
            "balance-service",
            "settlement-service",
            "notification-service"
    })
    @DisplayName("The real served repository contains no credential material")
    void servedRepositoryHasNoCredentials(String appName) throws Exception {
        JsonNode document = servedDocument("/" + appName + "/default");

        List<String> offenders = new ArrayList<>();
        List<String> servedKeys = new ArrayList<>();
        for (JsonNode source : document.path("propertySources")) {
            JsonNode values = source.path("source").isMissingNode()
                    ? source.path("properties")
                    : source.path("source");
            values.fieldNames().forEachRemaining(key -> {
                // Only source name and key name are reported; values are never surfaced.
                if (CREDENTIAL_SHAPED_KEY.matcher(key).find()) {
                    offenders.add(source.path("name").asText() + " -> " + key);
                }
                if ((key.endsWith(".url") || key.endsWith(".jdbc-url"))
                        && values.get(key).asText().contains("password=")) {
                    offenders.add(source.path("name").asText() + " -> " + key + " embeds a password");
                }
                servedKeys.add(key);
            });
        }

        assertThat(offenders).isEmpty();
        assertThat(servedKeys).isNotEmpty();
    }

    @Test
    @DisplayName("An actuator-shaped application name cannot read another service's configuration")
    void actuatorShapedNameDoesNotLeakServiceConfiguration() throws Exception {
        JsonNode document = servedDocument("/actuator/env");
        List<String> sourceNames = new ArrayList<>();
        document.path("propertySources").forEach(source -> sourceNames.add(source.path("name").asText()));

        assertThat(sourceNames).isNotEmpty();
        assertThat(sourceNames).allMatch(name -> name.endsWith("application.yml"));
    }
}
