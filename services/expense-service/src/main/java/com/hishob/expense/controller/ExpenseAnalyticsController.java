package com.hishob.expense.controller;

import com.hishob.expense.dto.GroupAnalyticsResponse;
import com.hishob.expense.principal.AuthenticatedUser;
import com.hishob.expense.service.ExpenseAnalyticsService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups/{groupId}/expenses")
public class ExpenseAnalyticsController {

    private final ExpenseAnalyticsService analyticsService;

    public ExpenseAnalyticsController(ExpenseAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/analytics")
    public ResponseEntity<GroupAnalyticsResponse> getAnalytics(
            @PathVariable UUID groupId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        GroupAnalyticsResponse response = analyticsService.getGroupAnalytics(
                groupId, currentUser.getUserId(), startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv(
            @PathVariable UUID groupId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        String csvData = analyticsService.exportExpensesCsv(
                groupId, currentUser.getUserId(), startDate, endDate);

        byte[] csvBytes = csvData.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"expenses-" + groupId + ".csv\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .contentLength(csvBytes.length)
                .body(csvBytes);
    }
}
