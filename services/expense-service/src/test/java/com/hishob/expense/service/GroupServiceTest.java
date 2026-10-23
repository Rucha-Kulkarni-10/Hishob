package com.hishob.expense.service;

import com.hishob.expense.client.AuthServiceClient;
import com.hishob.expense.client.NotificationServiceClient;
import com.hishob.expense.dto.AddMemberRequest;
import com.hishob.expense.dto.CreateGroupRequest;
import com.hishob.expense.dto.CreateNotificationRequest;
import com.hishob.expense.dto.GroupResponse;
import com.hishob.expense.dto.MemberResponse;
import com.hishob.expense.dto.UserSummaryData;
import com.hishob.expense.entity.Group;
import com.hishob.expense.entity.GroupMember;
import com.hishob.expense.entity.MemberRole;
import com.hishob.expense.exception.DuplicateMembershipException;
import com.hishob.expense.exception.GroupNotFoundException;
import com.hishob.expense.exception.InvalidGroupException;
import com.hishob.expense.exception.InvalidTargetUserException;
import com.hishob.expense.exception.PermissionDeniedException;
import com.hishob.expense.repository.GroupMemberRepository;
import com.hishob.expense.repository.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private AuthServiceClient authServiceClient;

    @Mock
    private NotificationServiceClient notificationServiceClient;

    private GroupService groupService;

    private UUID creatorId;
    private UUID otherUserId;
    private UUID groupId;

    @BeforeEach
    void setUp() {
        groupService = new GroupService(groupRepository, groupMemberRepository, authServiceClient, notificationServiceClient);
        creatorId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        groupId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Create group: persists Group + OWNER GroupMember and returns response")
    void testCreateGroup_Success() {
        CreateGroupRequest req = new CreateGroupRequest("Trip to Paris", "Summer vacation");
        Group savedGroup = new Group("Trip to Paris", "Summer vacation", creatorId);
        savedGroup.setId(groupId);
        savedGroup.setCreatedAt(Instant.now());
        savedGroup.setUpdatedAt(Instant.now());

        when(groupRepository.save(any(Group.class))).thenReturn(savedGroup);
        when(groupMemberRepository.save(any(GroupMember.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        GroupResponse resp = groupService.createGroup(req, creatorId);

        assertThat(resp).isNotNull();
        assertThat(resp.getId()).isEqualTo(groupId);
        assertThat(resp.getName()).isEqualTo("Trip to Paris");
        assertThat(resp.getDescription()).isEqualTo("Summer vacation");
        assertThat(resp.getCreatedBy()).isEqualTo(creatorId);

        verify(groupMemberRepository).save(argThat(gm ->
                gm.getGroupId().equals(groupId)
                        && gm.getUserId().equals(creatorId)
                        && gm.getRole() == MemberRole.OWNER));
    }

    @Test
    @DisplayName("List my groups: returns only groups user is a member of")
    void testListMyGroups_Success() {
        Group g1 = new Group("Trip", "desc", creatorId);
        g1.setId(UUID.randomUUID());
        g1.setCreatedAt(Instant.now());
        g1.setUpdatedAt(Instant.now());
        Group g2 = new Group("Roommates", null, creatorId);
        g2.setId(UUID.randomUUID());
        g2.setCreatedAt(Instant.now());
        g2.setUpdatedAt(Instant.now());

        when(groupRepository.findAllByMemberUserId(creatorId)).thenReturn(List.of(g1, g2));

        List<GroupResponse> groups = groupService.listMyGroups(creatorId);

        assertThat(groups).hasSize(2);
        assertThat(groups).extracting(GroupResponse::getName)
                .containsExactlyInAnyOrder("Trip", "Roommates");
    }

    @Test
    @DisplayName("Get group detail: returns data when caller is member")
    void testGetGroupDetail_Member_Success() {
        Group group = new Group("G", "d", creatorId);
        group.setId(groupId);
        group.setCreatedAt(Instant.now());
        group.setUpdatedAt(Instant.now());

        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, creatorId)).thenReturn(true);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

        GroupResponse resp = groupService.getGroupDetail(groupId, creatorId);

        assertThat(resp.getId()).isEqualTo(groupId);
        assertThat(resp.getName()).isEqualTo("G");
    }

    @Test
    @DisplayName("Get group detail: throws PermissionDenied when caller not member")
    void testGetGroupDetail_NotMember_Forbidden() {
        Group group = new Group("G", "d", creatorId);
        group.setId(groupId);
        group.setCreatedAt(Instant.now());
        group.setUpdatedAt(Instant.now());

        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, otherUserId)).thenReturn(false);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> groupService.getGroupDetail(groupId, otherUserId))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("Get group detail: throws GroupNotFound when group doesn't exist")
    void testGetGroupDetail_NotFound() {
        when(groupRepository.findById(groupId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.getGroupDetail(groupId, creatorId))
                .isInstanceOf(GroupNotFoundException.class);
    }

    @Test
    @DisplayName("List members: member can view members")
    void testListMembers_Success() {
        GroupMember owner = new GroupMember(groupId, creatorId, MemberRole.OWNER);
        owner.setId(UUID.randomUUID());
        owner.setJoinedAt(Instant.now());
        GroupMember member = new GroupMember(groupId, otherUserId, MemberRole.MEMBER);
        member.setId(UUID.randomUUID());
        member.setJoinedAt(Instant.now());

        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, creatorId)).thenReturn(true);
        when(groupMemberRepository.findByGroupId(groupId)).thenReturn(List.of(owner, member));
        when(authServiceClient.getUsersBatch(any())).thenReturn(List.of(
                new UserSummaryData(creatorId, "Creator Name"),
                new UserSummaryData(otherUserId, "Other Name")
        ));

        List<MemberResponse> members = groupService.listMembers(groupId, creatorId);

        assertThat(members).hasSize(2);
        assertThat(members).extracting(MemberResponse::getUserId)
                .containsExactlyInAnyOrder(creatorId, otherUserId);
        assertThat(members).extracting(MemberResponse::getRole)
                .containsExactlyInAnyOrder(MemberRole.OWNER, MemberRole.MEMBER);
        assertThat(members).extracting(MemberResponse::getName)
                .containsExactlyInAnyOrder("Creator Name", "Other Name");
        assertThat(MemberResponse.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("email");
        verify(authServiceClient).getUsersBatch(any());
    }

    @Test
    @DisplayName("Add member: OWNER can add valid existing user")
    void testAddMember_Owner_Success() {
        UUID newUserId = UUID.randomUUID();
        AddMemberRequest req = new AddMemberRequest(newUserId);

        when(groupMemberRepository.existsByGroupIdAndUserIdAndRole(groupId, creatorId, MemberRole.OWNER))
                .thenReturn(true);
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, newUserId)).thenReturn(false);
        when(groupMemberRepository.save(any(GroupMember.class)))
                .thenAnswer(inv -> {
                    GroupMember gm = inv.getArgument(0);
                    gm.setId(UUID.randomUUID());
                    gm.setJoinedAt(Instant.now());
                    return gm;
                });
        when(authServiceClient.getUsersBatch(any())).thenReturn(List.of(
                new UserSummaryData(newUserId, "New User")
        ));

        MemberResponse resp = groupService.addMember(groupId, req, creatorId);

        assertThat(resp.getUserId()).isEqualTo(newUserId);
        assertThat(resp.getRole()).isEqualTo(MemberRole.MEMBER);
        assertThat(resp.getName()).isEqualTo("New User");
        verify(authServiceClient).validateUserExists(newUserId);
        verify(authServiceClient).getUsersBatch(any());

        // Verify notification is sent
        verify(notificationServiceClient).createNotification(argThat(notification ->
                notification.getUserId().equals(newUserId)
                        && notification.getGroupId().equals(groupId)
                        && notification.getType().equals("GROUP_MEMBER_ADDED")
                        && notification.getMessage().equals("You were added to the group.")
                        && notification.getMetadata().containsKey("addedBy")
                        && notification.getMetadata().get("addedBy").equals(creatorId.toString())));
    }

    @Test
    @DisplayName("Add member: MEMBER role forbidden from adding")
    void testAddMember_NonOwner_Forbidden() {
        AddMemberRequest req = new AddMemberRequest(otherUserId);

        when(groupMemberRepository.existsByGroupIdAndUserIdAndRole(groupId, otherUserId, MemberRole.OWNER))
                .thenReturn(false);
        Group group = new Group("G", null, creatorId);
        group.setId(groupId);
        group.setCreatedAt(Instant.now());
        group.setUpdatedAt(Instant.now());
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, otherUserId)).thenReturn(true);

        assertThatThrownBy(() -> groupService.addMember(groupId, req, otherUserId))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("Only group owners can add members");
        verify(authServiceClient, never()).validateUserExists(any());
    }

    @Test
    @DisplayName("Add member: duplicate membership returns 409")
    void testAddMember_Duplicate_Conflict() {
        AddMemberRequest req = new AddMemberRequest(otherUserId);

        when(groupMemberRepository.existsByGroupIdAndUserIdAndRole(groupId, creatorId, MemberRole.OWNER))
                .thenReturn(true);
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, otherUserId)).thenReturn(true);

        assertThatThrownBy(() -> groupService.addMember(groupId, req, creatorId))
                .isInstanceOf(DuplicateMembershipException.class);
        verify(authServiceClient, never()).validateUserExists(any());
    }

    @Test
    @DisplayName("Add member: invalid target user rejected via AuthServiceClient")
    void testAddMember_InvalidTargetUser() {
        UUID fakeUserId = UUID.randomUUID();
        AddMemberRequest req = new AddMemberRequest(fakeUserId);

        when(groupMemberRepository.existsByGroupIdAndUserIdAndRole(groupId, creatorId, MemberRole.OWNER))
                .thenReturn(true);
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, fakeUserId)).thenReturn(false);
        org.mockito.Mockito.doThrow(new InvalidTargetUserException("Target user does not exist"))
                .when(authServiceClient).validateUserExists(fakeUserId);

        assertThatThrownBy(() -> groupService.addMember(groupId, req, creatorId))
                .isInstanceOf(InvalidTargetUserException.class);
    }

    @Test
    @DisplayName("Add member: notification is attempted after the membership is saved")
    void testAddMember_NotificationAttempted_MemberAdded() {
        UUID newUserId = UUID.randomUUID();
        AddMemberRequest req = new AddMemberRequest(newUserId);

        when(groupMemberRepository.existsByGroupIdAndUserIdAndRole(groupId, creatorId, MemberRole.OWNER))
                .thenReturn(true);
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, newUserId)).thenReturn(false);
        when(groupMemberRepository.save(any(GroupMember.class)))
                .thenAnswer(inv -> {
                    GroupMember gm = inv.getArgument(0);
                    gm.setId(UUID.randomUUID());
                    gm.setJoinedAt(Instant.now());
                    return gm;
                });

        // Member addition should succeed and notification should be attempted
        MemberResponse resp = groupService.addMember(groupId, req, creatorId);

        assertThat(resp.getUserId()).isEqualTo(newUserId);
        assertThat(resp.getRole()).isEqualTo(MemberRole.MEMBER);
        verify(authServiceClient).validateUserExists(newUserId);
        verify(notificationServiceClient).createNotification(any(CreateNotificationRequest.class));
    }

    @Test
    @DisplayName("Add member: a notification delivery failure must not abort the membership")
    void testAddMember_NotificationThrows_MemberStillAdded() {
        UUID newUserId = UUID.randomUUID();
        AddMemberRequest req = new AddMemberRequest(newUserId);

        when(groupMemberRepository.existsByGroupIdAndUserIdAndRole(groupId, creatorId, MemberRole.OWNER))
                .thenReturn(true);
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, newUserId)).thenReturn(false);
        when(groupMemberRepository.save(any(GroupMember.class)))
                .thenAnswer(inv -> {
                    GroupMember gm = inv.getArgument(0);
                    gm.setId(UUID.randomUUID());
                    gm.setJoinedAt(Instant.now());
                    return gm;
                });
        doThrow(new RuntimeException("Auth Service unavailable: cannot create notification"))
                .when(notificationServiceClient).createNotification(any(CreateNotificationRequest.class));

        MemberResponse resp = groupService.addMember(groupId, req, creatorId);

        assertThat(resp.getUserId()).isEqualTo(newUserId);
        assertThat(resp.getRole()).isEqualTo(MemberRole.MEMBER);
        verify(groupMemberRepository).save(any(GroupMember.class));
    }

    // Consolidated in-process validation tests
    @Test
    @DisplayName("validateGroupExists: succeeds when group exists in repository")
    void testValidateGroupExists_Success() {
        when(groupRepository.existsById(groupId)).thenReturn(true);

        assertThatCode(() -> groupService.validateGroupExists(groupId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateGroupExists: throws InvalidGroupException when group not found")
    void testValidateGroupExists_NotFound() {
        when(groupRepository.existsById(groupId)).thenReturn(false);

        assertThatThrownBy(() -> groupService.validateGroupExists(groupId))
                .isInstanceOf(InvalidGroupException.class)
                .hasMessageContaining("Group not found");
    }

    @Test
    @DisplayName("validateMember: succeeds when user is a member of the group")
    void testValidateMember_Success() {
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, creatorId)).thenReturn(true);

        assertThatCode(() -> groupService.validateMember(groupId, creatorId, "Creator"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateMember: throws PermissionDeniedException when user is not a member")
    void testValidateMember_NotMember() {
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, otherUserId)).thenReturn(false);

        assertThatThrownBy(() -> groupService.validateMember(groupId, otherUserId, "Participant"))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("Participant is not a member of this group");
    }

    @Test
    @DisplayName("getGroupMemberIds: returns list of member UUIDs when group exists")
    void testGetGroupMemberIds_Success() {
        GroupMember gm1 = new GroupMember(groupId, creatorId, MemberRole.OWNER);
        GroupMember gm2 = new GroupMember(groupId, otherUserId, MemberRole.MEMBER);

        when(groupRepository.existsById(groupId)).thenReturn(true);
        when(groupMemberRepository.findByGroupId(groupId)).thenReturn(List.of(gm1, gm2));

        List<UUID> memberIds = groupService.getGroupMemberIds(groupId);

        assertThat(memberIds).containsExactly(creatorId, otherUserId);
    }

    @Test
    @DisplayName("getGroupMemberIds: throws InvalidGroupException when group not found")
    void testGetGroupMemberIds_NotFound() {
        when(groupRepository.existsById(groupId)).thenReturn(false);

        assertThatThrownBy(() -> groupService.getGroupMemberIds(groupId))
                .isInstanceOf(InvalidGroupException.class)
                .hasMessageContaining("Group not found");
    }

}
