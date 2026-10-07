package com.hishob.expense.service;

import com.hishob.expense.client.GroupServiceClient;
import com.hishob.expense.client.SettlementServiceClient;
import com.hishob.expense.dto.GroupAnalyticsResponse;
import com.hishob.expense.dto.MemberSpendingSummary;
import com.hishob.expense.dto.MonthlySpendingSummary;
import com.hishob.expense.entity.Expense;
import com.hishob.expense.entity.ExpenseSplit;
import com.hishob.expense.repository.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ExpenseAnalyticsService {

    private final ExpenseRepository expenseRepository;
    private final GroupServiceClient groupServiceClient;
    private final SettlementServiceClient settlementServiceClient;

    public ExpenseAnalyticsService(
            ExpenseRepository expenseRepository,
            GroupServiceClient groupServiceClient,
            SettlementServiceClient settlementServiceClient) {
        this.expenseRepository = expenseRepository;
        this.groupServiceClient = groupServiceClient;
        this.settlementServiceClient = settlementServiceClient;
    }

    public GroupAnalyticsResponse getGroupAnalytics(
            UUID groupId,
            UUID currentUserId,
            String startDateStr,
            String endDateStr) {

        // 1. Authorization: check group existence and membership
        groupServiceClient.validateGroupExists(groupId);
        groupServiceClient.validateMember(groupId, currentUserId, "Current user");

        // 2. Parse dates if provided
        Instant startInstant = parseDateBoundary(startDateStr, false);
        Instant endInstant = parseDateBoundary(endDateStr, true);

        // 3. Fetch expenses
        List<Expense> expenses = fetchExpenses(groupId, startInstant, endInstant);

        // 4. Calculate total spending, count, average
        long expenseCount = expenses.size();
        BigDecimal totalSpending = expenses.stream()
                .map(Expense::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal averageExpense = expenseCount > 0
                ? totalSpending.divide(BigDecimal.valueOf(expenseCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // 5. Total Settled Volume from Settlement Service
        BigDecimal totalSettledVolume = settlementServiceClient.getCompletedSettlements(groupId).stream()
                .map(SettlementServiceClient.CompletedSettlementData::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // 6. Member Spending breakdown
        List<UUID> memberIds = groupServiceClient.getGroupMemberIds(groupId);
        Set<UUID> allMemberIds = new LinkedHashSet<>(memberIds);
        for (Expense e : expenses) {
            allMemberIds.add(e.getPaidBy());
            for (ExpenseSplit s : e.getSplits()) {
                allMemberIds.add(s.getUserId());
            }
        }

        Map<UUID, BigDecimal> totalPaidMap = new LinkedHashMap<>();
        Map<UUID, BigDecimal> totalShareMap = new LinkedHashMap<>();
        for (UUID id : allMemberIds) {
            totalPaidMap.put(id, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            totalShareMap.put(id, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }

        for (Expense e : expenses) {
            totalPaidMap.merge(e.getPaidBy(), e.getTotalAmount(), BigDecimal::add);
            for (ExpenseSplit split : e.getSplits()) {
                totalShareMap.merge(split.getUserId(), split.getAmount(), BigDecimal::add);
            }
        }

        List<MemberSpendingSummary> memberSummaries = new ArrayList<>();
        for (UUID id : allMemberIds) {
            BigDecimal paid = totalPaidMap.getOrDefault(id, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal share = totalShareMap.getOrDefault(id, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal net = paid.subtract(share).setScale(2, RoundingMode.HALF_UP);

            String status;
            int cmp = net.compareTo(BigDecimal.ZERO);
            if (cmp > 0) {
                status = "OWED";
            } else if (cmp < 0) {
                status = "OWES";
            } else {
                status = "SETTLED";
            }

            memberSummaries.add(new MemberSpendingSummary(id, paid, share, net, status));
        }

        // 7. Monthly Trends (grouped ONLY by createdAt)
        Map<YearMonth, List<Expense>> monthlyMap = expenses.stream()
                .collect(Collectors.groupingBy(
                        e -> YearMonth.from(e.getCreatedAt().atZone(ZoneOffset.UTC)),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        DateTimeFormatter monthLabelFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
        List<MonthlySpendingSummary> monthlyTrends = monthlyMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    YearMonth ym = entry.getKey();
                    List<Expense> monthExpenses = entry.getValue();
                    BigDecimal monthTotal = monthExpenses.stream()
                            .map(Expense::getTotalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(2, RoundingMode.HALF_UP);
                    return new MonthlySpendingSummary(
                            ym.toString(),
                            ym.format(monthLabelFormat),
                            monthTotal,
                            monthExpenses.size()
                    );
                })
                .collect(Collectors.toList());

        return new GroupAnalyticsResponse(
                groupId,
                totalSpending,
                expenseCount,
                averageExpense,
                totalSettledVolume,
                memberSummaries,
                monthlyTrends
        );
    }

    public String exportExpensesCsv(
            UUID groupId,
            UUID currentUserId,
            String startDateStr,
            String endDateStr) {

        groupServiceClient.validateGroupExists(groupId);
        groupServiceClient.validateMember(groupId, currentUserId, "Current user");

        Instant startInstant = parseDateBoundary(startDateStr, false);
        Instant endInstant = parseDateBoundary(endDateStr, true);

        List<Expense> expenses = fetchExpenses(groupId, startInstant, endInstant);
        // Sort chronological for CSV export
        List<Expense> sortedExpenses = new ArrayList<>(expenses);
        sortedExpenses.sort(Comparator.comparing(Expense::getCreatedAt));

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

        StringBuilder sb = new StringBuilder();
        sb.append("Date,Description,Total Amount (INR),Paid By,Split Type,Participants Count\r\n");

        for (Expense e : sortedExpenses) {
            String dateStr = dtf.format(e.getCreatedAt());
            String desc = escapeCsv(e.getDescription());
            String amount = e.getTotalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();
            String paidBy = "User-" + e.getPaidBy().toString().substring(0, 8);
            String splitType = e.getSplitType() != null ? e.getSplitType().name() : "EQUAL";
            int participants = e.getSplits() != null ? e.getSplits().size() : 0;

            sb.append(dateStr).append(',')
                    .append(desc).append(',')
                    .append(amount).append(',')
                    .append(paidBy).append(',')
                    .append(splitType).append(',')
                    .append(participants).append("\r\n");
        }

        return sb.toString();
    }

    private List<Expense> fetchExpenses(UUID groupId, Instant startInstant, Instant endInstant) {
        if (startInstant != null && endInstant != null) {
            return expenseRepository.findByGroupIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                    groupId, startInstant, endInstant);
        } else if (startInstant != null) {
            return expenseRepository.findByGroupIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                    groupId, startInstant);
        } else if (endInstant != null) {
            return expenseRepository.findByGroupIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
                    groupId, endInstant);
        } else {
            return expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
        }
    }

    private Instant parseDateBoundary(String dateStr, boolean isEnd) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(dateStr.trim());
        } catch (Exception e1) {
            try {
                LocalDate ld = LocalDate.parse(dateStr.trim());
                return ld.atTime(isEnd ? LocalTime.MAX : LocalTime.MIN).toInstant(ZoneOffset.UTC);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "\"\"";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
