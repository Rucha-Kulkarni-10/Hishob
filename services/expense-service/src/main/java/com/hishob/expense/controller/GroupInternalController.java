package com.hishob.expense.controller;

import com.hishob.expense.service.GroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/groups")
public class GroupInternalController {

    private final GroupService groupService;

    public GroupInternalController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping("/{groupId}/exists")
    public ResponseEntity<Void> groupExists(@PathVariable UUID groupId) {
        if (groupService.groupExists(groupId)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{groupId}/members/{userId}/exists")
    public ResponseEntity<Void> memberExists(@PathVariable UUID groupId, @PathVariable UUID userId) {
        if (groupService.isMember(groupId, userId)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<UUID>> listMemberIds(@PathVariable UUID groupId) {
        if (!groupService.groupExists(groupId)) {
            return ResponseEntity.notFound().build();
        }
        List<UUID> memberIds = groupService.listMemberIds(groupId);
        return ResponseEntity.ok(memberIds);
    }

}
