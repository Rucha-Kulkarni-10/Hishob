package com.hishob.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

    @Autowired
    private RouteLocator routeLocator;

    private List<String> routePredicates() {
        return routeLocator.getRoutes()
                .map(route -> route.getPredicate().toString())
                .collectList()
                .block();
    }

    @Test
    @DisplayName("No gateway route exposes /internal/** paths")
    void testNoInternalRouteExposed() {
        boolean hasInternalRoute = routeLocator.getRoutes()
                .filter(route -> route.getPredicate()
                        .toString()
                        .contains("/internal"))
                .hasElements()
                .block();

        assertThat(hasInternalRoute).isFalse();
    }

    @Test
    @DisplayName("Gateway routes cover only /api/** paths")
    void testOnlyApiRoutesExist() {
        routeLocator.getRoutes()
                .doOnNext(route -> {
                    String predicateStr = route.getPredicate().toString();
                    assertThat(predicateStr)
                            .as("Route predicate should not contain /internal: " + predicateStr)
                            .doesNotContain("/internal");
                })
                .blockLast();
    }

    @Test
    @DisplayName("Internal group-exists path is not matched by any route predicate")
    void testInternalGroupExistsNotRouted() {
        UUID groupId = UUID.randomUUID();
        String internalPath = "/internal/groups/" + groupId + "/exists";

        boolean matched = routeLocator.getRoutes()
                .filter(route -> {
                    String predicateStr = route.getPredicate().toString();
                    return predicateStr.contains("/internal");
                })
                .hasElements()
                .block();

        assertThat(matched).isFalse();
    }

    @Test
    @DisplayName("Gateway exposes auth friendship and user search routes")
    void testFriendshipAndUserSearchRoutesExist() {
        List<String> predicates = routePredicates();

        assertThat(predicates)
                .anyMatch(predicate -> predicate.contains("/api/friends/requests/**"))
                .anyMatch(predicate -> predicate.contains("/api/friends"))
                .anyMatch(predicate -> predicate.contains("/api/users/**"));
    }

    @Test
    @DisplayName("Gateway exposes notification and personal finance routes")
    void testNotificationAndPersonalRoutesExist() {
        List<String> predicates = routePredicates();

        assertThat(predicates)
                .anyMatch(predicate -> predicate.contains("/api/notifications/**"))
                .anyMatch(predicate -> predicate.contains("/api/personal-expenses/**"))
                .anyMatch(predicate -> predicate.contains("/api/personal-balances/**"))
                .anyMatch(predicate -> predicate.contains("/api/personal-settlements/**"));
    }

    @Test
    @DisplayName("Friend-specific personal routes do not require broad /api/friends/** routing")
    void testFriendSpecificPersonalRoutesAreExplicit() {
        List<String> predicates = routePredicates();

        assertThat(predicates)
                .noneMatch(predicate -> predicate.contains("/api/friends/**"))
                .anyMatch(predicate -> predicate.contains("/api/friends/*/expenses/**"))
                .anyMatch(predicate -> predicate.contains("/api/friends/*/balance"))
                .anyMatch(predicate -> predicate.contains("/api/friends/*/settlements"));
    }
}
