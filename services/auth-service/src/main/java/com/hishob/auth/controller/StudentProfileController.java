package com.hishob.auth.controller;

import com.hishob.auth.dto.StudentProfileResponse;
import com.hishob.auth.dto.UpdateStudentProfileRequest;
import com.hishob.auth.entity.User;
import com.hishob.auth.exception.ResourceNotFoundException;
import com.hishob.auth.repository.UserRepository;
import com.hishob.auth.service.StudentProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/students/me/profile", "/api/auth/students/me/profile"})
public class StudentProfileController {

    private final StudentProfileService studentProfileService;
    private final UserRepository userRepository;

    public StudentProfileController(StudentProfileService studentProfileService,
                                     UserRepository userRepository) {
        this.studentProfileService = studentProfileService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<StudentProfileResponse> getMyProfile(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        StudentProfileResponse profile = studentProfileService.getProfileForUser(user.getId());
        return ResponseEntity.ok(profile);
    }

    @PutMapping
    public ResponseEntity<StudentProfileResponse> updateMyProfile(
            @Valid @RequestBody UpdateStudentProfileRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        StudentProfileResponse updated = studentProfileService.upsertProfile(user.getId(), request);
        return ResponseEntity.ok(updated);
    }

    private User resolveCurrentUser(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null) {
            throw new ResourceNotFoundException("Authenticated user not available");
        }
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found for email: " + userDetails.getUsername()));
    }
}
