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
import com.hishob.expense.exception.PermissionDeniedException;
import com.hishob.expense.repository.GroupMemberRepository;
import com.hishob.expense.repository.GroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final AuthServiceClient authServiceClient;
    private final NotificationServiceClient notificationServiceClient;

    public GroupService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            AuthServiceClient authServiceClient,
            NotificationServiceClient notificationServiceClient) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.authServiceClient = authServiceClient;
        this.notificationServiceClient = notificationServiceClient;
    }

    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, UUID currentUserId) {
        Group group = new Group();
        group.setName(request.getName().trim());
        group.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        group.setCreatedBy(currentUserId);

        Group savedGroup = groupRepository.save(group);

        GroupMember ownerMembership = new GroupMember(savedGroup.getId(), currentUserId, MemberRole.OWNER);
        groupMemberRepository.save(ownerMembership);

        return GroupResponse.fromEntity(savedGroup);
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> listMyGroups(UUID currentUserId) {
        return groupRepository.findAllByMemberUserId(currentUserId)
                .stream()
                .map(GroupResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UUID> listGroupIdsForMember(UUID userId) {
        return groupRepository.findAllByMemberUserId(userId)
                .stream()
                .map(Group::getId)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroupDetail(UUID groupId, UUID currentUserId) {
        ensureMember(groupId, currentUserId);
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found: " + groupId));
        return GroupResponse.fromEntity(group);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(UUID groupId, UUID currentUserId) {
        ensureMember(groupId, currentUserId);
        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        if (members.isEmpty()) {
            return List.of();
        }

        Set<UUID> userIds = members.stream()
                .map(GroupMember::getUserId)
                .collect(Collectors.toSet());

        Map<UUID, UserSummaryData> userMap = authServiceClient.getUsersBatch(userIds).stream()
                .collect(Collectors.toMap(UserSummaryData::getId, u -> u, (a, b) -> a));

        return members.stream().map(m -> {
            UserSummaryData profile = userMap.get(m.getUserId());
            String name = profile != null ? profile.getName() : null;
            return new MemberResponse(m.getId(), m.getUserId(), m.getRole(), m.getJoinedAt(), name);
        }).collect(Collectors.toList());
    }

    @Transactional
    public MemberResponse addMember(UUID groupId, AddMemberRequest request, UUID currentUserId) {
        UUID targetUserId = request.getUserId();

        ensureOwner(groupId, currentUserId);

        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, targetUserId)) {
            throw new DuplicateMembershipException("User is already a member of this group: " + targetUserId);
        }

        authServiceClient.validateUserExists(targetUserId);

        GroupMember membership = new GroupMember(groupId, targetUserId, MemberRole.MEMBER);
        GroupMember saved = groupMemberRepository.save(membership);

        // Best-effort: a delivery failure must not undo the membership created above.
        sendMemberAddedNotification(groupId, targetUserId, currentUserId);

        List<UserSummaryData> userSummaries = authServiceClient.getUsersBatch(List.of(targetUserId));
        UserSummaryData profile = userSummaries.isEmpty() ? null : userSummaries.get(0);
        String name = profile != null ? profile.getName() : null;

        return new MemberResponse(saved.getId(), saved.getUserId(), saved.getRole(), saved.getJoinedAt(), name);
    }

    private void ensureMember(UUID groupId, UUID userId) {
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            Group group = groupRepository.findById(groupId).orElse(null);
            if (group == null) {
                throw new GroupNotFoundException("Group not found: " + groupId);
            }
            throw new PermissionDeniedException("You are not a member of this group");
        }
    }

    private void ensureOwner(UUID groupId, UUID userId) {
        if (!groupMemberRepository.existsByGroupIdAndUserIdAndRole(groupId, userId, MemberRole.OWNER)) {
            Group group = groupRepository.findById(groupId).orElse(null);
            if (group == null) {
                throw new GroupNotFoundException("Group not found: " + groupId);
            }
            if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
                throw new PermissionDeniedException("You are not a member of this group");
            }
            throw new PermissionDeniedException("Only group owners can add members");
        }
    }

    @Transactional(readOnly = true)
    public boolean groupExists(UUID groupId) {
        return groupRepository.existsById(groupId);
    }

    @Transactional(readOnly = true)
    public boolean isMember(UUID groupId, UUID userId) {
        if (!groupRepository.existsById(groupId)) {
            return false;
        }
        return groupMemberRepository.existsByGroupIdAndUserId(groupId, userId);
    }

    @Transactional(readOnly = true)
    public List<UUID> listMemberIds(UUID groupId) {
        return groupMemberRepository.findByGroupId(groupId)
                .stream()
                .map(GroupMember::getUserId)
                .collect(Collectors.toList());
    }

    // Direct in-process methods replacing GroupServiceClient HTTP calls
    @Transactional(readOnly = true)
    public void validateGroupExists(UUID groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new InvalidGroupException("Group not found: " + groupId);
        }
    }

    @Transactional(readOnly = true)
    public void validateMember(UUID groupId, UUID userId, String roleLabel) {
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new PermissionDeniedException(roleLabel + " is not a member of this group");
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> getGroupMemberIds(UUID groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new InvalidGroupException("Group not found: " + groupId);
        }
        return listMemberIds(groupId);
    }

    private void sendMemberAddedNotification(UUID groupId, UUID targetUserId, UUID addedByUserId) {
        CreateNotificationRequest notificationRequest = new CreateNotificationRequest(
                targetUserId,
                groupId,
                "GROUP_MEMBER_ADDED",
                "You were added to the group.",
                Map.of("addedBy", addedByUserId.toString())
        );

        try {
            notificationServiceClient.createNotification(notificationRequest);
        } catch (Exception e) {
            log.warn("Failed to dispatch GROUP_MEMBER_ADDED notification for group {} to user {}: {}",
                    groupId, targetUserId, e.toString());
        }
    }

}
