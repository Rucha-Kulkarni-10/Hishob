package com.hishob.expense.controller;

import com.hishob.expense.dto.DashboardOverviewResponse;
import com.hishob.expense.principal.AuthenticatedUser;
import com.hishob.expense.service.DashboardOverviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardOverviewController {

    private final DashboardOverviewService dashboardOverviewService;

    public DashboardOverviewController(DashboardOverviewService dashboardOverviewService) {
        this.dashboardOverviewService = dashboardOverviewService;
    }

    @GetMapping("/api/expenses/overview")
    public ResponseEntity<DashboardOverviewResponse> getOverview(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(dashboardOverviewService.getOverview(currentUser.getUserId()));
    }
}
