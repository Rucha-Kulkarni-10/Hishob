package com.hishob.settlement.controller;

import com.hishob.settlement.dto.InternalCompletedSettlementData;
import com.hishob.settlement.service.SettlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Internal-only controller for Settlement Service.
 *
 * All endpoints under /internal/** are:
 *   - Protected by X-Internal-Token via InternalAuthFilter
 *   - NOT routed through the API Gateway
 *   - NOT accessible by public clients
 *
 * Used by Balance Service to fetch completed settlements for balance adjustment.
 */
@RestController
public class SettlementInternalController {

    private final SettlementService settlementService;

    public SettlementInternalController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    /**
     * Returns all COMPLETED settlements for a group.
     * PENDING settlements are excluded by contract.
     * Used by Balance Service to adjust expense-derived net balances.
     *
     * Requires: X-Internal-Token header.
     * Not accessible through the API Gateway.
     */
    @GetMapping("/internal/groups/{groupId}/settlements/completed")
    public ResponseEntity<List<InternalCompletedSettlementData>> getCompletedSettlements(
            @PathVariable UUID groupId) {
        return ResponseEntity.ok(settlementService.getCompletedSettlements(groupId));
    }

    @GetMapping("/internal/personal-settlements/users/{userId}/completed")
    public ResponseEntity<List<InternalCompletedSettlementData>> getCompletedPersonalSettlements(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(settlementService.getCompletedPersonalSettlements(userId));
    }

}
