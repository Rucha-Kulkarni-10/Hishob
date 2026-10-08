package com.hishob.expense.controller;

import com.hishob.expense.entity.Expense;
import com.hishob.expense.entity.ExpenseCategory;
import com.hishob.expense.entity.ExpenseSplit;
import com.hishob.expense.entity.SplitType;
import com.hishob.expense.repository.ExpenseRepository;
import com.hishob.expense.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class PersonalExpenseAnalyticsControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ExpenseRepository expenseRepository;

    private MockMvc mockMvc;

    private UUID userId;
    private UUID friendId;
    private String jwtToken;

    private static final String TEST_SECRET = "d3f4u1t-s3cr3t-k3y-f0r-h1sh0b-auth-s3rv1c3-jwt-t0k3n-g3n3r4t10n-2026";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        expenseRepository.deleteAll();

        userId = UUID.randomUUID();
        friendId = UUID.randomUUID();

        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);

        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

        jwtToken = Jwts.builder()
                .subject("personal_tester@example.com")
                .claim("userId", userId.toString())
                .claim("name", "Personal Tester")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("GET /api/personal-expenses/analytics - 200 OK with personal analytics")
    void getPersonalAnalyticsSuccess() throws Exception {
        Expense exp = new Expense(null, "Dinner", new BigDecimal("200.00"), userId, userId, SplitType.EQUAL);
        exp.setCategory(ExpenseCategory.FOOD);
        exp.addSplit(new ExpenseSplit(userId, new BigDecimal("100.00"), SplitType.EQUAL, null));
        exp.addSplit(new ExpenseSplit(friendId, new BigDecimal("100.00"), SplitType.EQUAL, null));
        expenseRepository.save(exp);

        mockMvc.perform(get("/api/personal-expenses/analytics")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpending", is(200.00)))
                .andExpect(jsonPath("$.expenseCount", is(1)))
                .andExpect(jsonPath("$.averageExpense", is(200.00)))
                .andExpect(jsonPath("$.totalPaidByUser", is(200.00)))
                .andExpect(jsonPath("$.totalUserShare", is(100.00)))
                .andExpect(jsonPath("$.categorySpending[0].category", is("FOOD")))
                .andExpect(jsonPath("$.friendSpending[0].friendUserId", is(friendId.toString())));
    }

    @Test
    @DisplayName("GET /api/personal-expenses/analytics - 403 Forbidden without Bearer token")
    void getPersonalAnalyticsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/personal-expenses/analytics")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/personal-expenses/export/csv - 200 OK with CSV headers")
    void exportPersonalCsvSuccess() throws Exception {
        Expense exp = new Expense(null, "Supermarket", new BigDecimal("150.00"), userId, userId, SplitType.EQUAL);
        exp.setCategory(ExpenseCategory.GROCERIES);
        exp.addSplit(new ExpenseSplit(userId, new BigDecimal("75.00"), SplitType.EQUAL, null));
        exp.addSplit(new ExpenseSplit(friendId, new BigDecimal("75.00"), SplitType.EQUAL, null));
        expenseRepository.save(exp);

        mockMvc.perform(get("/api/personal-expenses/export/csv")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"personal-expenses.csv\""))
                .andExpect(content().string(containsString("Date,Description,Category,Total Amount (INR),Friend,Paid By,Split Type,Your Share (INR),Friend Share (INR)")))
                .andExpect(content().string(containsString("\"Supermarket\",\"Groceries\",150.00")));
    }
}
