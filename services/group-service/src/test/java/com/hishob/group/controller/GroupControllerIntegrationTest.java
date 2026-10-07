package com.hishob.group.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hishob.group.client.AuthServiceClient;
import com.hishob.group.dto.AddMemberRequest;
import com.hishob.group.dto.CreateGroupRequest;
import com.hishob.group.entity.Group;
import com.hishob.group.entity.GroupMember;
import com.hishob.group.entity.MemberRole;
import com.hishob.group.repository.GroupMemberRepository;
import com.hishob.group.repository.GroupRepository;
import com.hishob.group.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class GroupControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AuthServiceClient authServiceClient;

    private MockMvc mockMvc;

    private static final String TEST_SECRET = "d3f4u1t-s3cr3t-k3y-f0r-h1sh0b-auth-s3rv1c3-jwt-t0k3n-g3n3r4t10n-2026";
    private static final long TEST_EXPIRATION = 3600000L;

    private UUID ownerId;
    private UUID memberId;
    private UUID nonMemberId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);

        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();

        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        nonMemberId = UUID.randomUUID();
    }

    private String jwt(UUID userId, String email) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId.toString())
                .claim("name", "Test User")
                .issuedAt(new Date(now))
                .expiration(new Date(now + TEST_EXPIRATION))
                .signWith(key)
                .compact();
    }

    private Group createGroupAsOwner() {
        Group group = new Group("Test Group", "Group for integration tests", ownerId);
        Group saved = groupRepository.save(group);
        GroupMember owner = new GroupMember(saved.getId(), ownerId, MemberRole.OWNER);
        groupMemberRepository.save(owner);
        return saved;
    }

    private void addMember(UUID groupId, UUID userId, MemberRole role) {
        groupMemberRepository.save(new GroupMember(groupId, userId, role));
    }

    @Test
    @DisplayName("Unauthenticated requests are rejected with 403")
    void testUnauthenticated_Forbidden() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateGroupRequest("G", null))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/groups — creates group with OWNER membership for current user")
    void testCreateGroup_Success() throws Exception {
        String token = jwt(ownerId, "owner@test.com");
        CreateGroupRequest req = new CreateGroupRequest("Trip to Japan", "Cherry blossom season");

        mockMvc.perform(post("/api/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Trip to Japan")))
                .andExpect(jsonPath("$.description", is("Cherry blossom season")))
                .andExpect(jsonPath("$.createdBy", is(ownerId.toString())));
    }

    @Test
    @DisplayName("POST /api/groups — validation failure (blank name) returns 400")
    void testCreateGroup_ValidationFailure() throws Exception {
        String token = jwt(ownerId, "owner@test.com");
        CreateGroupRequest req = new CreateGroupRequest("A", "");

        mockMvc.perform(post("/api/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/groups — returns only groups user is a member of")
    void testListMyGroups() throws Exception {
        String token = jwt(ownerId, "owner@test.com");
        createGroupAsOwner();

        mockMvc.perform(get("/api/groups")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Test Group")));

        // Non-member sees empty
        String nonMemberToken = jwt(nonMemberId, "non@test.com");
        mockMvc.perform(get("/api/groups")
                        .header("Authorization", "Bearer " + nonMemberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/groups/{id} — member sees group, non-member gets 403")
    void testGetGroupDetail_MemberVsNonMember() throws Exception {
        Group group = createGroupAsOwner();
        String ownerToken = jwt(ownerId, "owner@test.com");
        String nonMemberToken = jwt(nonMemberId, "non@test.com");

        mockMvc.perform(get("/api/groups/" + group.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(group.getId().toString())));

        mockMvc.perform(get("/api/groups/" + group.getId())
                        .header("Authorization", "Bearer " + nonMemberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/groups/{id}/members — member lists members")
    void testListMembers() throws Exception {
        Group group = createGroupAsOwner();
        addMember(group.getId(), memberId, MemberRole.MEMBER);
        String token = jwt(ownerId, "owner@test.com");

        org.mockito.Mockito.when(authServiceClient.getUsersBatch(any())).thenReturn(List.of(
                new com.hishob.group.dto.UserSummaryData(ownerId, "Owner Name", "owner@test.com"),
                new com.hishob.group.dto.UserSummaryData(memberId, "Member Name", "member@test.com")
        ));

        mockMvc.perform(get("/api/groups/" + group.getId() + "/members")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].userId").isNotEmpty())
                .andExpect(jsonPath("$[0].role").isNotEmpty())
                .andExpect(jsonPath("$[?(@.userId == '" + ownerId + "')].name").value("Owner Name"))
                .andExpect(jsonPath("$[?(@.userId == '" + memberId + "')].name").value("Member Name"));
    }

    @Test
    @DisplayName("POST /api/groups/{id}/members — OWNER adds valid target user successfully")
    void testAddMember_Owner_Success() throws Exception {
        Group group = createGroupAsOwner();
        UUID newUserId = UUID.randomUUID();
        doNothing().when(authServiceClient).validateUserExists(newUserId);
        org.mockito.Mockito.when(authServiceClient.getUsersBatch(any())).thenReturn(List.of(
                new com.hishob.group.dto.UserSummaryData(newUserId, "New Added User", "added@test.com")
        ));

        String token = jwt(ownerId, "owner@test.com");
        AddMemberRequest req = new AddMemberRequest(newUserId);

        mockMvc.perform(post("/api/groups/" + group.getId() + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId", is(newUserId.toString())))
                .andExpect(jsonPath("$.role", is("MEMBER")))
                .andExpect(jsonPath("$.name", is("New Added User")))
                .andExpect(jsonPath("$.email", is("added@test.com")));
    }

    @Test
    @DisplayName("POST /api/groups/{id}/members — MEMBER cannot add others (403)")
    void testAddMember_NonOwner_Forbidden() throws Exception {
        Group group = createGroupAsOwner();
        addMember(group.getId(), memberId, MemberRole.MEMBER);
        String memberToken = jwt(memberId, "member@test.com");

        AddMemberRequest req = new AddMemberRequest(UUID.randomUUID());

        mockMvc.perform(post("/api/groups/" + group.getId() + "/members")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/groups/{id}/members — duplicate member returns 409")
    void testAddMember_Duplicate_Conflict() throws Exception {
        Group group = createGroupAsOwner();
        addMember(group.getId(), memberId, MemberRole.MEMBER);
        String token = jwt(ownerId, "owner@test.com");
        AddMemberRequest req = new AddMemberRequest(memberId);

        mockMvc.perform(post("/api/groups/" + group.getId() + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/groups/{id}/members — invalid target user (not in Auth) returns 400")
    void testAddMember_InvalidTargetUser() throws Exception {
        Group group = createGroupAsOwner();
        UUID badUserId = UUID.randomUUID();
        doThrow(new com.hishob.group.exception.InvalidTargetUserException("Target user does not exist"))
                .when(authServiceClient).validateUserExists(badUserId);

        String token = jwt(ownerId, "owner@test.com");
        AddMemberRequest req = new AddMemberRequest(badUserId);

        mockMvc.perform(post("/api/groups/" + group.getId() + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/groups/{id} — non-existent group returns 404 for owner (checked after not member)")
    void testGetGroupDetail_NotFound() throws Exception {
        UUID fakeGroup = UUID.randomUUID();
        String token = jwt(ownerId, "owner@test.com");
        mockMvc.perform(get("/api/groups/" + fakeGroup)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

}
