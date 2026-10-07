package com.hishob.settlement.controller;

import com.hishob.settlement.dto.RecordSettlementRequest;
import com.hishob.settlement.dto.RecordPersonalSettlementRequest;
import com.hishob.settlement.dto.SettlementHistoryResponse;
import com.hishob.settlement.dto.SettlementSuggestion;
import com.hishob.settlement.principal.AuthenticatedUser;
import com.hishob.settlement.service.SettlementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping("/api/groups/{groupId}/settlements")
    public ResponseEntity<List<SettlementSuggestion>> getSuggestions(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(settlementService.getSuggestions(groupId, currentUser.getUserId()));
    }

    @PostMapping("/api/groups/{groupId}/settlements")
    public ResponseEntity<SettlementHistoryResponse> recordSettlement(
            @PathVariable UUID groupId,
            @Valid @RequestBody RecordSettlementRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        SettlementHistoryResponse response = settlementService.recordSettlement(
                groupId, currentUser.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/groups/{groupId}/settlements/history")
    public ResponseEntity<List<SettlementHistoryResponse>> getHistory(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(settlementService.getHistory(groupId, currentUser.getUserId()));
    }

    @PostMapping("/api/personal-settlements")
    public ResponseEntity<SettlementHistoryResponse> recordPersonalSettlement(
            @Valid @RequestBody RecordPersonalSettlementRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        SettlementHistoryResponse response = settlementService.recordPersonalSettlement(
                currentUser.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/personal-settlements/history")
    public ResponseEntity<List<SettlementHistoryResponse>> getPersonalHistory(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(settlementService.getPersonalHistory(currentUser.getUserId()));
    }

    @GetMapping("/api/friends/{friendUserId}/settlements")
    public ResponseEntity<List<SettlementHistoryResponse>> getFriendPersonalHistory(
            @PathVariable UUID friendUserId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(settlementService.getFriendPersonalHistory(currentUser.getUserId(), friendUserId));
    }

}
