package com.hishob.expense.controller;

import com.hishob.expense.dto.AddMemberRequest;
import com.hishob.expense.dto.CreateGroupRequest;
import com.hishob.expense.dto.GroupResponse;
import com.hishob.expense.dto.MemberResponse;
import com.hishob.expense.principal.AuthenticatedUser;
import com.hishob.expense.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        GroupResponse response = groupService.createGroup(request, currentUser.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<GroupResponse>> listMyGroups(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        List<GroupResponse> response = groupService.listMyGroups(currentUser.getUserId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<GroupResponse> getGroupDetail(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        GroupResponse response = groupService.getGroupDetail(groupId, currentUser.getUserId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<MemberResponse>> listMembers(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        List<MemberResponse> response = groupService.listMembers(groupId, currentUser.getUserId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{groupId}/members")
    public ResponseEntity<MemberResponse> addMember(
            @PathVariable UUID groupId,
            @Valid @RequestBody AddMemberRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        MemberResponse response = groupService.addMember(groupId, request, currentUser.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

}
