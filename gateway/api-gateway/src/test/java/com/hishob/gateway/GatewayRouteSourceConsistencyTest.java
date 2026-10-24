package com.hishob.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1 regression tests: the three gateway route tables must stay identical and must resolve
 * their targets through deployment-supplied URL variables, so a Config Server property source can
 * never reintroduce hardcoded Eureka routes.
 */
class GatewayRouteSourceConsistencyTest {

    private static final List<String> ROUTE_SOURCES = List.of(
            "gateway/api-gateway/src/main/resources/application.yml",
            "infrastructure/config-server/config/api-gateway.yml",
            "infrastructure/config-server/src/main/resources/config/api-gateway.yml");

    private static final int EXPECTED_ROUTE_COUNT = 19;

    private record Route(String id, String uri, List<String> predicates) {

        String path() {
            return predicates.stream()
                    .filter(predicate -> predicate.startsWith("Path="))
                    .findFirst()
                    .map(predicate -> predicate.substring("Path=".length()))
                    .orElseThrow(() -> new AssertionError("Route " + id + " declares no Path predicate"));
        }

        /** Service the route resolves to when no environment variable overrides the placeholder. */
        String fallbackService() {
            String target = uri;
            if (target.startsWith("${") && target.contains(":")) {
                target = target.substring(target.indexOf(':') + 1, target.length() - 1);
            }
            int schemeEnd = target.indexOf("://");
            return schemeEnd < 0 ? target : target.substring(schemeEnd + 3);
        }

        String placeholderVariable() {
            if (!uri.startsWith("${")) {
                return null;
            }
            int end = uri.indexOf(':');
            return end < 0 ? null : uri.substring(2, end);
        }
    }

    @Test
    @DisplayName("All gateway route sources declare the same routes in the same order")
    void testRouteSourcesAreIdentical() {
        List<Route> reference = routes(ROUTE_SOURCES.get(0));

        assertThat(reference).hasSize(EXPECTED_ROUTE_COUNT);

        for (String source : ROUTE_SOURCES) {
            assertThat(routes(source))
                    .as("Route table drift in " + source)
                    .isEqualTo(reference);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "gateway/api-gateway/src/main/resources/application.yml",
            "infrastructure/config-server/config/api-gateway.yml",
            "infrastructure/config-server/src/main/resources/config/api-gateway.yml"
    })
    @DisplayName("Every route target is an environment-variable placeholder, never a bare lb:// URI")
    void testEveryRouteIsPlaceholderDriven(String source) {
        assertThat(routes(source)).allSatisfy(route -> {
            assertThat(route.placeholderVariable())
                    .as("Route " + route.id() + " must resolve its URI from a deployment URL variable")
                    .isIn("AUTH_SERVICE_URL", "EXPENSE_SERVICE_URL", "SETTLEMENT_SERVICE_URL");
            assertThat(route.uri()).doesNotContain("BALANCE_SERVICE_URL", "NOTIFICATION_SERVICE_URL", "GROUP_SERVICE_URL");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "gateway/api-gateway/src/main/resources/application.yml",
            "infrastructure/config-server/config/api-gateway.yml",
            "infrastructure/config-server/src/main/resources/config/api-gateway.yml"
    })
    @DisplayName("Balance and notification paths fall back to their consolidated owning services")
    void testConsolidatedOwnershipFallbacks(String source) {
        List<Route> routes = routes(source);

        assertThat(routeFor(routes, "/api/personal-balances/**").fallbackService()).isEqualTo("settlement-service");
        assertThat(routeFor(routes, "/api/friends/*/balance").fallbackService()).isEqualTo("settlement-service");
        assertThat(routeFor(routes, "/api/groups/*/balances").fallbackService()).isEqualTo("settlement-service");
        assertThat(routeFor(routes, "/api/notifications/**").placeholderVariable()).isEqualTo("AUTH_SERVICE_URL");
        assertThat(routeFor(routes, "/api/notifications/**").fallbackService()).isEqualTo("auth-service");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "gateway/api-gateway/src/main/resources/application.yml",
            "infrastructure/config-server/config/api-gateway.yml",
            "infrastructure/config-server/src/main/resources/config/api-gateway.yml"
    })
    @DisplayName("No route resolves to a retired service, and the group catch-all stays last")
    void testNoRetiredServiceTargetsAndCatchAllStaysLast(String source) {
        List<Route> routes = routes(source);

        assertThat(routes).allSatisfy(route ->
                assertThat(route.fallbackService()).isIn("auth-service", "expense-service", "settlement-service"));

        assertThat(routes.get(routes.size() - 1).path()).isEqualTo("/api/groups/**");
        assertThat(routes.get(routes.size() - 1).placeholderVariable()).isEqualTo("EXPENSE_SERVICE_URL");
        assertThat(routes.get(routes.size() - 1).fallbackService()).isEqualTo("expense-service");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "gateway/api-gateway/src/main/resources/application.yml",
            "infrastructure/config-server/config/api-gateway.yml",
            "infrastructure/config-server/src/main/resources/config/api-gateway.yml"
    })
    @DisplayName("Every route document strips the inbound internal-service token and routes no /internal path")
    void testInternalTokenStripAndNoInternalRouteInEverySource(String source) {
        String document;
        try {
            document = Files.readString(repoRoot().resolve(source));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read gateway route document " + source, e);
        }

        assertThat(document)
                .as("X-Internal-Token must be stripped in " + source)
                .contains("default-filters:")
                .contains("- RemoveRequestHeader=X-Internal-Token");

        assertThat(routes(source))
                .as("No gateway route may expose an internal path")
                .allSatisfy(route -> assertThat(route.path()).doesNotStartWith("/internal"));
    }

    private Route routeFor(List<Route> routes, String path) {
        return routes.stream()
                .filter(route -> path.equals(route.path()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No gateway route declares Path=" + path));
    }

    private List<Route> routes(String relativeConfigPath) {
        Path file = repoRoot().resolve(relativeConfigPath);
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read gateway route table " + file, e);
        }

        List<Route> routes = new ArrayList<>();
        String id = null;
        String uri = null;
        List<String> predicates = new ArrayList<>();

        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("- id:")) {
                if (id != null && uri != null && !predicates.isEmpty()) {
                    routes.add(new Route(id, uri, List.copyOf(predicates)));
                }
                id = line.substring("- id:".length()).trim();
                uri = null;
                predicates = new ArrayList<>();
            } else if (line.startsWith("uri:") && id != null) {
                uri = line.substring("uri:".length()).trim();
            } else if (line.startsWith("- Path=") && id != null && uri != null) {
                predicates.add(line.substring("- ".length()));
            } else if (line.startsWith("- Method=") && id != null && uri != null) {
                predicates.add(line.substring("- ".length()));
            }
        }

        if (id != null && uri != null && !predicates.isEmpty()) {
            routes.add(new Route(id, uri, List.copyOf(predicates)));
        }
        return routes;
    }

    private Path repoRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.exists(candidate.resolve("render.yaml"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new AssertionError("Repository root not found from " + Path.of("").toAbsolutePath());
    }
}
