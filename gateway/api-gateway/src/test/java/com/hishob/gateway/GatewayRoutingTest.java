package com.hishob.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

    /** The stand-in upstream every route in src/test/resources/application.yml points at. */
    private static final int UPSTREAM_PORT = 9999;
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String PROBE_TOKEN = "gateway-test-internal-token-canary-value-0123456789";
    private static final String CONTROL_HEADER = "X-Gateway-Probe-Control";

    private static HttpServer upstreamSink;
    private static final List<RecordedRequest> upstreamRequests = new ArrayList<>();

    private record RecordedRequest(String path, Set<String> headerNames) {
    }

    @Autowired
    private RouteLocator routeLocator;

    @LocalServerPort
    private int gatewayPort;

    @BeforeAll
    static void startUpstreamSink() throws IOException {
        upstreamSink = HttpServer.create(new InetSocketAddress("127.0.0.1", UPSTREAM_PORT), 0);
        upstreamSink.createContext("/", GatewayRoutingTest::recordAndAnswer);
        upstreamSink.start();
    }

    @AfterAll
    static void stopUpstreamSink() {
        upstreamSink.stop(0);
    }

    private static void recordAndAnswer(HttpExchange exchange) throws IOException {
        Set<String> headers = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        headers.addAll(exchange.getRequestHeaders().keySet());
        synchronized (upstreamRequests) {
            upstreamRequests.add(new RecordedRequest(exchange.getRequestURI().getPath(), headers));
        }
        byte[] body = "upstream-sink".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @BeforeEach
    void clearRecordedRequests() {
        synchronized (upstreamRequests) {
            upstreamRequests.clear();
        }
    }

    private List<String> routePredicates() {
        return routeLocator.getRoutes()
                .map(route -> route.getPredicate().toString())
                .collectList()
                .block();
    }

    private HttpResponse<String> get(String path, String... headerPairs) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(15))
                .GET()
                .uri(URI.create("http://127.0.0.1:" + gatewayPort + path));
        for (int i = 0; i < headerPairs.length; i += 2) {
            builder.header(headerPairs[i], headerPairs[i + 1]);
        }
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private List<RecordedRequest> recorded() {
        synchronized (upstreamRequests) {
            return List.copyOf(upstreamRequests);
        }
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
    @DisplayName("A routed /api request reaches the upstream, an /internal request is refused and never forwarded")
    void testInternalGroupExistsNotRouted() throws Exception {
        UUID groupId = UUID.randomUUID();
        String internalPath = "/internal/groups/" + groupId + "/exists";

        HttpResponse<String> routedProbe = get("/api/auth/routing-control");
        assertThat(routedProbe.statusCode())
                .as("Positive control: the test route table must actually forward /api/auth/**")
                .isEqualTo(200);

        HttpResponse<String> internal = get(internalPath);

        assertThat(internal.statusCode()).isEqualTo(404);
        assertThat(recorded())
                .as("Only the control probe may reach the upstream")
                .extracting(RecordedRequest::path)
                .containsExactly("/api/auth/routing-control");
    }

    @Test
    @DisplayName("A caller-supplied X-Internal-Token is stripped before forwarding")
    void testInternalTokenHeaderIsNotForwardedUpstream() throws Exception {
        HttpResponse<String> response = get(
                "/api/auth/header-stripping",
                INTERNAL_TOKEN_HEADER, PROBE_TOKEN,
                CONTROL_HEADER, "kept");

        assertThat(response.statusCode()).isEqualTo(200);

        List<RecordedRequest> upstream = recorded();
        assertThat(upstream).hasSize(1);
        RecordedRequest forwarded = upstream.get(0);
        assertThat(forwarded.path()).isEqualTo("/api/auth/header-stripping");
        assertThat(forwarded.headerNames())
                .as("An untrusted internal token must never reach a service")
                .doesNotContain(INTERNAL_TOKEN_HEADER);
        assertThat(forwarded.headerNames())
                .as("Control: header capture works, so the absence above is a real strip and not a blind sink")
                .contains(CONTROL_HEADER);
    }

    @Test
    @DisplayName("Even a caller presenting an internal token cannot make /internal route through the gateway")
    void testInternalPathStaysUnroutedWithAToken() throws Exception {
        UUID groupId = UUID.randomUUID();
        String internalPath = "/internal/groups/" + groupId + "/members/" + UUID.randomUUID() + "/exists";

        HttpResponse<String> response = get(internalPath, INTERNAL_TOKEN_HEADER, PROBE_TOKEN);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(recorded()).isEmpty();
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
