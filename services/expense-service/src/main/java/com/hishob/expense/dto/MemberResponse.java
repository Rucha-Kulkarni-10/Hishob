package com.hishob.expense.dto;

import com.hishob.expense.entity.GroupMember;
import com.hishob.expense.entity.MemberRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Group roster entry. Carries a display name only: group members are addressed by user ID,
 * so contact details have no purpose here and must not spread across the roster.
 */
public class MemberResponse {

    private UUID id;
    private UUID userId;
    private MemberRole role;
    private Instant joinedAt;
    private String name;

    public MemberResponse() {
    }

    public MemberResponse(UUID id, UUID userId, MemberRole role, Instant joinedAt) {
        this.id = id;
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public MemberResponse(UUID id, UUID userId, MemberRole role, Instant joinedAt, String name) {
        this.id = id;
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
        this.name = name;
    }

    public static MemberResponse fromEntity(GroupMember member) {
        return new MemberResponse(
                member.getId(),
                member.getUserId(),
                member.getRole(),
                member.getJoinedAt()
        );
    }

    public static MemberResponse fromEntity(GroupMember member, String name) {
        return new MemberResponse(
                member.getId(),
                member.getUserId(),
                member.getRole(),
                member.getJoinedAt(),
                name
        );
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public MemberRole getRole() {
        return role;
    }

    public void setRole(MemberRole role) {
        this.role = role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
