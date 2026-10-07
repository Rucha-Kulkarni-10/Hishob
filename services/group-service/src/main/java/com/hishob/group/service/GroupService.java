package com.hishob.group.service;

import com.hishob.group.client.AuthServiceClient;
import com.hishob.group.client.NotificationServiceClient;
import com.hishob.group.dto.AddMemberRequest;
import com.hishob.group.dto.CreateGroupRequest;
import com.hishob.group.dto.CreateNotificationRequest;
import com.hishob.group.dto.GroupResponse;
import com.hishob.group.dto.MemberResponse;
import com.hishob.group.entity.Group;
import com.hishob.group.entity.GroupMember;
import com.hishob.group.entity.MemberRole;
import com.hishob.group.exception.DuplicateMembershipException;
import com.hishob.group.exception.GroupNotFoundException;
import com.hishob.group.exception.PermissionDeniedException;
import com.hishob.group.repository.GroupMemberRepository;
import com.hishob.group.repository.GroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GroupService {

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

        java.util.Set<UUID> userIds = members.stream()
                .map(GroupMember::getUserId)
                .collect(Collectors.toSet());

        Map<UUID, com.hishob.group.dto.UserSummaryData> userMap = authServiceClient.getUsersBatch(userIds).stream()
                .collect(Collectors.toMap(com.hishob.group.dto.UserSummaryData::getId, u -> u, (a, b) -> a));

        return members.stream().map(m -> {
            com.hishob.group.dto.UserSummaryData profile = userMap.get(m.getUserId());
            String name = profile != null ? profile.getName() : null;
            String email = profile != null ? profile.getEmail() : null;
            return new MemberResponse(m.getId(), m.getUserId(), m.getRole(), m.getJoinedAt(), name, email);
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
        
        // Send notification after successful member addition
        sendMemberAddedNotification(groupId, targetUserId, currentUserId);
        
        List<com.hishob.group.dto.UserSummaryData> userSummaries = authServiceClient.getUsersBatch(List.of(targetUserId));
        com.hishob.group.dto.UserSummaryData profile = userSummaries.isEmpty() ? null : userSummaries.get(0);
        String name = profile != null ? profile.getName() : null;
        String email = profile != null ? profile.getEmail() : null;

        return new MemberResponse(saved.getId(), saved.getUserId(), saved.getRole(), saved.getJoinedAt(), name, email);
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

    private void sendMemberAddedNotification(UUID groupId, UUID targetUserId, UUID addedByUserId) {
        CreateNotificationRequest notificationRequest = new CreateNotificationRequest(
                targetUserId,
                groupId,
                "GROUP_MEMBER_ADDED",
                "You were added to the group.",
                Map.of("addedBy", addedByUserId.toString())
        );
        
        notificationServiceClient.createNotification(notificationRequest);
    }

}
