package com.hishob.auth.controller;

import com.hishob.auth.dto.FriendRequestResponse;
import com.hishob.auth.dto.FriendResponse;
import com.hishob.auth.dto.SendFriendRequest;
import com.hishob.auth.service.FriendshipService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/friends")
public class FriendshipController {

    private final FriendshipService friendshipService;

    public FriendshipController(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    @PostMapping("/requests")
    public ResponseEntity<FriendRequestResponse> sendRequest(
            @Valid @RequestBody SendFriendRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        FriendRequestResponse response = friendshipService.sendRequest(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/requests/incoming")
    public ResponseEntity<List<FriendRequestResponse>> getIncomingRequests(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<FriendRequestResponse> response = friendshipService.getIncomingRequests(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/requests/outgoing")
    public ResponseEntity<List<FriendRequestResponse>> getOutgoingRequests(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<FriendRequestResponse> response = friendshipService.getOutgoingRequests(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/requests/{requestId}/accept")
    public ResponseEntity<FriendResponse> acceptRequest(
            @PathVariable UUID requestId,
            @AuthenticationPrincipal UserDetails userDetails) {
        FriendResponse response = friendshipService.acceptRequest(userDetails.getUsername(), requestId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/requests/{requestId}/reject")
    public ResponseEntity<Void> rejectRequest(
            @PathVariable UUID requestId,
            @AuthenticationPrincipal UserDetails userDetails) {
        friendshipService.rejectRequest(userDetails.getUsername(), requestId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/requests/{requestId}/cancel")
    public ResponseEntity<Void> cancelRequest(
            @PathVariable UUID requestId,
            @AuthenticationPrincipal UserDetails userDetails) {
        friendshipService.cancelRequest(userDetails.getUsername(), requestId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<FriendResponse>> getFriends(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<FriendResponse> response = friendshipService.getFriends(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{friendUserId}")
    public ResponseEntity<Void> removeFriend(
            @PathVariable UUID friendUserId,
            @AuthenticationPrincipal UserDetails userDetails) {
        friendshipService.removeFriend(userDetails.getUsername(), friendUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{targetUserId}/block")
    public ResponseEntity<Void> blockUser(
            @PathVariable UUID targetUserId,
            @AuthenticationPrincipal UserDetails userDetails) {
        friendshipService.blockUser(userDetails.getUsername(), targetUserId);
        return ResponseEntity.noContent().build();
    }
}
