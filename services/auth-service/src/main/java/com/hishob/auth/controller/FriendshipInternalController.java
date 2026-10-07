package com.hishob.auth.controller;

import com.hishob.auth.dto.ValidateAcceptedFriendshipsRequest;
import com.hishob.auth.dto.ValidateAcceptedFriendshipsResponse;
import com.hishob.auth.service.FriendshipService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/friendships")
public class FriendshipInternalController {

    private final FriendshipService friendshipService;

    public FriendshipInternalController(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    @PostMapping("/accepted/validate")
    public ResponseEntity<ValidateAcceptedFriendshipsResponse> validateAcceptedFriendships(
            @Valid @RequestBody ValidateAcceptedFriendshipsRequest request) {
        return ResponseEntity.ok(friendshipService.validateAcceptedFriendships(request));
    }
}
