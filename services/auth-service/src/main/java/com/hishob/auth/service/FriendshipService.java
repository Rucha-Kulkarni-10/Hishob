package com.hishob.auth.service;

import com.hishob.auth.dto.FriendRequestResponse;
import com.hishob.auth.dto.FriendResponse;
import com.hishob.auth.dto.SendFriendRequest;
import com.hishob.auth.dto.UserSearchResponse;
import com.hishob.auth.dto.ValidateAcceptedFriendshipsRequest;
import com.hishob.auth.dto.ValidateAcceptedFriendshipsResponse;
import com.hishob.auth.entity.Friendship;
import com.hishob.auth.entity.FriendshipStatus;
import com.hishob.auth.entity.User;
import com.hishob.auth.exception.AlreadyFriendsException;
import com.hishob.auth.exception.BlockedUserException;
import com.hishob.auth.exception.DuplicateFriendRequestException;
import com.hishob.auth.exception.FriendshipNotFoundException;
import com.hishob.auth.exception.InvalidFriendshipStateException;
import com.hishob.auth.exception.ResourceNotFoundException;
import com.hishob.auth.exception.SelfFriendRequestException;
import com.hishob.auth.exception.UnauthorizedFriendActionException;
import com.hishob.auth.repository.FriendshipRepository;
import com.hishob.auth.repository.UserRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class FriendshipService {

    private static final java.util.Comparator<UUID> CANONICAL_UUID_ORDER = java.util.Comparator.comparing(UUID::toString);

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    public FriendshipService(FriendshipRepository friendshipRepository, UserRepository userRepository) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
    }

    public FriendRequestResponse sendRequest(String currentUserEmail, SendFriendRequest request) {
        User currentUser = resolveUserByEmail(currentUserEmail);
        User recipient = userRepository.findById(request.getRecipientUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Recipient user not found with id: " + request.getRecipientUserId()));

        if (currentUser.getId().equals(recipient.getId())) {
            throw new SelfFriendRequestException("Cannot send friend request to yourself");
        }

        FriendshipPair pair = FriendshipPair.of(currentUser.getId(), recipient.getId());
        lockUsers(pair);
        Friendship friendship = friendshipRepository.findByUserIdLowAndUserIdHigh(pair.low(), pair.high())
                .map(existing -> reopenOrRejectSend(existing, currentUser.getId()))
                .orElseGet(() -> createPendingFriendship(pair, currentUser.getId()));

        return toFriendRequestResponse(friendship, recipient);
    }

    @Transactional(readOnly = true)
    public List<FriendRequestResponse> getIncomingRequests(String currentUserEmail) {
        User currentUser = resolveUserByEmail(currentUserEmail);
        List<Friendship> requests = friendshipRepository.findIncomingRequests(currentUser.getId(), FriendshipStatus.PENDING);

        if (requests.isEmpty()) {
            return Collections.emptyList();
        }

        Set<UUID> senderIds = requests.stream().map(Friendship::getActionUserId).collect(Collectors.toSet());
        Map<UUID, User> userMap = userRepository.findAllByIdIn(senderIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return requests.stream().map(f -> {
            User sender = userMap.get(f.getActionUserId());
            String name = sender != null ? sender.getName() : "Unknown User";
            String email = sender != null ? sender.getEmail() : "";
            return new FriendRequestResponse(f.getId(), f.getActionUserId(), name, email, f.getCreatedAt());
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FriendRequestResponse> getOutgoingRequests(String currentUserEmail) {
        User currentUser = resolveUserByEmail(currentUserEmail);
        List<Friendship> requests = friendshipRepository.findOutgoingRequests(currentUser.getId(), FriendshipStatus.PENDING);

        if (requests.isEmpty()) {
            return Collections.emptyList();
        }

        Set<UUID> recipientIds = requests.stream().map(f -> getOtherUserId(f, currentUser.getId())).collect(Collectors.toSet());
        Map<UUID, User> userMap = userRepository.findAllByIdIn(recipientIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return requests.stream().map(f -> {
            UUID recipientId = getOtherUserId(f, currentUser.getId());
            User recipient = userMap.get(recipientId);
            String name = recipient != null ? recipient.getName() : "Unknown User";
            String email = recipient != null ? recipient.getEmail() : "";
            return new FriendRequestResponse(f.getId(), recipientId, name, email, f.getCreatedAt());
        }).collect(Collectors.toList());
    }

    public FriendResponse acceptRequest(String currentUserEmail, UUID requestId) {
        User currentUser = resolveUserByEmail(currentUserEmail);
        Friendship friendship = friendshipRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new FriendshipNotFoundException("Friend request not found with id: " + requestId));

        validateRecipientAction(friendship, currentUser.getId(), "accept");

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendship.setActionUserId(currentUser.getId());
        Friendship saved = friendshipRepository.save(friendship);

        UUID requesterId = getOtherUserId(saved, currentUser.getId());
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + requesterId));

        return new FriendResponse(saved.getId(), requester.getId(), requester.getName(), requester.getEmail(), saved.getCreatedAt());
    }

    public void rejectRequest(String currentUserEmail, UUID requestId) {
        User currentUser = resolveUserByEmail(currentUserEmail);
        Friendship friendship = friendshipRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new FriendshipNotFoundException("Friend request not found with id: " + requestId));

        validateRecipientAction(friendship, currentUser.getId(), "reject");

        friendship.setStatus(FriendshipStatus.REJECTED);
        friendship.setActionUserId(currentUser.getId());
        friendshipRepository.save(friendship);
    }

    public void cancelRequest(String currentUserEmail, UUID requestId) {
        User currentUser = resolveUserByEmail(currentUserEmail);
        Friendship friendship = friendshipRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new FriendshipNotFoundException("Friend request not found with id: " + requestId));

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new InvalidFriendshipStateException("Friend request is not pending (current status: " + friendship.getStatus() + ")");
        }

        if (!friendship.getActionUserId().equals(currentUser.getId())) {
            throw new UnauthorizedFriendActionException("Only the requester can cancel their friend request");
        }

        friendshipRepository.delete(friendship);
    }

    @Transactional(readOnly = true)
    public List<FriendResponse> getFriends(String currentUserEmail) {
        User currentUser = resolveUserByEmail(currentUserEmail);
        List<Friendship> friendships = friendshipRepository.findAllByUserIdAndStatus(currentUser.getId(), FriendshipStatus.ACCEPTED);

        if (friendships.isEmpty()) {
            return Collections.emptyList();
        }

        Set<UUID> friendIds = friendships.stream().map(f -> getOtherUserId(f, currentUser.getId())).collect(Collectors.toSet());
        Map<UUID, User> userMap = userRepository.findAllByIdIn(friendIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return friendships.stream().map(f -> {
            UUID friendId = getOtherUserId(f, currentUser.getId());
            User friend = userMap.get(friendId);
            String name = friend != null ? friend.getName() : "Unknown User";
            String email = friend != null ? friend.getEmail() : "";
            return new FriendResponse(f.getId(), friendId, name, email, f.getCreatedAt());
        }).collect(Collectors.toList());
    }

    public void removeFriend(String currentUserEmail, UUID friendUserId) {
        User currentUser = resolveUserByEmail(currentUserEmail);

        if (currentUser.getId().equals(friendUserId)) {
            throw new IllegalArgumentException("Cannot remove yourself as a friend");
        }

        FriendshipPair pair = FriendshipPair.of(currentUser.getId(), friendUserId);
        lockUsers(pair);
        Friendship friendship = friendshipRepository.findByPairForUpdate(pair.low(), pair.high())
                .orElseThrow(() -> new FriendshipNotFoundException("Friendship not found with user: " + friendUserId));

        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new InvalidFriendshipStateException("You are not currently friends with this user");
        }

        friendshipRepository.delete(friendship);
    }

    public void blockUser(String currentUserEmail, UUID targetUserId) {
        User currentUser = resolveUserByEmail(currentUserEmail);

        if (currentUser.getId().equals(targetUserId)) {
            throw new IllegalArgumentException("Cannot block yourself");
        }

        userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + targetUserId));

        FriendshipPair pair = FriendshipPair.of(currentUser.getId(), targetUserId);
        lockUsers(pair);
        Friendship friendship = friendshipRepository.findByPairForUpdate(pair.low(), pair.high())
                .orElseGet(() -> new Friendship(pair.low(), pair.high(), currentUser.getId(), FriendshipStatus.BLOCKED));

        friendship.setStatus(FriendshipStatus.BLOCKED);
        friendship.setActionUserId(currentUser.getId());

        friendshipRepository.saveAndFlush(friendship);
    }

    @Transactional(readOnly = true)
    public List<UserSearchResponse> searchUsers(String currentUserEmail, String query) {
        User currentUser = resolveUserByEmail(currentUserEmail);
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<User> matched = userRepository.searchUsers(query.trim(), currentUser.getId(), PageRequest.of(0, 20));
        return matched.stream()
                .map(UserSearchResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ValidateAcceptedFriendshipsResponse validateAcceptedFriendships(
            ValidateAcceptedFriendshipsRequest request) {
        UUID currentUserId = request.getCurrentUserId();
        LinkedHashSet<UUID> requestedIds = new LinkedHashSet<>(
                request.getUserIds() != null ? request.getUserIds() : List.of());
        requestedIds.remove(currentUserId);

        List<UUID> missingUserIds = new ArrayList<>();
        List<UUID> nonAcceptedFriendIds = new ArrayList<>();

        if (!userRepository.existsById(currentUserId)) {
            missingUserIds.add(currentUserId);
        }

        for (UUID userId : requestedIds) {
            if (userId == null || !userRepository.existsById(userId)) {
                missingUserIds.add(userId);
                continue;
            }

            FriendshipPair pair = FriendshipPair.of(currentUserId, userId);
            boolean accepted = friendshipRepository.findByUserIdLowAndUserIdHigh(pair.low(), pair.high())
                    .map(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                    .orElse(false);
            if (!accepted) {
                nonAcceptedFriendIds.add(userId);
            }
        }

        return new ValidateAcceptedFriendshipsResponse(
                missingUserIds.isEmpty() && nonAcceptedFriendIds.isEmpty(),
                missingUserIds,
                nonAcceptedFriendIds);
    }

    private void lockUsers(FriendshipPair pair) {
        userRepository.findByIdForUpdate(pair.low())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + pair.low()));
        userRepository.findByIdForUpdate(pair.high())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + pair.high()));
    }

    private User resolveUserByEmail(String email) {
        String normEmail = email.trim().toLowerCase();
        return userRepository.findByEmail(normEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + normEmail));
    }

    private UUID getOtherUserId(Friendship f, UUID currentUserId) {
        return f.getUserIdLow().equals(currentUserId) ? f.getUserIdHigh() : f.getUserIdLow();
    }

    private FriendRequestResponse toFriendRequestResponse(Friendship friendship, User relatedUser) {
        return new FriendRequestResponse(
                friendship.getId(),
                relatedUser.getId(),
                relatedUser.getName(),
                relatedUser.getEmail(),
                friendship.getCreatedAt()
        );
    }

    private Friendship reopenOrRejectSend(Friendship existing, UUID requesterId) {
        return switch (existing.getStatus()) {
            case ACCEPTED -> throw new AlreadyFriendsException("You are already friends with this user");
            case PENDING -> throw new DuplicateFriendRequestException("A friend request is already pending between you and this user");
            case BLOCKED -> throw new BlockedUserException("Unable to send friend request to this user");
            case REJECTED -> {
                existing.setStatus(FriendshipStatus.PENDING);
                existing.setActionUserId(requesterId);
                yield friendshipRepository.save(existing);
            }
        };
    }

    private Friendship createPendingFriendship(FriendshipPair pair, UUID requesterId) {
        Friendship newFriendship = new Friendship(pair.low(), pair.high(), requesterId, FriendshipStatus.PENDING);
        return friendshipRepository.saveAndFlush(newFriendship);
    }

    private void validateRecipientAction(Friendship friendship, UUID currentUserId, String action) {
        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new InvalidFriendshipStateException("Friend request is not pending (current status: " + friendship.getStatus() + ")");
        }

        boolean isParticipant = friendship.getUserIdLow().equals(currentUserId) || friendship.getUserIdHigh().equals(currentUserId);
        if (!isParticipant || friendship.getActionUserId().equals(currentUserId)) {
            throw new UnauthorizedFriendActionException("Only the recipient can " + action + " a friend request");
        }
    }

    private record FriendshipPair(UUID low, UUID high) {

        private static FriendshipPair of(UUID firstUserId, UUID secondUserId) {
            if (CANONICAL_UUID_ORDER.compare(firstUserId, secondUserId) < 0) {
                return new FriendshipPair(firstUserId, secondUserId);
            }
            return new FriendshipPair(secondUserId, firstUserId);
        }
    }
}
