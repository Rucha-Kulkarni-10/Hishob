package com.hishob.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hishob.auth.entity.User;
import com.hishob.auth.repository.UserRepository;
import com.hishob.auth.security.InternalAuthFilter;
import com.hishob.auth.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B-09: {@code GET /api/auth/users/{userId}} used to answer any authenticated caller, which let one
 * account walk UUID space and harvest names. Access now follows the relationship model the
 * friendship endpoints already enforce, and refused lookups are answered exactly like unknown ids.
 */
@SpringBootTest
class UserSummaryAccessIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Value("${hishob.internal.token}")
    private String internalToken;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        userRepository.deleteAll();
    }

    private User seed(String name) {
        return userRepository.saveAndFlush(new User(name, UUID.randomUUID() + "@summary.test", "not-a-real-hash"));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user.getId(), user.getEmail(), user.getName());
    }

    private void becomeFriends(User requester, User recipient) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/friends/requests")
                        .header("Authorization", bearer(requester))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("recipientUserId", recipient.getId()))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        mockMvc.perform(post("/api/friends/requests/" + body.get("requestId").asText() + "/accept")
                        .header("Authorization", bearer(recipient)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A user reading their own summary still succeeds and exposes no contact fields")
    void selfSummary_IsStillReturned() throws Exception {
        User alice = seed("Alice Self");

        mockMvc.perform(get("/api/auth/users/" + alice.getId())
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alice.getId().toString()))
                .andExpect(jsonPath("$.name").value("Alice Self"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(content().string(not(containsString("@"))));
    }

    @Test
    @DisplayName("An accepted friend may read the summary")
    void acceptedFriend_CanReadSummary() throws Exception {
        User alice = seed("Alice Friend");
        User bob = seed("Bob Friend");
        becomeFriends(alice, bob);

        mockMvc.perform(get("/api/auth/users/" + alice.getId())
                        .header("Authorization", bearer(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Friend"))
                .andExpect(jsonPath("$.email").doesNotExist());

        mockMvc.perform(get("/api/auth/users/" + bob.getId())
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bob Friend"));
    }

    @Test
    @DisplayName("A pending request is not an accepted friendship")
    void pendingRequest_CannotReadSummary() throws Exception {
        User alice = seed("Alice Pending");
        User bob = seed("Bob Pending");

        mockMvc.perform(post("/api/friends/requests")
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("recipientUserId", bob.getId()))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/auth/users/" + alice.getId())
                        .header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());

        // control: the same caller can still read its own summary, so the 404 is relationship-scoped
        mockMvc.perform(get("/api/auth/users/" + bob.getId())
                        .header("Authorization", bearer(bob)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A blocked relationship is not an accepted friendship")
    void blockedRelationship_CannotReadSummary() throws Exception {
        User alice = seed("Alice Blocked");
        User bob = seed("Bob Blocker");

        mockMvc.perform(post("/api/friends/" + alice.getId() + "/block")
                        .header("Authorization", bearer(bob)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/users/" + alice.getId())
                        .header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("An unrelated caller gets the identical answer for a real and an unknown id")
    void strangerRefusal_IsIndistinguishableFromUnknownId() throws Exception {
        User alice = seed("Alice Stranger");
        User friend = seed("Known Friend");
        User stranger = seed("Hidden Stranger");
        becomeFriends(alice, friend);

        // control: this caller can reach another user's summary when the relationship allows it
        mockMvc.perform(get("/api/auth/users/" + friend.getId())
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isOk());

        String inaccessible = summarizeRefusal(stranger.getId(), alice);
        String unknown = summarizeRefusal(UUID.randomUUID(), alice);

        assertThat(inaccessible).isEqualTo(unknown);
        assertThat(inaccessible).doesNotContain("Hidden Stranger");
    }

    private String summarizeRefusal(UUID targetId, User viewer) throws Exception {
        String raw = mockMvc.perform(get("/api/auth/users/" + targetId)
                        .header("Authorization", bearer(viewer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode body = objectMapper.readTree(raw);
        List<String> fields = new ArrayList<>();
        body.fieldNames().forEachRemaining(fields::add);
        Collections.sort(fields);

        // the echoed id is the caller's own input, so it may differ; the timestamp is wall-clock and
        // is deliberately excluded. Everything that can be inferred about the target must match.
        return "status=" + body.path("status").asInt()
                + " error=" + body.path("error").asText()
                + " message=" + body.path("message").asText().replace(targetId.toString(), "<id>")
                + " path=" + body.path("path").asText().replace(targetId.toString(), "<id>")
                + " fields=" + fields;
    }

    @Test
    @DisplayName("Guessing ids cannot separate existing accounts from absent ones")
    void enumerationByGuessedIds_YieldsOnlyNotFound() throws Exception {
        User alice = seed("Alice Enumerator");
        User[] targets = new User[5];
        for (int i = 0; i < targets.length; i++) {
            targets[i] = seed("Target " + i);
        }

        for (User target : targets) {
            mockMvc.perform(get("/api/auth/users/" + target.getId())
                            .header("Authorization", bearer(alice)))
                    .andExpect(status().isNotFound());
        }
        for (int i = 0; i < targets.length; i++) {
            mockMvc.perform(get("/api/auth/users/" + UUID.randomUUID())
                            .header("Authorization", bearer(alice)))
                    .andExpect(status().isNotFound());
        }

        // control: all five targets are genuinely present and searchable by their accepted friend
        for (User target : targets) {
            becomeFriends(alice, target);
            mockMvc.perform(get("/api/auth/users/" + target.getId())
                            .header("Authorization", bearer(alice)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("Anonymous and internal-token callers keep their established answers")
    void anonymousAndInternalCallers_AreUnchanged() throws Exception {
        User alice = seed("Alice Anonymous Probe");

        mockMvc.perform(get("/api/auth/users/" + alice.getId()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/auth/users/" + alice.getId())
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, internalToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("B-08/B-1 regression: the summary endpoints keep their trust boundaries")
    void batchAndExistLookups_StillRequireServiceCredentials() throws Exception {
        User alice = seed("Alice Batch");

        mockMvc.perform(post("/api/auth/users/batch")
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(alice.getId()))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/users/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(alice.getId()))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/users/batch")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(alice.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Alice Batch"))
                .andExpect(jsonPath("$[0].email").doesNotExist());

        mockMvc.perform(get("/api/auth/users/" + alice.getId() + "/exists"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/users/" + alice.getId() + "/exists")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, internalToken))
                .andExpect(status().isOk());
    }
}
