package com.hishob.expense.controller;

import com.hishob.expense.dto.PersonalAnalyticsResponse;
import com.hishob.expense.principal.AuthenticatedUser;
import com.hishob.expense.service.ExpenseAnalyticsService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/personal-expenses")
public class PersonalExpenseAnalyticsController {

    private final ExpenseAnalyticsService analyticsService;

    public PersonalExpenseAnalyticsController(ExpenseAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/analytics")
    public ResponseEntity<PersonalAnalyticsResponse> getPersonalAnalytics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        PersonalAnalyticsResponse response = analyticsService.getPersonalAnalytics(
                currentUser.getUserId(), startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportPersonalCsv(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        String csvData = analyticsService.exportPersonalExpensesCsv(
                currentUser.getUserId(), startDate, endDate);

        byte[] csvBytes = csvData.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"personal-expenses.csv\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .contentLength(csvBytes.length)
                .body(csvBytes);
    }
}
