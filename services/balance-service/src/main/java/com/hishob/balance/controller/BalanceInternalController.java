package com.hishob.balance.controller;

import com.hishob.balance.dto.FriendBalanceResponse;
import com.hishob.balance.dto.GroupBalanceResponse;
import com.hishob.balance.dto.PersonalDebtSimplificationResponse;
import com.hishob.balance.service.BalanceCalculationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class BalanceInternalController {

    private final BalanceCalculationService balanceCalculationService;

    public BalanceInternalController(BalanceCalculationService balanceCalculationService) {
        this.balanceCalculationService = balanceCalculationService;
    }

    /**
     * Returns net balances for all group members (including completed settlements).
     * Used by Settlement Service for debt simplification and pre-settlement validation.
     * Protected by X-Internal-Token via InternalAuthFilter.
     * Skips caller membership validation — caller is a trusted internal service.
     */
    @GetMapping("/internal/groups/{groupId}/balances")
    public ResponseEntity<GroupBalanceResponse> getGroupBalancesInternal(@PathVariable UUID groupId) {
        return ResponseEntity.ok(balanceCalculationService.calculateGroupBalancesInternal(groupId));
    }

    @GetMapping("/internal/personal-balances/users/{userId}/simplify")
    public ResponseEntity<PersonalDebtSimplificationResponse> simplifyPersonalBalancesInternal(@PathVariable UUID userId) {
        return ResponseEntity.ok(balanceCalculationService.simplifyPersonalBalances(userId));
    }

    /**
     * Returns direct pairwise balance between two users.
     * Used by Settlement Service to validate outstanding personal debt between payer and recipient.
     * Protected by X-Internal-Token via InternalAuthFilter.
     */
    @GetMapping("/internal/personal-balances/users/{currentUserId}/friends/{friendUserId}")
    public ResponseEntity<FriendBalanceResponse> getFriendBalanceInternal(
            @PathVariable UUID currentUserId,
            @PathVariable UUID friendUserId) {
        return ResponseEntity.ok(balanceCalculationService.calculateFriendBalance(currentUserId, friendUserId));
    }

}
