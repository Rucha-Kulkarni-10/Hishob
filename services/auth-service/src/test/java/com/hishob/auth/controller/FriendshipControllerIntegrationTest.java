package com.hishob.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hishob.auth.dto.AuthResponse;
import com.hishob.auth.dto.FriendRequestResponse;
import com.hishob.auth.dto.FriendResponse;
import com.hishob.auth.dto.RegisterRequest;
import com.hishob.auth.dto.SendFriendRequest;
import com.hishob.auth.repository.FriendshipRepository;
import com.hishob.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class FriendshipControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        friendshipRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Send request successfully")
    void testSendRequestSuccessfully() throws Exception {
        AuthResponse sender = registerUser("Alice Sender", "alice.sender@example.com", "password123");
        AuthResponse recipient = registerUser("Bob Recipient", "bob.recipient@example.com", "password123");

        mockMvc.perform(post("/api/friends/requests")
                        .header("Authorization", bearerToken(sender.getAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendFriendRequest(recipient.getUser().getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.userId").value(recipient.getUser().getId().toString()))
                .andExpect(jsonPath("$.name").value("Bob Recipient"))
                .andExpect(jsonPath("$.email").value("bob.recipient@example.com"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("Cannot send request to self")
    void testCannotSendRequestToSelf() throws Exception {
        AuthResponse user = registerUser("Self User", "self.user@example.com", "password123");

        mockMvc.perform(post("/api/friends/requests")
                        .header("Authorization", bearerToken(user.getAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendFriendRequest(user.getUser().getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot send friend request to yourself"));
    }

    @Test
    @DisplayName("Cannot send duplicate pending request")
    void testCannotSendDuplicatePendingRequest() throws Exception {
        AuthResponse sender = registerUser("Pending Sender", "pending.sender@example.com", "password123");
        AuthResponse recipient = registerUser("Pending Recipient", "pending.recipient@example.com", "password123");

        sendFriendRequest(sender.getAccessToken(), recipient.getUser().getId());

        mockMvc.perform(post("/api/friends/requests")
                        .header("Authorization", bearerToken(sender.getAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendFriendRequest(recipient.getUser().getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A friend request is already pending between you and this user"));
    }

    @Test
    @DisplayName("Cannot send request when already friends")
    void testCannotSendRequestWhenAlreadyFriends() throws Exception {
        AuthResponse alice = registerUser("Alice Friend", "alice.friend@example.com", "password123");
        AuthResponse bob = registerUser("Bob Friend", "bob.friend@example.com", "password123");

        FriendRequestResponse request = sendFriendRequest(alice.getAccessToken(), bob.getUser().getId());
        acceptRequest(bob.getAccessToken(), request.getRequestId());

        mockMvc.perform(post("/api/friends/requests")
                        .header("Authorization", bearerToken(alice.getAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendFriendRequest(bob.getUser().getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You are already friends with this user"));
    }

    @Test
    @DisplayName("Recipient can view incoming request")
    void testRecipientCanViewIncomingRequest() throws Exception {
        AuthResponse sender = registerUser("Incoming Sender", "incoming.sender@example.com", "password123");
        AuthResponse recipient = registerUser("Incoming Recipient", "incoming.recipient@example.com", "password123");

        FriendRequestResponse request = sendFriendRequest(sender.getAccessToken(), recipient.getUser().getId());

        mockMvc.perform(get("/api/friends/requests/incoming")
                        .header("Authorization", bearerToken(recipient.getAccessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestId").value(request.getRequestId().toString()))
                .andExpect(jsonPath("$[0].userId").value(sender.getUser().getId().toString()))
                .andExpect(jsonPath("$[0].name").value("Incoming Sender"))
                .andExpect(jsonPath("$[0].email").value("incoming.sender@example.com"));
    }

    @Test
    @DisplayName("Requester can view outgoing request")
    void testRequesterCanViewOutgoingRequest() throws Exception {
        AuthResponse sender = registerUser("Outgoing Sender", "outgoing.sender@example.com", "password123");
        AuthResponse recipient = registerUser("Outgoing Recipient", "outgoing.recipient@example.com", "password123");

        FriendRequestResponse request = sendFriendRequest(sender.getAccessToken(), recipient.getUser().getId());

        mockMvc.perform(get("/api/friends/requests/outgoing")
                        .header("Authorization", bearerToken(sender.getAccessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestId").value(request.getRequestId().toString()))
                .andExpect(jsonPath("$[0].userId").value(recipient.getUser().getId().toString()))
                .andExpect(jsonPath("$[0].name").value("Outgoing Recipient"))
                .andExpect(jsonPath("$[0].email").value("outgoing.recipient@example.com"));
    }

    @Test
    @DisplayName("Recipient can accept request")
    void testRecipientCanAcceptRequest() throws Exception {
        AuthResponse sender = registerUser("Accept Sender", "accept.sender@example.com", "password123");
        AuthResponse recipient = registerUser("Accept Recipient", "accept.recipient@example.com", "password123");

        FriendRequestResponse request = sendFriendRequest(sender.getAccessToken(), recipient.getUser().getId());

        mockMvc.perform(post("/api/friends/requests/{requestId}/accept", request.getRequestId())
                        .header("Authorization", bearerToken(recipient.getAccessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.friendshipId").value(request.getRequestId().toString()))
                .andExpect(jsonPath("$.userId").value(sender.getUser().getId().toString()))
                .andExpect(jsonPath("$.name").value("Accept Sender"))
                .andExpect(jsonPath("$.email").value("accept.sender@example.com"));
    }

    @Test
    @DisplayName("Non-recipient cannot accept request")
    void testNonRecipientCannotAcceptRequest() throws Exception {
        AuthResponse sender = registerUser("Blocked Accept Sender", "blocked.accept.sender@example.com", "password123");
        AuthResponse recipient = registerUser("Blocked Accept Recipient", "blocked.accept.recipient@example.com", "password123");
        AuthResponse outsider = registerUser("Accept Outsider", "accept.outsider@example.com", "password123");

        FriendRequestResponse request = sendFriendRequest(sender.getAccessToken(), recipient.getUser().getId());

        mockMvc.perform(post("/api/friends/requests/{requestId}/accept", request.getRequestId())
                        .header("Authorization", bearerToken(outsider.getAccessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only the recipient can accept a friend request"));
    }

    @Test
    @DisplayName("Recipient can reject request")
    void testRecipientCanRejectRequest() throws Exception {
        AuthResponse sender = registerUser("Reject Sender", "reject.sender@example.com", "password123");
        AuthResponse recipient = registerUser("Reject Recipient", "reject.recipient@example.com", "password123");

        FriendRequestResponse request = sendFriendRequest(sender.getAccessToken(), recipient.getUser().getId());

        mockMvc.perform(post("/api/friends/requests/{requestId}/reject", request.getRequestId())
                        .header("Authorization", bearerToken(recipient.getAccessToken())))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Non-recipient cannot reject request")
    void testNonRecipientCannotRejectRequest() throws Exception {
        AuthResponse sender = registerUser("Reject Sender 2", "reject.sender2@example.com", "password123");
        AuthResponse recipient = registerUser("Reject Recipient 2", "reject.recipient2@example.com", "password123");
        AuthResponse outsider = registerUser("Reject Outsider", "reject.outsider@example.com", "password123");

        FriendRequestResponse request = sendFriendRequest(sender.getAccessToken(), recipient.getUser().getId());

        mockMvc.perform(post("/api/friends/requests/{requestId}/reject", request.getRequestId())
                        .header("Authorization", bearerToken(outsider.getAccessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only the recipient can reject a friend request"));
    }

    @Test
    @DisplayName("Requester can cancel request")
    void testRequesterCanCancelRequest() throws Exception {
        AuthResponse sender = registerUser("Cancel Sender", "cancel.sender@example.com", "password123");
        AuthResponse recipient = registerUser("Cancel Recipient", "cancel.recipient@example.com", "password123");

        FriendRequestResponse request = sendFriendRequest(sender.getAccessToken(), recipient.getUser().getId());

        mockMvc.perform(delete("/api/friends/requests/{requestId}/cancel", request.getRequestId())
                        .header("Authorization", bearerToken(sender.getAccessToken())))
                .andExpect(status().isNoContent());

        assertThat(friendshipRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Non-requester cannot cancel request")
    void testNonRequesterCannotCancelRequest() throws Exception {
        AuthResponse sender = registerUser("Cancel Sender 2", "cancel.sender2@example.com", "password123");
        AuthResponse recipient = registerUser("Cancel Recipient 2", "cancel.recipient2@example.com", "password123");

        FriendRequestResponse request = sendFriendRequest(sender.getAccessToken(), recipient.getUser().getId());

        mockMvc.perform(delete("/api/friends/requests/{requestId}/cancel", request.getRequestId())
                        .header("Authorization", bearerToken(recipient.getAccessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only the requester can cancel their friend request"));
    }

    @Test
    @DisplayName("Accepted friendship appears in both users friend lists")
    void testAcceptedFriendshipAppearsInBothUsersFriendLists() throws Exception {
        AuthResponse alice = registerUser("Friends Alice", "friends.alice@example.com", "password123");
        AuthResponse bob = registerUser("Friends Bob", "friends.bob@example.com", "password123");

        FriendRequestResponse request = sendFriendRequest(alice.getAccessToken(), bob.getUser().getId());
        acceptRequest(bob.getAccessToken(), request.getRequestId());

        mockMvc.perform(get("/api/friends")
                        .header("Authorization", bearerToken(alice.getAccessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].friendshipId").value(request.getRequestId().toString()))
                .andExpect(jsonPath("$[0].userId").value(bob.getUser().getId().toString()))
                .andExpect(jsonPath("$[0].name").value("Friends Bob"));

        mockMvc.perform(get("/api/friends")
                        .header("Authorization", bearerToken(bob.getAccessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].friendshipId").value(request.getRequestId().toString()))
                .andExpect(jsonPath("$[0].userId").value(alice.getUser().getId().toString()))
                .andExpect(jsonPath("$[0].name").value("Friends Alice"));
    }

    @Test
    @DisplayName("Friend can be removed")
    void testFriendCanBeRemoved() throws Exception {
        AuthResponse alice = registerUser("Remove Alice", "remove.alice@example.com", "password123");
        AuthResponse bob = registerUser("Remove Bob", "remove.bob@example.com", "password123");

        FriendRequestResponse request = sendFriendRequest(alice.getAccessToken(), bob.getUser().getId());
        acceptRequest(bob.getAccessToken(), request.getRequestId());

        mockMvc.perform(delete("/api/friends/{friendUserId}", bob.getUser().getId())
                        .header("Authorization", bearerToken(alice.getAccessToken())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/friends")
                        .header("Authorization", bearerToken(alice.getAccessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/friends")
                        .header("Authorization", bearerToken(bob.getAccessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("Cannot remove someone who is not a friend")
    void testCannotRemoveSomeoneWhoIsNotAFriend() throws Exception {
        AuthResponse alice = registerUser("Non Friend Alice", "non.friend.alice@example.com", "password123");
        AuthResponse bob = registerUser("Non Friend Bob", "non.friend.bob@example.com", "password123");

        mockMvc.perform(delete("/api/friends/{friendUserId}", bob.getUser().getId())
                        .header("Authorization", bearerToken(alice.getAccessToken())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("User search returns no contact details for unrelated users")
    void testUserSearchWorks() throws Exception {
        AuthResponse searcher = registerUser("Searcher Person", "searcher@example.com", "password123");
        registerUser("Sarah Example", "sarah@example.com", "password123");
        registerUser("Mark Example", "mark@example.com", "password123");

        mockMvc.perform(get("/api/users/search")
                        .header("Authorization", bearerToken(searcher.getAccessToken()))
                        .param("q", "sarah"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Sarah Example"))
                .andExpect(jsonPath("$[0].userId").isNotEmpty())
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    @DisplayName("Current user excluded from search")
    void testCurrentUserExcludedFromSearch() throws Exception {
        AuthResponse self = registerUser("Self Search", "self.search@example.com", "password123");
        registerUser("Other Search", "other.search@example.com", "password123");

        mockMvc.perform(get("/api/users/search")
                        .header("Authorization", bearerToken(self.getAccessToken()))
                        .param("q", "search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Other Search"))
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @Test
    @DisplayName("Blocked relationship prevents request")
    void testBlockedRelationshipPreventsRequest() throws Exception {
        AuthResponse blocker = registerUser("Blocker User", "blocker@example.com", "password123");
        AuthResponse blocked = registerUser("Blocked User", "blocked@example.com", "password123");

        mockMvc.perform(post("/api/friends/{targetUserId}/block", blocked.getUser().getId())
                        .header("Authorization", bearerToken(blocker.getAccessToken())))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/friends/requests")
                        .header("Authorization", bearerToken(blocked.getAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendFriendRequest(blocker.getUser().getId()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Unable to send friend request to this user"));
    }

    @Test
    @DisplayName("Canonical pair prevents A-B and B-A duplicates")
    void testCanonicalPairPreventsReverseDuplicates() throws Exception {
        AuthResponse alice = registerUser("Canonical Alice", "canonical.alice@example.com", "password123");
        AuthResponse bob = registerUser("Canonical Bob", "canonical.bob@example.com", "password123");

        sendFriendRequest(alice.getAccessToken(), bob.getUser().getId());

        mockMvc.perform(post("/api/friends/requests")
                        .header("Authorization", bearerToken(bob.getAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendFriendRequest(alice.getUser().getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A friend request is already pending between you and this user"));

        assertThat(friendshipRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Recipient must be an existing registered user")
    void testCannotSendRequestToUnknownUser() throws Exception {
        AuthResponse sender = registerUser("Unknown Recipient Sender", "unknown.recipient.sender@example.com", "password123");

        mockMvc.perform(post("/api/friends/requests")
                        .header("Authorization", bearerToken(sender.getAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendFriendRequest(UUID.randomUUID()))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A rejected request can be safely retried without creating a reverse row")
    void testRejectedRequestCanBeRetried() throws Exception {
        AuthResponse requester = registerUser("Retry Requester", "retry.requester@example.com", "password123");
        AuthResponse recipient = registerUser("Retry Recipient", "retry.recipient@example.com", "password123");
        FriendRequestResponse original = sendFriendRequest(requester.getAccessToken(), recipient.getUser().getId());

        mockMvc.perform(post("/api/friends/requests/{requestId}/reject", original.getRequestId())
                        .header("Authorization", bearerToken(recipient.getAccessToken())))
                .andExpect(status().isNoContent());

        FriendRequestResponse retried = sendFriendRequest(requester.getAccessToken(), recipient.getUser().getId());

        assertThat(retried.getRequestId()).isEqualTo(original.getRequestId());
        assertThat(friendshipRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Either participant can block an existing relationship")
    void testEitherParticipantCanBlockRelationship() throws Exception {
        AuthResponse requester = registerUser("Block Retry Requester", "block.retry.requester@example.com", "password123");
        AuthResponse recipient = registerUser("Block Retry Recipient", "block.retry.recipient@example.com", "password123");
        FriendRequestResponse request = sendFriendRequest(requester.getAccessToken(), recipient.getUser().getId());
        acceptRequest(recipient.getAccessToken(), request.getRequestId());

        mockMvc.perform(post("/api/friends/{targetUserId}/block", requester.getUser().getId())
                        .header("Authorization", bearerToken(recipient.getAccessToken())))
                .andExpect(status().isNoContent());

        assertThat(friendshipRepository.findAll()).hasSize(1);
        assertThat(friendshipRepository.findAll().get(0).getStatus().name()).isEqualTo("BLOCKED");
    }

    private AuthResponse registerUser(String name, String email, String password) throws Exception {
        RegisterRequest request = new RegisterRequest(name, email, password);
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    private FriendRequestResponse sendFriendRequest(String accessToken, UUID recipientUserId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/friends/requests")
                        .header("Authorization", bearerToken(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendFriendRequest(recipientUserId))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), FriendRequestResponse.class);
    }

    private FriendResponse acceptRequest(String accessToken, UUID requestId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/friends/requests/{requestId}/accept", requestId)
                        .header("Authorization", bearerToken(accessToken)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), FriendResponse.class);
    }

    private String bearerToken(String accessToken) {
        return "Bearer " + accessToken;
    }
}
