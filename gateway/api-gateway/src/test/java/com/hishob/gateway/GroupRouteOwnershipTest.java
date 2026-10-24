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

class GroupRouteOwnershipTest {

    private record GatewayRoute(int order, String id, String uri, String path) {

        String service() {
            String target = uri;
            if (target.startsWith("${") && target.contains(":")) {
                target = target.substring(target.indexOf(':') + 1, target.length() - 1);
            }
            int schemeEnd = target.indexOf("://");
            return schemeEnd < 0 ? target : target.substring(schemeEnd + 3);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "gateway/api-gateway/src/main/resources/application.yml",
            "infrastructure/config-server/config/api-gateway.yml",
            "infrastructure/config-server/src/main/resources/config/api-gateway.yml"
    })
    @DisplayName("Group paths route to their owning service in every gateway route table")
    void testGroupRouteOwnership(String relativeConfigPath) {
        List<GatewayRoute> routes = routes(relativeConfigPath);

        assertThat(routeFor(routes, "/api/groups/**").id()).isEqualTo("expense-service-groups");
        assertThat(routeFor(routes, "/api/groups/**").service()).isEqualTo("expense-service");
        assertThat(routeFor(routes, "/api/groups/*/expenses/**").service()).isEqualTo("expense-service");
        assertThat(routeFor(routes, "/api/groups/*/balances").service()).isEqualTo("settlement-service");
        assertThat(routeFor(routes, "/api/groups/*/settlements/history").service()).isEqualTo("settlement-service");
        assertThat(routeFor(routes, "/api/groups/*/settlements").service()).isEqualTo("settlement-service");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "gateway/api-gateway/src/main/resources/application.yml",
            "infrastructure/config-server/config/api-gateway.yml",
            "infrastructure/config-server/src/main/resources/config/api-gateway.yml"
    })
    @DisplayName("Group catch-all is declared after every specific group path")
    void testCatchAllGroupRouteIsLastAmongGroupRoutes(String relativeConfigPath) {
        List<GatewayRoute> routes = routes(relativeConfigPath);
        int catchAll = routeFor(routes, "/api/groups/**").order();

        assertThat(routes)
                .filteredOn(route -> route.path().startsWith("/api/groups/")
                        && !"/api/groups/**".equals(route.path()))
                .allSatisfy(route -> assertThat(route.order()).isLessThan(catchAll));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "gateway/api-gateway/src/main/resources/application.yml",
            "infrastructure/config-server/config/api-gateway.yml",
            "infrastructure/config-server/src/main/resources/config/api-gateway.yml",
            "gateway/api-gateway/src/test/resources/application.yml"
    })
    @DisplayName("No gateway route sends traffic to the legacy group service")
    void testNoRouteTargetsLegacyGroupService(String relativeConfigPath) {
        assertThat(routes(relativeConfigPath))
                .allSatisfy(route -> {
                    assertThat(route.service()).doesNotContain("group-service");
                    assertThat(route.uri()).doesNotContain("GROUP_SERVICE_URL");
                });
    }

    @Test
    @DisplayName("Gateway catch-all group route override cannot point back at the legacy service")
    void testCatchAllGroupRouteUsesExpenseServiceUrlVariable() {
        GatewayRoute catchAll = routeFor(
                routes("gateway/api-gateway/src/main/resources/application.yml"),
                "/api/groups/**");

        assertThat(catchAll.uri()).isEqualTo("${EXPENSE_SERVICE_URL:lb://expense-service}");
    }

    private GatewayRoute routeFor(List<GatewayRoute> routes, String path) {
        return routes.stream()
                .filter(route -> path.equals(route.path()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No gateway route declares Path=" + path));
    }

    private List<GatewayRoute> routes(String relativeConfigPath) {
        Path file = repoRoot().resolve(relativeConfigPath);
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read gateway route table " + file, e);
        }

        List<GatewayRoute> routes = new ArrayList<>();
        String id = null;
        String uri = null;
        int order = 0;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("- id:")) {
                id = line.substring("- id:".length()).trim();
                uri = null;
            } else if (line.startsWith("uri:") && id != null) {
                uri = line.substring("uri:".length()).trim();
            } else if (line.startsWith("- Path=") && id != null && uri != null) {
                routes.add(new GatewayRoute(order++, id, uri, line.substring("- Path=".length()).trim()));
            }
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
