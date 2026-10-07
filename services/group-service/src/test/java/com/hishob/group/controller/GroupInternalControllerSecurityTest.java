package com.hishob.group.controller;

import com.hishob.group.client.AuthServiceClient;
import com.hishob.group.entity.Group;
import com.hishob.group.entity.GroupMember;
import com.hishob.group.entity.MemberRole;
import com.hishob.group.repository.GroupMemberRepository;
import com.hishob.group.repository.GroupRepository;
import com.hishob.group.security.InternalAuthFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class GroupInternalControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @MockitoBean
    private AuthServiceClient authServiceClient;

    private MockMvc mockMvc;

    private static final String VALID_TOKEN = "test-internal-token";

    private UUID groupId;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();

        memberId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Group group = new Group("Test Group", null, ownerId);
        Group saved = groupRepository.save(group);
        groupId = saved.getId();
        groupMemberRepository.save(new GroupMember(groupId, memberId, MemberRole.MEMBER));
    }

    @Test
    @DisplayName("Internal groupExists — valid token returns 204")
    void testGroupExists_ValidToken_Success() throws Exception {
        mockMvc.perform(get("/internal/groups/" + groupId + "/exists")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Internal groupExists — no token returns 401")
    void testGroupExists_NoToken_Unauthorized() throws Exception {
        mockMvc.perform(get("/internal/groups/" + groupId + "/exists"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Internal groupExists — wrong token returns 401")
    void testGroupExists_WrongToken_Unauthorized() throws Exception {
        mockMvc.perform(get("/internal/groups/" + groupId + "/exists")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, "wrong-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Internal groupExists — blank token returns 401")
    void testGroupExists_BlankToken_Unauthorized() throws Exception {
        mockMvc.perform(get("/internal/groups/" + groupId + "/exists")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, "   "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Internal memberExists — valid token, known member returns 204")
    void testMemberExists_ValidToken_Success() throws Exception {
        mockMvc.perform(get("/internal/groups/" + groupId + "/members/" + memberId + "/exists")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Internal memberExists — no token returns 401")
    void testMemberExists_NoToken_Unauthorized() throws Exception {
        mockMvc.perform(get("/internal/groups/" + groupId + "/members/" + memberId + "/exists"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Internal memberExists — wrong token returns 401")
    void testMemberExists_WrongToken_Unauthorized() throws Exception {
        mockMvc.perform(get("/internal/groups/" + groupId + "/members/" + memberId + "/exists")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, "bad-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Normal JWT-protected user endpoint still requires JWT, not internal token")
    void testUserEndpoint_InternalToken_Rejected() throws Exception {
        // Sending internal token to a user endpoint must not grant access
        mockMvc.perform(get("/api/groups")
                        .header(InternalAuthFilter.INTERNAL_TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isForbidden());
    }
}
