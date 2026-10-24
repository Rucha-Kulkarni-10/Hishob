package com.hishob.settlement.controller;

import com.hishob.settlement.dto.FriendBalanceResponse;
import com.hishob.settlement.dto.GroupBalanceResponse;
import com.hishob.settlement.dto.PersonalBalanceSummaryResponse;
import com.hishob.settlement.dto.PersonalDebtSimplificationResponse;
import com.hishob.settlement.principal.AuthenticatedUser;
import com.hishob.settlement.service.BalanceCalculationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class BalanceController {

    private final BalanceCalculationService balanceCalculationService;

    public BalanceController(BalanceCalculationService balanceCalculationService) {
        this.balanceCalculationService = balanceCalculationService;
    }

    @GetMapping("/api/groups/{groupId}/balances")
    public ResponseEntity<GroupBalanceResponse> getGroupBalances(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        GroupBalanceResponse response = balanceCalculationService.calculateGroupBalances(groupId, currentUser.getUserId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/personal-balances")
    public ResponseEntity<PersonalBalanceSummaryResponse> getPersonalBalances(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        PersonalBalanceSummaryResponse response = balanceCalculationService.calculatePersonalBalances(currentUser.getUserId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/personal-balances/simplify")
    public ResponseEntity<PersonalDebtSimplificationResponse> simplifyPersonalBalances(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        PersonalDebtSimplificationResponse response = balanceCalculationService.simplifyPersonalBalances(currentUser.getUserId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/friends/{friendUserId}/balance")
    public ResponseEntity<FriendBalanceResponse> getFriendBalance(
            @PathVariable UUID friendUserId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        FriendBalanceResponse response = balanceCalculationService.calculateFriendBalance(currentUser.getUserId(), friendUserId);
        return ResponseEntity.ok(response);
    }

}
