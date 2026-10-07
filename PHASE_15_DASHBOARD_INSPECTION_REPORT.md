# Phase 15: Dashboard Frontend - Inspection Report

**Date:** October 2, 2026  
**Status:** 🔍 **INSPECTION COMPLETE - NO CODE MODIFIED**

---

## Executive Summary

The Dashboard page is currently a **placeholder** showing only a welcome message and a "Go to My Groups" button. This inspection identifies what can be implemented using **EXISTING backend APIs** without any backend modifications.

### Key Findings

✅ **Can Implement Immediately:**
- You owe / You are owed summary (aggregated from existing group balances)
- My Groups list with member counts
- Recent expenses from all groups

⚠️ **Cannot Implement Without Backend Changes:**
- **Recent Activity feed** (combined expenses + settlements chronologically)
  - Requires a new user-level aggregated endpoint or client-side merging of multiple APIs
- **Cross-group balance summary** (total you owe across all groups, total owed to you)
  - Requires fetching balances from EVERY group individually (expensive)

---

## 1. Current DashboardPage.tsx Implementation

**Location:** `frontend/hishob-web/src/pages/dashboard/DashboardPage.tsx`

### Currently Displayed
- Welcome message: "Welcome back, {user.name}!"
- User email display
- **User ID panel** with Copy button (for sharing with group owners during Add Member flow)
- Single CTA: "Go to My Groups →" button
- Placeholder text: "Expenses, balances, and settlements will appear here in a future phase."

### What Is Placeholder
- Everything except the welcome banner and User ID panel
- No API calls are currently made
- No data is fetched or displayed

### APIs Currently Called
**NONE** — The DashboardPage makes zero API calls currently.

---

## 2. Existing Backend APIs Available for Dashboard

### ✅ Available APIs (No Backend Changes Needed)

#### User & Auth APIs
| Endpoint | Method | Service | Purpose |
|----------|--------|---------|---------|
| `/api/auth/me` | GET | Auth | Current user details |

#### Group APIs
| Endpoint | Method | Service | Purpose |
|----------|--------|---------|---------|
| `/api/groups` | GET | Group | List all user's groups |
| `/api/groups/{groupId}` | GET | Group | Single group details |
| `/api/groups/{groupId}/members` | GET | Group | Group member list |

#### Expense APIs
| Endpoint | Method | Service | Purpose |
|----------|--------|---------|---------|
| `/api/groups/{groupId}/expenses` | GET | Expense | All expenses in a group |

#### Balance APIs
| Endpoint | Method | Service | Purpose |
|----------|--------|---------|---------|
| `/api/groups/{groupId}/balances` | GET | Balance | Member balances in a group |
| `/api/groups/{groupId}/settlements` | GET | Settlement | Settlement suggestions for a group |

#### Settlement APIs
| Endpoint | Method | Service | Purpose |
|----------|--------|---------|---------|
| `/api/groups/{groupId}/settlements/history` | GET | Settlement | Settlement history for a group |

### ❌ Missing APIs (Would Need Backend Development)

**None of these exist:**
- `GET /api/users/me/balances` — Cross-group balance summary
- `GET /api/users/me/expenses` — All user's expenses across all groups
- `GET /api/users/me/activity` — Combined expenses + settlements feed
- `GET /api/users/me/settlements` — All user's settlements across all groups
- `GET /api/users/me/dashboard` — Aggregated dashboard summary

---

## 3. Determining Data Availability

### ✅ Current User's Balances (Per-Group Only)
- **Available:** YES, via `GET /api/groups/{groupId}/balances`
- **Limitation:** Must call separately for EACH group
- **Format:** Returns `GroupBalanceResponse` with array of `MemberBalance`
  ```typescript
  interface MemberBalance {
    userId: string;
    netBalance: number;
    status: 'OWES' | 'GETS' | 'SETTLED';
  }
  ```
- **Current user identification:** Filter array for `userId === currentUser.id`

### ✅ Groups
- **Available:** YES, via `GET /api/groups`
- **Returns:** Array of `GroupResponse[]`
- **Data includes:**
  - `id`, `name`, `description`, `createdBy`, `createdAt`, `updatedAt`
- **Member count:** **NOT included** in `/api/groups` response
  - Would require calling `/api/groups/{groupId}/members` for each group

### ✅ Recent Expenses (Per-Group Only)
- **Available:** YES, via `GET /api/groups/{groupId}/expenses`
- **Limitation:** Must call separately for EACH group
- **Sorting:** Backend returns "newest first" (sorted by `createdAt DESC`)
- **Data includes:**
  - `id`, `description`, `totalAmount`, `paidBy`, `createdBy`, `splitType`, `splits[]`, `createdAt`, `updatedAt`

### ✅ Recent Settlements (Per-Group Only)
- **Available:** YES, via `GET /api/groups/{groupId}/settlements/history`
- **Limitation:** Must call separately for EACH group
- **Sorting:** Backend returns ordered by `createdAt DESC`
- **Data includes:**
  - `id`, `fromUserId`, `fromUserName`, `toUserId`, `toUserName`, `amount`, `notes`, `status`, `settledAt`

### ❌ Recent Activity (Combined Expenses + Settlements)
- **Available:** NO
- **Why not:** No single endpoint provides a unified activity feed
- **Workaround:** Client-side merge of expenses and settlements
  - Fetch expenses from all groups
  - Fetch settlements from all groups
  - Merge arrays
  - Sort by timestamp (`expense.createdAt` vs `settlement.settledAt`)
  - Take top N most recent items
- **Problem with workaround:**
  - **Expensive:** Requires 2N API calls (N groups × 2 endpoints)
  - **Over-fetching:** Must fetch ALL expenses + ALL settlements to find 10 most recent
  - **No pagination:** Cannot efficiently paginate a merged feed
  - **Not scalable:** With 50 groups, this is 100 API calls on every dashboard load

---

## 4. Existing Frontend Services

### authService.ts
```typescript
register(data: RegisterRequest): Promise<AuthResponse>
login(data: LoginRequest): Promise<AuthResponse>
getCurrentUser(): Promise<User>
```
**Dashboard usage:** Already in use via `useAuth()` hook

### groupService.ts
```typescript
createGroup(data: CreateGroupRequest): Promise<GroupResponse>
listMyGroups(): Promise<GroupResponse[]>  ← NEEDED for Dashboard
getGroupDetail(groupId: string): Promise<GroupResponse>
listMembers(groupId: string): Promise<MemberResponse[]>  ← NEEDED for member counts
addMember(groupId: string, data: AddMemberRequest): Promise<MemberResponse>
```

### expenseService.ts
```typescript
createExpense(data: CreateExpenseRequest): Promise<ExpenseResponse>
getGroupExpenses(groupId: string): Promise<ExpenseResponse[]>  ← NEEDED for recent expenses
getExpense(expenseId: string): Promise<ExpenseResponse>
updateExpense(expenseId: string, data: CreateExpenseRequest): Promise<ExpenseResponse>
deleteExpense(expenseId: string): Promise<void>
```

### balanceService.ts
```typescript
getGroupBalances(groupId: string): Promise<GroupBalanceResponse>  ← NEEDED for "You owe"
getSettlementSuggestions(groupId: string): Promise<SettlementSuggestion[]>
```

### settlementService.ts
```typescript
recordSettlement(groupId: string, request: RecordSettlementRequest): Promise<void>
getSettlementHistory(groupId: string): Promise<SettlementHistoryItem[]>  ← NEEDED for activity
```

**All services already exist.** No new service files needed.

---

## 5. Reusable Components

### From Groups
- ✅ **GroupsList.tsx** — Grid display of groups
  - Shows: name, description, owner badge, created date
  - Already has link to `/groups/{id}`
  - **Reusable as-is** for Dashboard "My Groups" section

### From Expenses
- ✅ **ExpenseCard.tsx** — Single expense display
  - Shows: description, amount, paid by, split type, date, participant count
  - Has Edit/Delete actions (only for creator)
  - **Reusable** but might want simplified version without actions
- ⚠️ **ExpenseList.tsx** — Full list with filtering/sorting
  - Might be overkill for dashboard (which only needs recent 5-10)

### From Balances
- ✅ **BalanceSummary.tsx** — Current user's balance in a group
  - Shows: netBalance, status (OWES/GETS/SETTLED)
  - **Reusable** but currently expects single-group context
  - Would need adaptation for cross-group aggregation

### From Settlements
- ✅ **SettlementHistory.tsx** — Settlement list display
  - Shows: from → to, amount, date, status, notes
  - Smart date formatting (Today/Yesterday/X days ago)
  - **Reusable** for recent settlements section

### From Common
- ✅ **LoadingState** — Spinner with label
- ✅ **EmptyState** — Empty state messaging

**Assessment:** Most components are reusable but assume **single-group context**. Dashboard needs **cross-group aggregation**.

---

## 6. What Dashboard Can Display Using EXISTING APIs

### ✅ Implementable Immediately (No Backend Changes)

#### 1. My Groups Section
- **Data source:** `GET /api/groups`
- **Display:** Group name, description, created date, owner badge
- **Component:** Reuse `GroupsList.tsx`
- **Member count:** Would require `N` additional API calls (`GET /api/groups/{id}/members` for each group)
  - **Decision:** Skip member count for now OR fetch on-demand

#### 2. Recent Expenses (Limited Scope)
- **Approach A:** Recent expenses from a **single group** (most active or most recent)
  - Pick the group with most recent `updatedAt`
  - Call `GET /api/groups/{groupId}/expenses`
  - Show top 5 expenses
- **Approach B:** Recent expenses **across all groups** (expensive)
  - Call `GET /api/groups/{groupId}/expenses` for EACH group
  - Merge results, sort by `createdAt DESC`
  - Take top 10
  - **Cost:** N API calls where N = number of groups

#### 3. Balance Summary (Limited Scope)
- **Approach A:** Show balances for **one group at a time** with dropdown selector
- **Approach B:** Aggregate "You owe" / "Owed to you" **across all groups**
  - Call `GET /api/groups/{groupId}/balances` for EACH group
  - Filter for current user's balance in each
  - Sum all OWES (negative), sum all GETS (positive)
  - **Cost:** N API calls where N = number of groups

### ❌ NOT Implementable Without Backend Changes

#### Recent Activity Feed (Expenses + Settlements Combined)
- **Why not:** Would require:
  - Fetching expenses from N groups: N API calls
  - Fetching settlements from N groups: N API calls
  - Client-side merge and sort: 2N items to process
  - Total: **2N API calls** on every dashboard load
- **Problem:** Not scalable
  - 10 groups = 20 API calls
  - 50 groups = 100 API calls
- **Backend solution needed:** `GET /api/users/me/activity?limit=20`
  - Returns pre-merged, pre-sorted activity feed
  - Paginated
  - Single query, single response

#### User-Level Balance Totals
- **Why not:** Would require N balance API calls
- **Backend solution needed:** `GET /api/users/me/balances/summary`
  - Returns `{ totalOwed: number, totalGets: number, netBalance: number }`

---

## 7. Exact Data Needed for Each Dashboard Section

### Dashboard Summary (Balances)

**Goal:** Show at-a-glance financial status

**Data Requirements:**
- `youOwe`: Sum of all negative balances across all groups
- `youAreOwed`: Sum of all positive balances across all groups
- `netBalance`: `youAreOwed - youOwe`

**Current API Limitations:**
- Must call `GET /api/groups/{groupId}/balances` for **each group**
- Extract `balances.find(b => b.userId === currentUserId).netBalance`
- Aggregate client-side

**Data Shape (Target):**
```typescript
interface DashboardBalanceSummary {
  youOwe: number;        // Sum of negative balances (absolute value)
  youAreOwed: number;    // Sum of positive balances
  netBalance: number;    // youAreOwed - youOwe
  groupCount: number;    // How many groups have non-zero balance
}
```

### My Groups Section

**Goal:** Quick access to all groups

**Data Requirements:**
- Group `id` (for navigation)
- Group `name`
- Group `description` (optional)
- `memberCount` (optional — requires additional API call per group)
- `isOwner` boolean (`group.createdBy === currentUser.id`)
- `createdAt` or `updatedAt` for sorting

**Current API:**
- ✅ `GET /api/groups` provides: `id`, `name`, `description`, `createdBy`, `createdAt`, `updatedAt`
- ❌ `memberCount` NOT included — would need `GET /api/groups/{id}/members` for each

**Data Shape:**
```typescript
interface DashboardGroupCard {
  id: string;
  name: string;
  description: string | null;
  memberCount?: number;  // Optional if we skip it
  isOwner: boolean;
  updatedAt: string;
}
```

### Recent Expenses Section

**Goal:** See latest spending activity

**Data Requirements:**
- Expense `description`
- Expense `totalAmount`
- `paidByName` (need member names from group)
- `groupName` (need group details)
- Expense `createdAt`
- Split type badge
- Participant count

**Current API:**
- ✅ `GET /api/groups/{groupId}/expenses` provides expense data
- ❌ Group name NOT included in expense response — need to fetch separately or pass from parent
- ❌ Member names NOT included — need `GET /api/groups/{groupId}/members` for name resolution

**Workaround for member names:**
- Display only `paidBy` UUID or "You" if current user
- Skip full name resolution on dashboard

**Data Shape:**
```typescript
interface DashboardExpenseItem {
  id: string;
  groupId: string;
  groupName: string;     // Requires joining with groups data
  description: string;
  totalAmount: number;
  paidBy: string;        // UUID
  paidByName?: string;   // Optional full name resolution
  splitType: 'EQUAL' | 'EXACT' | 'PERCENTAGE';
  participantCount: number;
  createdAt: string;
}
```

### Recent Activity Section (NOT IMPLEMENTABLE)

**Goal:** Unified feed of expenses + settlements

**Data Requirements:**
- Activity type: 'EXPENSE' or 'SETTLEMENT'
- Description / summary text
- Amount
- Timestamp (for sorting)
- Related users
- Group context

**Current API Limitations:**
- No single endpoint provides this
- Would require:
  1. Fetch all expenses from all groups: `N` calls
  2. Fetch all settlements from all groups: `N` calls
  3. Client-side merge: `O(M)` where M = total items
  4. Sort: `O(M log M)`
  5. Take top 10-20
- **Not feasible** without dedicated backend endpoint

**Recommended Backend Solution:**
```
GET /api/users/me/activity?limit=20
Response: [
  {
    type: 'EXPENSE',
    id: 'uuid',
    groupId: 'uuid',
    groupName: 'Weekend Trip',
    description: 'Hotel booking',
    amount: 5000.00,
    timestamp: '2026-10-01T14:30:00Z'
  },
  {
    type: 'SETTLEMENT',
    id: 'uuid',
    groupId: 'uuid',
    groupName: 'Weekend Trip',
    description: 'John paid Sarah',
    amount: 2500.00,
    timestamp: '2026-10-01T10:15:00Z'
  }
]
```

---

## 8. Current Responsive Design & Dashboard CSS

### Existing Dashboard CSS

**Location:** `frontend/hishob-web/src/index.css`

**Existing Styles:**
```css
/* Dashboard placeholder */
.dashboard-page { padding, flex-column }
.dashboard-welcome { surface card style }
.dashboard-welcome__heading { 2xl, bold }
.dashboard-welcome__sub { muted text }
.dashboard-welcome__note { small, light }
.dashboard-cta-row { margin-bottom }

/* Dashboard User ID panel */
.dashboard-userid { panel with border }
.dashboard-userid__label { xs, bold, uppercase }
.dashboard-userid__row { flex, gap }
.dashboard-userid__value { monospace, small }
.dashboard-userid__copy { button }
.dashboard-userid__hint { xs, muted }
```

**Assessment:**
- Basic structure exists
- No CSS for balance cards, group cards, expense cards, activity feed
- Would need new styles for:
  - `.dashboard-section` (consistent section spacing)
  - `.dashboard-summary-cards` (balance display)
  - `.dashboard-stat-card` (you owe / owed to you)
  - Responsive grid for groups
  - List styles for recent expenses

---

## 9. Missing Backend Capability

### Critical Missing Endpoint: User Activity Feed

**Endpoint:** `GET /api/users/me/activity`

**Purpose:** Provide a unified, paginated, chronologically sorted feed of:
- Expenses the user created or is involved in
- Settlements the user made or received

**Why Needed:**
- Eliminates 2N API calls (N groups × 2 endpoints)
- Enables pagination (limit/offset or cursor-based)
- Server-side sorting and merging
- Efficient: Single database query with JOIN/UNION

**Response Schema:**
```typescript
interface ActivityItem {
  type: 'EXPENSE' | 'SETTLEMENT';
  id: string;
  groupId: string;
  groupName: string;
  description: string;
  amount: number;
  timestamp: string;  // ISO 8601
  relatedUsers?: string[];  // Participant UUIDs
}

interface ActivityResponse {
  items: ActivityItem[];
  pagination?: {
    hasMore: boolean;
    nextCursor?: string;
  };
}
```

**Backend Implementation Notes:**
- New controller in **Auth Service** or **new Dashboard/User Service**
- UNION query: expenses + settlements
- Filter by `currentUserId` (creator, paidBy, fromUserId, toUserId, or split participant)
- Order by timestamp DESC
- Limit to 20-50 items
- Consider caching for performance

### Optional Missing Endpoint: User Balance Summary

**Endpoint:** `GET /api/users/me/balances/summary`

**Purpose:** Aggregate balance totals across all groups

**Response:**
```typescript
interface UserBalanceSummary {
  totalOwed: number;       // Sum of all negative balances (abs value)
  totalGets: number;       // Sum of all positive balances
  netBalance: number;      // totalGets - totalOwed
  groupsWithBalance: number;  // Count of groups with non-zero balance
}
```

**Why Needed:**
- Eliminates N balance API calls
- Single query, single response

**Workaround (Client-Side):**
- Fetch balances from all groups
- Aggregate in browser
- **Feasible** but less efficient than backend aggregation

---

## 10. Files That Would Need Modification/Creation

### Files to CREATE

#### 1. `src/components/dashboard/BalanceSummaryCards.tsx`
**Purpose:** Display "You owe" / "Owed to you" / "Net balance" cards  
**Props:** `{ youOwe: number, youAreOwed: number, netBalance: number }`  
**Reusable:** Adapted from `BalanceSummary.tsx` but for cross-group context

#### 2. `src/components/dashboard/DashboardGroupsList.tsx`
**Purpose:** Simplified group list for dashboard (no edit/delete actions)  
**Props:** `{ groups: GroupResponse[], currentUserId: string }`  
**Alternative:** Reuse existing `GroupsList.tsx` directly

#### 3. `src/components/dashboard/RecentExpensesList.tsx`
**Purpose:** Display 5-10 most recent expenses across all groups  
**Props:** `{ expenses: DashboardExpenseItem[], onViewExpense: (id) => void }`  
**Reusable:** Simplified version of `ExpenseCard.tsx`

#### 4. `src/services/dashboardService.ts` (Optional)
**Purpose:** Aggregation logic for cross-group data  
**Functions:**
- `getUserBalanceSummary(groups: GroupResponse[]): Promise<BalanceSummary>`
- `getRecentExpensesAcrossGroups(groups: GroupResponse[], limit: number): Promise<ExpenseResponse[]>`

**Note:** This would encapsulate the multi-API-call logic

### Files to MODIFY

#### 1. `src/pages/dashboard/DashboardPage.tsx`
**Changes:**
- Remove placeholder text
- Add data fetching via `useEffect`
- Call `listMyGroups()` from `groupService`
- Fetch balances for each group (or selected groups)
- Fetch recent expenses from groups
- Render new dashboard sections
- Add loading/error states

**Structure:**
```tsx
<div className="dashboard-page">
  <header>Welcome back, {user.name}!</header>
  
  {/* Balance Summary Section */}
  <section className="dashboard-section">
    <h3>Your Balance Summary</h3>
    <BalanceSummaryCards {...balanceSummary} />
  </section>

  {/* My Groups Section */}
  <section className="dashboard-section">
    <h3>My Groups</h3>
    <DashboardGroupsList groups={groups} />
  </section>

  {/* Recent Expenses Section */}
  <section className="dashboard-section">
    <h3>Recent Expenses</h3>
    <RecentExpensesList expenses={recentExpenses} />
  </section>

  {/* User ID panel (keep existing) */}
</div>
```

#### 2. `src/index.css`
**Changes:**
- Add `.dashboard-section` styles
- Add `.dashboard-summary-cards` grid
- Add `.dashboard-stat-card` styles
- Add responsive breakpoints for dashboard layout

---

## 11. Recommended Implementation Plan

### Phase 15A: Minimal Dashboard (Implementable Today)

**Scope:** Use ONLY existing APIs, accept performance tradeoffs

#### Features:
1. ✅ **My Groups Section**
   - Reuse `GroupsList` component
   - Data: `GET /api/groups`
   - Skip member counts (requires N extra calls)

2. ✅ **Recent Expenses (Single Group)**
   - Show "Recent Expenses from [Group Name]"
   - Pick most recently updated group
   - Data: `GET /api/groups/{groupId}/expenses`
   - Limit to 5-10 expenses

3. ✅ **Balance Summary (Aggregated)**
   - Fetch balances from ALL groups
   - Aggregate client-side
   - Show: "You owe", "You are owed", "Net balance"
   - **Cost:** N API calls
   - **Acceptable:** Most users have <10 groups

4. ✅ **Improved Layout**
   - Remove placeholder text
   - Add proper sections
   - Keep User ID panel

#### Implementation Steps:
1. Create `BalanceSummaryCards.tsx`
2. Create `RecentExpensesList.tsx` (simplified)
3. Modify `DashboardPage.tsx`:
   - Fetch groups via `listMyGroups()`
   - Fetch balances for each group (parallel `Promise.all`)
   - Aggregate balance summary
   - Pick most recent group, fetch its expenses
4. Add new CSS for dashboard sections

**Estimated Effort:** 4-6 hours  
**Backend Changes Required:** NONE  
**API Calls on Dashboard Load:**
- 1 call to `/api/groups`
- N calls to `/api/groups/{id}/balances` (where N = group count)
- 1 call to `/api/groups/{selectedGroupId}/expenses`
- **Total:** N + 2 calls

### Phase 15B: Full Dashboard (Requires Backend Changes)

**Scope:** Implement proper user-level aggregation

#### New Backend Requirements:
1. **New Endpoint:** `GET /api/users/me/activity?limit=20`
   - Returns unified expense + settlement feed
   - Pre-sorted, paginated
2. **New Endpoint:** `GET /api/users/me/balances/summary`
   - Returns aggregated balance totals
3. **Enhancement:** Include `memberCount` in `GET /api/groups` response

#### Features:
1. ✅ **Recent Activity Feed**
   - Unified expenses + settlements
   - Chronologically sorted
   - Data: `GET /api/users/me/activity`
   - Paginated

2. ✅ **Balance Summary (Optimized)**
   - Data: `GET /api/users/me/balances/summary`
   - Single API call

3. ✅ **My Groups (Enhanced)**
   - Show member counts
   - Data: Enhanced `GET /api/groups` response

**Estimated Effort:**
- Backend: 8-12 hours (new endpoints, testing)
- Frontend: 4-6 hours (new components, integration)

**API Calls on Dashboard Load (Phase 15B):**
- 1 call to `/api/groups` (enhanced)
- 1 call to `/api/users/me/balances/summary`
- 1 call to `/api/users/me/activity`
- **Total:** 3 calls (constant, regardless of group count)

---

## 12. Decision Matrix

| Feature | Implementable Now? | Backend Changes? | Performance | Recommendation |
|---------|-------------------|------------------|-------------|----------------|
| My Groups List | ✅ Yes | None | O(1) call | **Implement in Phase 15A** |
| Member Counts | ⚠️ Workaround | None, but expensive | O(N) calls | **Defer to Phase 15B** |
| Balance Summary | ⚠️ Workaround | None, but expensive | O(N) calls | **Implement in 15A if N<20** |
| Recent Expenses (Single Group) | ✅ Yes | None | O(1) call | **Implement in Phase 15A** |
| Recent Expenses (All Groups) | ⚠️ Workaround | None, but expensive | O(N) calls | **Defer to Phase 15B** |
| Recent Activity Feed | ❌ No | **Required** | O(1) call | **Phase 15B only** |
| User Balance Total | ⚠️ Workaround | Recommended | O(N) vs O(1) | **Workaround for 15A, optimize in 15B** |

---

## 13. Final Recommendations

### Recommendation A: Incremental Approach (RECOMMENDED)

**Phase 15A (No Backend Changes):**
1. Implement "My Groups" section (reuse `GroupsList`)
2. Implement "Balance Summary" with client-side aggregation (acceptable for <20 groups)
3. Implement "Recent Expenses" from most active group
4. Improve dashboard layout and styling
5. Keep User ID panel

**Phase 15B (With Backend Changes):**
1. Backend: Implement `GET /api/users/me/activity`
2. Backend: Implement `GET /api/users/me/balances/summary`
3. Backend: Enhance `GET /api/groups` to include `memberCount`
4. Frontend: Replace client-side aggregation with optimized endpoints
5. Frontend: Add "Recent Activity" unified feed
6. Frontend: Add member counts to group cards

### Recommendation B: Wait for Backend (ONLY if necessary)

- Skip Phase 15A entirely
- Implement full backend support first
- Then build complete dashboard in one phase

**Verdict:** **Recommendation A (Incremental)** is better because:
- Delivers user value sooner
- Validates dashboard UX before heavy backend investment
- Client-side aggregation is acceptable for typical user (5-15 groups)
- Backend optimization can come later if performance becomes an issue

---

## 14. Summary Table: What Can Be Built Today

| Dashboard Section | Status | Data Source | API Calls | Notes |
|-------------------|--------|-------------|-----------|-------|
| Welcome Banner | ✅ Exists | `useAuth()` | 0 | Already implemented |
| User ID Panel | ✅ Exists | `useAuth()` | 0 | Already implemented |
| My Groups | ✅ Ready | `GET /api/groups` | 1 | Reuse GroupsList component |
| Balance Summary | ⚠️ Expensive | `GET /api/groups/{id}/balances` × N | N | Client-side aggregation |
| Recent Expenses | ⚠️ Limited | `GET /api/groups/{id}/expenses` | 1 | Single group only |
| Recent Activity | ❌ Blocked | N/A | 2N | **Needs backend endpoint** |

---

## Conclusion

**Phase 15A Dashboard can be implemented immediately** using existing APIs with the following limitations:

✅ **Can Implement:**
- My Groups list (full feature)
- Balance summary cards (with O(N) API calls)
- Recent expenses from one group

❌ **Cannot Implement (without backend):**
- Unified recent activity feed (expenses + settlements)
- Efficient cross-group balance aggregation

**Next Steps:**
1. Decide: Incremental (Phase 15A now, 15B later) or Wait for full backend?
2. If incremental: Proceed with Phase 15A implementation
3. If waiting: Design and implement backend user-level endpoints first

**Inspection Complete. No code has been modified.**
