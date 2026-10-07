package com.hishob.auth.dto;

import com.hishob.auth.entity.StudentProfile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StudentProfileResponse {

    private UUID userId;
    private String department;
    private String year;
    private List<String> skills;
    private List<String> interests;
    private Instant createdAt;
    private Instant updatedAt;

    public StudentProfileResponse() {
    }

    public StudentProfileResponse(UUID userId,
                                  String department,
                                  String year,
                                  List<String> skills,
                                  List<String> interests,
                                  Instant createdAt,
                                  Instant updatedAt) {
        this.userId = userId;
        this.department = department;
        this.year = year;
        this.skills = skills != null ? skills : new ArrayList<>();
        this.interests = interests != null ? interests : new ArrayList<>();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static StudentProfileResponse fromEntity(StudentProfile profile) {
        return new StudentProfileResponse(
                profile.getUserId(),
                profile.getDepartment(),
                profile.getStudyYear(),
                profile.getSkills(),
                profile.getInterests(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }

    public static StudentProfileResponse defaultForUser(UUID userId) {
        Instant now = Instant.now();
        return new StudentProfileResponse(
                userId,
                null,
                null,
                new ArrayList<>(),
                new ArrayList<>(),
                now,
                now
        );
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public List<String> getInterests() {
        return interests;
    }

    public void setInterests(List<String> interests) {
        this.interests = interests;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
