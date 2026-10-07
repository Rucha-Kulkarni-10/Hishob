# HISHOB - Final Balance Bug Trace Report

## Executive Summary

**Root Cause:** Expense Service throws HTTP 500 Internal Server Error for group `7a223a25-0c66-4bb1-ad22-9cb4c085d387`

**Location:** Backend - Expense Service `/internal/groups/{groupId}/expenses` endpoint

**Impact:** Cascading failure prevents Balance Service from calculating balances, causing frontend to display "Expense Service unavailable: cannot fetch expenses"

**Frontend Status:** ✅ Code is correct - no frontend changes needed

---

## Evidence Chain

### 1. Browser Request (Inferred)

**User's Browser URL:**
```
/groups/7a223a25-0c66-4bb1-ad22-9cb4c085d387/balances
```

**Frontend Makes:**
```
GET http://localhost:8080/api/groups/7a223a25-0c66-4bb1-ad22-9cb4c085d387/balances
Authorization: Bearer <JWT>
```

**Expected:** HTTP 200 with balance data  
**Actual:** HTTP 503 with error message "Expense Service unavailable: cannot fetch expenses"

### 2. Gateway → Balance Service

**Gateway Routes:**
```
GET /api/groups/{groupId}/balances
→ Balance Service :8084
```

**Balance Service Receives:** Request for group `7a223a25-0c66-4bb1-ad22-9cb4c085d387`

### 3. Balance Service → Expense Service (Internal)

**Balance Service Attempts:**
```
GET http://localhost:8083/internal/groups/7a223a25-0c66-4bb1-ad22-9cb4c085d387/expenses
X-Internal-Token: dev-internal-token-hishob-service-2026
```

**Direct Test Result:**
```
✗ FAILED - Status: 500
Error: The remote server returned an error: (500) Internal Server Error.
```

**Evidence:** Tested directly via PowerShell - confirmed 500 error

### 4. Error Propagation

**Expense Service:** Returns HTTP 500  
**↓**  
**ExpenseServiceClient:** Catches exception, throws `ServiceUnavailableException("Expense Service unavailable: cannot fetch expenses")`  
**↓**  
**Balance Service:** Returns HTTP 503 to Gateway  
**↓**  
**Gateway:** Forwards 503 to frontend  
**↓**  
**Frontend:** Displays error message from response body

---

## Comparison: Working vs. Failing Group

### Test Group (Created During Diagnosis): **WORKS**

```
Group ID: 72782a15-1890-4bff-9a12-e3daf05aae9e

GET http://localhost:8083/internal/groups/72782a15-1890-4bff-9a12-e3daf05aae9e/expenses
→ HTTP 200 OK
→ Response: [] (empty array - no expenses)

GET http://localhost:8080/api/groups/72782a25-1890-4bff-9a12-e3daf05aae9e/balances
→ HTTP 200 OK
→ Response: { groupId: "...", balances: [{ userId: "...", netBalance: 0.00, status: "SETTLED" }] }
```

### User's Group: **FAILS**

```
Group ID: 7a223a25-0c66-4bb1-ad22-9cb4c085d387

GET http://localhost:8083/internal/groups/7a223a25-0c66-4bb1-ad22-9cb4c085d387/expenses
→ HTTP 500 Internal Server Error
→ No response body

GET http://localhost:8080/api/groups/7a223a25-0c66-4bb1-ad22-9cb4c085d387/balances
→ HTTP 503 Service Unavailable
→ Response: { message: "Expense Service unavailable: cannot fetch expenses" }
```

**Conclusion:** The issue is specific to this group's data, not a general service failure.

---

## Root Cause Analysis

### Expense Service Internal Controller

**File:** `services/expense-service/src/main/java/com/hishob/expense/controller/ExpenseInternalController.java`

**Code:**
```java
@GetMapping("/{groupId}/expenses")
public ResponseEntity<List<InternalExpenseData>> getGroupExpenses(@PathVariable UUID groupId) {
    List<Expense> expenses = expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
    List<InternalExpenseData> result = expenses.stream()
        .map(e -> new InternalExpenseData(
            e.getId(),
            e.getGroupId(),
            e.getTotalAmount(),
            e.getPaidBy(),
            e.getSplits().stream()  // ← Potential NullPointerException if splits is null
                .map(s -> new InternalSplitData(s.getUserId(), s.getAmount()))
                .collect(Collectors.toList())
        ))
        .collect(Collectors.toList());
    return ResponseEntity.ok(result);
}
```

### Possible Causes of HTTP 500

1. **Null Splits Collection**
   - If `e.getSplits()` returns null instead of empty list
   - Calling `.stream()` on null → `NullPointerException`

2. **Lazy Loading Exception**
   - If `splits` relationship is lazy-loaded
   - Accessing `e.getSplits()` outside transaction → `LazyInitializationException`

3. **Database Constraint Violation**
   - Corrupt foreign key references
   - Invalid UUID values

4. **Serialization Error**
   - Invalid data types that can't be mapped to DTO

### Most Likely: Null Splits or Lazy Loading

The repository query doesn't fetch splits eagerly:
```java
expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId)
```

If the Expense entity has `@OneToMany(fetch = FetchType.LAZY)` for splits, accessing them outside the transaction context causes `LazyInitializationException`.

---

## Files Analyzed

### Frontend (All Correct ✅)

1. **src/services/balanceService.ts**
   - ✅ Correct URL: `/api/groups/{groupId}/balances`
   - ✅ Correct URL: `/api/groups/{groupId}/settlements`
   - ✅ Uses Gateway base URL: `http://localhost:8080`

2. **src/services/api.ts**
   - ✅ Correct baseURL: `http://localhost:8080`
   - ✅ JWT interceptor working

3. **src/pages/groups/GroupBalancesPage.tsx**
   - ✅ Imports correct balanceService functions
   - ✅ Error handling displays backend error messages
   - ✅ No direct microservice calls

4. **.env**
   - ✅ `VITE_API_BASE_URL=http://localhost:8080`

### Backend (Issue Found ❌)

1. **services/expense-service/.../ExpenseInternalController.java**
   - ❌ Missing null check for `e.getSplits()`
   - ❌ Possible lazy loading issue
   - ❌ No error handling / try-catch

2. **services/balance-service/.../ExpenseServiceClient.java**
   - ⚠️  Swallows actual exception details
   - Generic error: "Expense Service unavailable: cannot fetch expenses"
   - Hides root cause (500 error from Expense Service)

---

## Recommended Fix

### Option 1: Add Null Check (Quick Fix)

**File:** `services/expense-service/src/main/java/com/hishob/expense/controller/ExpenseInternalController.java`

```java
@GetMapping("/{groupId}/expenses")
public ResponseEntity<List<InternalExpenseData>> getGroupExpenses(@PathVariable UUID groupId) {
    List<Expense> expenses = expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
    List<InternalExpenseData> result = expenses.stream()
        .map(e -> new InternalExpenseData(
            e.getId(),
            e.getGroupId(),
            e.getTotalAmount(),
            e.getPaidBy(),
            e.getSplits() != null ? e.getSplits().stream()
                .map(s -> new InternalSplitData(s.getUserId(), s.getAmount()))
                .collect(Collectors.toList())
                : List.of()  // ← Return empty list if splits is null
        ))
        .collect(Collectors.toList());
    return ResponseEntity.ok(result);
}
```

### Option 2: Eager Fetch (Proper Fix)

**File:** `services/expense-service/src/main/java/com/hishob/expense/repository/ExpenseRepository.java`

```java
@Query("SELECT e FROM Expense e LEFT JOIN FETCH e.splits WHERE e.groupId = :groupId ORDER BY e.createdAt DESC")
List<Expense> findByGroupIdOrderByCreatedAtDesc(@Param("groupId") UUID groupId);
```

### Option 3: Initialize Splits in Entity

**File:** `services/expense-service/src/main/java/com/hishob/expense/entity/Expense.java`

```java
@OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
private List<ExpenseSplit> splits = new ArrayList<>();  // ← Initialize to empty list
```

---

## Test Results

### Before Fix:
```
GET http://localhost:8083/internal/groups/7a223a25-0c66-4bb1-ad22-9cb4c085d387/expenses
→ HTTP 500 Internal Server Error

Frontend displays: "Expense Service unavailable: cannot fetch expenses"
```

### After Fix (Expected):
```
GET http://localhost:8083/internal/groups/7a223a25-0c66-4bb1-ad22-9cb4c085d387/expenses
→ HTTP 200 OK
→ Response: [{ expenseId: "...", splits: [...] }] or []

GET http://localhost:8080/api/groups/7a223a25-0c66-4bb1-ad22-9cb4c085d387/balances
→ HTTP 200 OK
→ Response: { groupId: "...", balances: [...] }

Frontend displays: Balance data correctly
```

---

## No Frontend Changes Needed

**Frontend Status:** ✅ **CORRECT**

The frontend code is working as designed:
- ✅ Makes correct API calls to Gateway
- ✅ Uses correct URLs and authentication
- ✅ Properly handles and displays backend error messages
- ✅ No hardcoded microservice ports
- ✅ No unnecessary API calls

**The bug is entirely in the Expense Service backend.**

---

## Build/Test Steps (After Applying Fix)

1. **Apply one of the recommended fixes** to Expense Service

2. **Rebuild Expense Service:**
   ```bash
   cd d:\Projects\My projects\Hishob
   .\mvnw.cmd package -pl services/expense-service -am -DskipTests
   ```

3. **Restart Expense Service:**
   - Stop current Expense Service process
   - Run: `java -jar services/expense-service/target/expense-service-0.0.1-SNAPSHOT.jar`

4. **Verify Fix:**
   ```powershell
   $headers = @{"X-Internal-Token"="dev-internal-token-hishob-service-2026"}
   Invoke-RestMethod -Uri "http://localhost:8083/internal/groups/7a223a25-0c66-4bb1-ad22-9cb4c085d387/expenses" -Headers $headers
   # Should return 200 OK
   ```

5. **Test Frontend:**
   - Navigate to: `/groups/7a223a25-0c66-4bb1-ad22-9cb4c085d387/balances`
   - Should display balance data without errors

---

## Summary

| Item | Status |
|------|--------|
| **Root Cause** | Expense Service throws 500 for specific group |
| **Location** | Backend - ExpenseInternalController |
| **Browser Request** | GET /api/groups/{groupId}/balances |
| **Gateway Status** | 503 (propagated from Balance Service) |
| **Response Error** | "Expense Service unavailable: cannot fetch expenses" |
| **Actual Issue** | Expense Service internal endpoint returns 500 |
| **Frontend Code** | ✅ Correct - no changes needed |
| **Backend Fix Required** | ❌ Yes - Expense Service needs null check or eager fetch |
| **Files to Change** | ExpenseInternalController.java OR ExpenseRepository.java OR Expense.java |

---

**Investigation Date:** 2026-10-04  
**Test Group (Working):** 72782a15-1890-4bff-9a12-e3daf05aae9e  
**User Group (Failing):** 7a223a25-0c66-4bb1-ad22-9cb4c085d387  
**Confirmed:** Direct API test shows 500 error from Expense Service internal endpoint
