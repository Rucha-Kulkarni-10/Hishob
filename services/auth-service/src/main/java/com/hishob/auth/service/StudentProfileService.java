package com.hishob.auth.service;

import com.hishob.auth.dto.StudentProfileResponse;
import com.hishob.auth.dto.UpdateStudentProfileRequest;
import com.hishob.auth.entity.StudentProfile;
import com.hishob.auth.exception.ResourceNotFoundException;
import com.hishob.auth.repository.StudentProfileRepository;
import com.hishob.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class StudentProfileService {

    private final StudentProfileRepository profileRepository;
    private final UserRepository userRepository;

    public StudentProfileService(StudentProfileRepository profileRepository,
                                 UserRepository userRepository) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse getProfileForUser(UUID userId) {
        requireUserExists(userId);
        return profileRepository.findByUserId(userId)
                .map(StudentProfileResponse::fromEntity)
                .orElse(StudentProfileResponse.defaultForUser(userId));
    }

    @Transactional
    public StudentProfileResponse upsertProfile(UUID userId, UpdateStudentProfileRequest request) {
        requireUserExists(userId);

        StudentProfile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> new StudentProfile(userId));

        profile.setDepartment(sanitizeNullableString(request.getDepartment()));
        profile.setStudyYear(sanitizeNullableString(request.getYear()));
        profile.setSkills(sanitizeList(request.getSkills()));
        profile.setInterests(sanitizeList(request.getInterests()));

        StudentProfile saved = profileRepository.save(profile);
        return StudentProfileResponse.fromEntity(saved);
    }

    /* ── internal helpers ────────────────────────────────── */

    private void requireUserExists(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }
    }

    private static String sanitizeNullableString(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static List<String> sanitizeList(List<String> raw) {
        if (raw == null) return new ArrayList<>();
        List<String> result = new ArrayList<>(raw.size());
        for (String item : raw) {
            if (item == null) continue;
            String trimmed = item.trim();
            if (trimmed.isEmpty()) continue;
            result.add(trimmed);
        }
        return result;
    }
}
