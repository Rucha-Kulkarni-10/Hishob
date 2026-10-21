package com.hishob.expense.service;

import com.hishob.expense.client.SettlementServiceClient;
import com.hishob.expense.dto.CategorySpendingSummary;
import com.hishob.expense.dto.FriendSpendingSummary;
import com.hishob.expense.dto.GroupAnalyticsResponse;
import com.hishob.expense.dto.MemberSpendingSummary;
import com.hishob.expense.dto.MonthlySpendingSummary;
import com.hishob.expense.dto.PersonalAnalyticsResponse;
import com.hishob.expense.entity.Expense;
import com.hishob.expense.entity.ExpenseCategory;
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
    private final GroupService groupService;
    private final SettlementServiceClient settlementServiceClient;

    public ExpenseAnalyticsService(
            ExpenseRepository expenseRepository,
            GroupService groupService,
            SettlementServiceClient settlementServiceClient) {
        this.expenseRepository = expenseRepository;
        this.groupService = groupService;
        this.settlementServiceClient = settlementServiceClient;
    }

    public GroupAnalyticsResponse getGroupAnalytics(
            UUID groupId,
            UUID currentUserId,
            String startDateStr,
            String endDateStr) {

        // 1. Authorization: check group existence and membership
        groupService.validateGroupExists(groupId);
        groupService.validateMember(groupId, currentUserId, "Current user");

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
        List<UUID> memberIds = groupService.getGroupMemberIds(groupId);
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

        // 8. Category Spending breakdown
        Map<ExpenseCategory, List<Expense>> categoryMap = expenses.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getCategory() != null ? e.getCategory() : ExpenseCategory.OTHER,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<CategorySpendingSummary> categorySummaries = categoryMap.entrySet().stream()
                .map(entry -> {
                    ExpenseCategory cat = entry.getKey();
                    List<Expense> catExpenses = entry.getValue();
                    BigDecimal catTotal = catExpenses.stream()
                            .map(Expense::getTotalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(2, RoundingMode.HALF_UP);
                    double percentage = totalSpending.compareTo(BigDecimal.ZERO) > 0
                            ? catTotal.multiply(BigDecimal.valueOf(100))
                                    .divide(totalSpending, 2, RoundingMode.HALF_UP)
                                    .doubleValue()
                            : 0.0;
                    return new CategorySpendingSummary(cat, catTotal, catExpenses.size(), percentage);
                })
                .sorted((a, b) -> b.getTotalAmount().compareTo(a.getTotalAmount()))
                .collect(Collectors.toList());

        return new GroupAnalyticsResponse(
                groupId,
                totalSpending,
                expenseCount,
                averageExpense,
                totalSettledVolume,
                memberSummaries,
                monthlyTrends,
                categorySummaries
        );
    }

    public String exportExpensesCsv(
            UUID groupId,
            UUID currentUserId,
            String startDateStr,
            String endDateStr) {

        groupService.validateGroupExists(groupId);
        groupService.validateMember(groupId, currentUserId, "Current user");

        Instant startInstant = parseDateBoundary(startDateStr, false);
        Instant endInstant = parseDateBoundary(endDateStr, true);

        List<Expense> expenses = fetchExpenses(groupId, startInstant, endInstant);
        // Sort chronological for CSV export
        List<Expense> sortedExpenses = new ArrayList<>(expenses);
        sortedExpenses.sort(Comparator.comparing(Expense::getCreatedAt));

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

        StringBuilder sb = new StringBuilder();
        sb.append("Date,Description,Category,Total Amount (INR),Paid By,Split Type,Participants Count\r\n");

        for (Expense e : sortedExpenses) {
            String dateStr = dtf.format(e.getCreatedAt());
            String desc = escapeCsv(e.getDescription());
            String categoryStr = formatCategoryName(e.getCategory());
            String amount = e.getTotalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();
            String paidBy = "User-" + e.getPaidBy().toString().substring(0, 8);
            String splitType = e.getSplitType() != null ? e.getSplitType().name() : "EQUAL";
            int participants = e.getSplits() != null ? e.getSplits().size() : 0;

            sb.append(dateStr).append(',')
                    .append(desc).append(',')
                    .append(escapeCsv(categoryStr)).append(',')
                    .append(amount).append(',')
                    .append(paidBy).append(',')
                    .append(splitType).append(',')
                    .append(participants).append("\r\n");
        }

        return sb.toString();
    }

    public PersonalAnalyticsResponse getPersonalAnalytics(
            UUID currentUserId,
            String startDateStr,
            String endDateStr) {

        Instant startInstant = parseDateBoundary(startDateStr, false);
        Instant endInstant = parseDateBoundary(endDateStr, true);

        List<Expense> allPersonalExpenses = expenseRepository.findPersonalExpensesForUser(currentUserId);
        List<Expense> expenses = filterByDateRange(allPersonalExpenses, startInstant, endInstant);

        long expenseCount = expenses.size();
        BigDecimal totalSpending = expenses.stream()
                .map(Expense::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal averageExpense = expenseCount > 0
                ? totalSpending.divide(BigDecimal.valueOf(expenseCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalPaidByUser = expenses.stream()
                .filter(e -> currentUserId.equals(e.getPaidBy()))
                .map(Expense::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalUserShare = expenses.stream()
                .flatMap(e -> e.getSplits().stream())
                .filter(s -> currentUserId.equals(s.getUserId()))
                .map(ExpenseSplit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // Category breakdown
        Map<ExpenseCategory, List<Expense>> categoryMap = expenses.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getCategory() != null ? e.getCategory() : ExpenseCategory.OTHER,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<CategorySpendingSummary> categorySummaries = categoryMap.entrySet().stream()
                .map(entry -> {
                    ExpenseCategory cat = entry.getKey();
                    List<Expense> catExpenses = entry.getValue();
                    BigDecimal catTotal = catExpenses.stream()
                            .map(Expense::getTotalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(2, RoundingMode.HALF_UP);
                    double percentage = totalSpending.compareTo(BigDecimal.ZERO) > 0
                            ? catTotal.multiply(BigDecimal.valueOf(100))
                                    .divide(totalSpending, 2, RoundingMode.HALF_UP)
                                    .doubleValue()
                            : 0.0;
                    return new CategorySpendingSummary(cat, catTotal, catExpenses.size(), percentage);
                })
                .sorted((a, b) -> b.getTotalAmount().compareTo(a.getTotalAmount()))
                .collect(Collectors.toList());

        // Friend breakdown
        Map<UUID, List<Expense>> friendExpensesMap = new LinkedHashMap<>();
        for (Expense e : expenses) {
            Set<UUID> otherUsers = new LinkedHashSet<>();
            if (e.getPaidBy() != null && !currentUserId.equals(e.getPaidBy())) {
                otherUsers.add(e.getPaidBy());
            }
            if (e.getSplits() != null) {
                for (ExpenseSplit s : e.getSplits()) {
                    if (s.getUserId() != null && !currentUserId.equals(s.getUserId())) {
                        otherUsers.add(s.getUserId());
                    }
                }
            }
            for (UUID friendId : otherUsers) {
                friendExpensesMap.computeIfAbsent(friendId, k -> new ArrayList<>()).add(e);
            }
        }

        List<com.hishob.expense.dto.FriendSpendingSummary> friendSummaries = friendExpensesMap.entrySet().stream()
                .map(entry -> {
                    UUID friendId = entry.getKey();
                    List<Expense> fExpenses = entry.getValue();
                    BigDecimal fTotal = fExpenses.stream()
                            .map(Expense::getTotalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(2, RoundingMode.HALF_UP);

                    BigDecimal uShare = fExpenses.stream()
                            .flatMap(e -> e.getSplits().stream())
                            .filter(s -> currentUserId.equals(s.getUserId()))
                            .map(ExpenseSplit::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(2, RoundingMode.HALF_UP);

                    BigDecimal fShare = fExpenses.stream()
                            .flatMap(e -> e.getSplits().stream())
                            .filter(s -> friendId.equals(s.getUserId()))
                            .map(ExpenseSplit::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(2, RoundingMode.HALF_UP);

                    String friendName = "User-" + friendId.toString().substring(0, 8);
                    return new com.hishob.expense.dto.FriendSpendingSummary(
                            friendId, friendName, fTotal, fExpenses.size(), uShare, fShare);
                })
                .sorted((a, b) -> b.getTotalAmount().compareTo(a.getTotalAmount()))
                .collect(Collectors.toList());

        // Monthly trends
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

        return new com.hishob.expense.dto.PersonalAnalyticsResponse(
                totalSpending,
                expenseCount,
                averageExpense,
                totalPaidByUser,
                totalUserShare,
                categorySummaries,
                friendSummaries,
                monthlyTrends
        );
    }

    public String exportPersonalExpensesCsv(
            UUID currentUserId,
            String startDateStr,
            String endDateStr) {

        Instant startInstant = parseDateBoundary(startDateStr, false);
        Instant endInstant = parseDateBoundary(endDateStr, true);

        List<Expense> allPersonalExpenses = expenseRepository.findPersonalExpensesForUser(currentUserId);
        List<Expense> expenses = filterByDateRange(allPersonalExpenses, startInstant, endInstant);

        List<Expense> sortedExpenses = new ArrayList<>(expenses);
        sortedExpenses.sort(Comparator.comparing(Expense::getCreatedAt));

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

        StringBuilder sb = new StringBuilder();
        sb.append("Date,Description,Category,Total Amount (INR),Friend,Paid By,Split Type,Your Share (INR),Friend Share (INR)\r\n");

        for (Expense e : sortedExpenses) {
            String dateStr = dtf.format(e.getCreatedAt());
            String desc = escapeCsv(e.getDescription());
            String categoryStr = formatCategoryName(e.getCategory());
            String totalAmount = e.getTotalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();

            // Resolve friend
            UUID friendId = null;
            if (e.getSplits() != null) {
                for (ExpenseSplit s : e.getSplits()) {
                    if (s.getUserId() != null && !currentUserId.equals(s.getUserId())) {
                        friendId = s.getUserId();
                        break;
                    }
                }
            }
            if (friendId == null && e.getPaidBy() != null && !currentUserId.equals(e.getPaidBy())) {
                friendId = e.getPaidBy();
            }

            String friendStr = friendId != null ? "User-" + friendId.toString().substring(0, 8) : "N/A";
            String paidByStr = currentUserId.equals(e.getPaidBy()) ? "You" : (friendId != null ? "User-" + friendId.toString().substring(0, 8) : "User-" + e.getPaidBy().toString().substring(0, 8));
            String splitType = e.getSplitType() != null ? e.getSplitType().name() : "EQUAL";

            BigDecimal yourShare = BigDecimal.ZERO;
            BigDecimal friendShare = BigDecimal.ZERO;
            if (e.getSplits() != null) {
                for (ExpenseSplit s : e.getSplits()) {
                    if (currentUserId.equals(s.getUserId())) {
                        yourShare = s.getAmount().setScale(2, RoundingMode.HALF_UP);
                    } else if (friendId != null && friendId.equals(s.getUserId())) {
                        friendShare = s.getAmount().setScale(2, RoundingMode.HALF_UP);
                    }
                }
            }

            sb.append(dateStr).append(',')
                    .append(desc).append(',')
                    .append(escapeCsv(categoryStr)).append(',')
                    .append(totalAmount).append(',')
                    .append(friendStr).append(',')
                    .append(paidByStr).append(',')
                    .append(splitType).append(',')
                    .append(yourShare.toPlainString()).append(',')
                    .append(friendShare.toPlainString()).append("\r\n");
        }

        return sb.toString();
    }

    private List<Expense> filterByDateRange(List<Expense> expenses, Instant start, Instant end) {
        if (start == null && end == null) {
            return expenses;
        }
        return expenses.stream()
                .filter(e -> {
                    if (start != null && e.getCreatedAt().isBefore(start)) {
                        return false;
                    }
                    if (end != null && e.getCreatedAt().isAfter(end)) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    private String formatCategoryName(ExpenseCategory category) {
        if (category == null) return "Other";
        return switch (category) {
            case FOOD -> "Food & Dining";
            case GROCERIES -> "Groceries";
            case TRAVEL -> "Travel & Transit";
            case SHOPPING -> "Shopping";
            case ENTERTAINMENT -> "Entertainment";
            case BILLS -> "Bills & Utilities";
            case HEALTH -> "Health & Fitness";
            case EDUCATION -> "Education";
            case OTHER -> "Other";
        };
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
