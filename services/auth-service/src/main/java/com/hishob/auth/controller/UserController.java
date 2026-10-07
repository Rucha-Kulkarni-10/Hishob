package com.hishob.auth.controller;

import com.hishob.auth.dto.UserSearchResponse;
import com.hishob.auth.service.FriendshipService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class UserController {

    private final FriendshipService friendshipService;

    public UserController(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    @GetMapping({"/api/users/search", "/api/auth/users/search"})
    public ResponseEntity<List<UserSearchResponse>> searchUsers(
            @RequestParam(name = "q", required = false, defaultValue = "") String query,
            @AuthenticationPrincipal UserDetails userDetails) {
        List<UserSearchResponse> results = friendshipService.searchUsers(userDetails.getUsername(), query);
        return ResponseEntity.ok(results);
    }
}
