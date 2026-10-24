package com.hishob.auth.security;

import com.hishob.auth.entity.User;
import com.hishob.auth.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import javax.crypto.SecretKey;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises Auth's internal trust boundary over real HTTP against embedded Tomcat. Auth is the one
 * service whose internal-only surface is not under {@code /internal/**}: {@code POST
 * /api/auth/users/batch} is guarded by the same token and authority, so the verb scoping of that
 * matcher is checked here with real requests rather than with a mocked request stub.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthServiceApiTrustBoundaryTest {

    private static final String TEST_SECRET = "hishob-test-jwt-secret-for-automated-tests-only-0123456789abcdef";
    private static final String INTERNAL_TOKEN = "hishob-test-internal-token-for-automated-tests-only";
    private static final String INTERNAL_TOKEN_SAME_LENGTH = "hishob-test-internal-token-for-automated-tests-onlX";

    private static final String BATCH_PATH = "/api/auth/users/batch";
    private static final String INTERNAL_PATH = "/internal/friendships/accepted/validate";
    private static final String USER_PATH = "/api/auth/me";
    private static final String EMPTY_BATCH_BODY = "[]";
    private static final String VALIDATE_BODY =
            "{\"currentUserId\":\"" + UUID.randomUUID() + "\",\"userIds\":[]}";

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    private String userJwt;

    @BeforeEach
    void seedUser() {
        userRepository.deleteAll();
        User user = userRepository.save(
                new User("Boundary Tester", "boundary@test.com", "unused-for-bearer-auth"));
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        userJwt = Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId().toString())
                .claim("name", user.getName())
                .issuedAt(new Date(now))
                .expiration(new Date(now + 3_600_000L))
                .signWith(key)
                .compact();
    }

    /** Sends the request target verbatim, so no HTTP client can normalise the shape under test. */
    private int send(String method, String path, String internalToken, String bearerJwt, String body)
            throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 5000);
            socket.setSoTimeout(15000);
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            out.print(method + " " + path + " HTTP/1.1\r\n");
            out.print("Host: 127.0.0.1:" + port + "\r\n");
            if (internalToken != null) {
                out.print("X-Internal-Token: " + internalToken + "\r\n");
            }
            if (bearerJwt != null) {
                out.print("Authorization: Bearer " + bearerJwt + "\r\n");
            }
            if (body != null) {
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                out.print("Content-Type: application/json\r\n");
                out.print("Content-Length: " + payload.length + "\r\n");
            }
            out.print("Connection: close\r\n\r\n");
            if (body != null) {
                out.print(body);
            }
            out.flush();

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String statusLine = in.readLine();
            while (in.readLine() != null) {
                // Drain so the container finishes writing before the socket closes.
            }
            assertThat(statusLine)
                    .as("%s %s must be answered by Tomcat", method, path)
                    .isNotNull();
            return Integer.parseInt(statusLine.split(" ")[1]);
        }
    }

    @Test
    @DisplayName("Positive control: a valid internal token reaches POST " + BATCH_PATH)
    void testInternalTokenReachesBatchEndpoint() throws Exception {
        assertThat(send("POST", BATCH_PATH, INTERNAL_TOKEN, null, EMPTY_BATCH_BODY)).isEqualTo(200);
    }

    @Test
    @DisplayName("Positive control: a user JWT still reaches its own endpoint")
    void testUserJwtStillReachesUserEndpoint() throws Exception {
        assertThat(send("GET", USER_PATH, null, userJwt, null))
                .as("Seeding must produce a JWT that authenticates, otherwise the 401 cases prove nothing")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("A user JWT cannot use the internal-only batch endpoint")
    void testUserJwtCannotReachBatchEndpoint() throws Exception {
        assertThat(send("POST", BATCH_PATH, null, userJwt, EMPTY_BATCH_BODY))
                .as("The internal filter rejects the batch path before the JWT filter runs")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("Anonymous access to the batch endpoint is rejected")
    void testAnonymousCannotReachBatchEndpoint() throws Exception {
        assertThat(send("POST", BATCH_PATH, null, null, EMPTY_BATCH_BODY)).isEqualTo(401);
    }

    @Test
    @DisplayName("Blank, wrong and same-length-different internal tokens on batch are rejected")
    void testBadInternalTokensRejectedOnBatch() throws Exception {
        assertThat(send("POST", BATCH_PATH, "   ", null, EMPTY_BATCH_BODY)).isEqualTo(401);
        assertThat(send("POST", BATCH_PATH, "definitely-not-the-internal-token", null, EMPTY_BATCH_BODY))
                .isEqualTo(401);

        assertThat(INTERNAL_TOKEN_SAME_LENGTH).hasSameSizeAs(INTERNAL_TOKEN);
        assertThat(send("POST", BATCH_PATH, INTERNAL_TOKEN_SAME_LENGTH, null, EMPTY_BATCH_BODY))
                .as("A same-length token differing only in the final byte must not pass the constant-time compare")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("The batch matcher is POST-scoped: a GET on that path is not sent through the internal filter")
    void testBatchMatcherAppliesOnlyToTheMappedVerb() throws Exception {
        int asUser = send("GET", BATCH_PATH, null, userJwt, null);

        assertThat(asUser)
                .as("GET " + BATCH_PATH + " must not be refused by the internal filter; only POST is guarded")
                .isNotEqualTo(401);
        assertThat(asUser)
                .as("MVC resolves the GET to the mapped GET /api/auth/users/{userId} handler, which rejects the non-UUID 'batch' segment")
                .isEqualTo(400);

        assertThat(send("GET", BATCH_PATH, INTERNAL_TOKEN, null, null))
                .as("The internal authority is granted only for POST, so an internal caller cannot use GET on this path")
                .isEqualTo(403);
    }

    @Test
    @DisplayName("Internal /internal/** surface keeps both sides: token accepted, user JWT refused")
    void testInternalFriendshipSurface() throws Exception {
        assertThat(send("POST", INTERNAL_PATH, INTERNAL_TOKEN, null, VALIDATE_BODY)).isEqualTo(200);
        assertThat(send("POST", INTERNAL_PATH, null, userJwt, VALIDATE_BODY)).isEqualTo(401);
        assertThat(send("POST", INTERNAL_PATH, null, null, VALIDATE_BODY)).isEqualTo(401);
    }

    @Test
    @DisplayName("An internal token alone does not authenticate an ordinary user endpoint")
    void testInternalTokenCannotReachUserEndpoint() throws Exception {
        assertThat(send("GET", USER_PATH, INTERNAL_TOKEN, null, null))
                .as("X-Internal-Token authenticates nothing outside the internal matchers")
                .isEqualTo(403);
    }
}
