# HISHOB Phase 14 — Settlement Backend Inspection Report

## 1. Public Settlement REST Endpoints

### Endpoint 1: Get Settlement Suggestions

**HTTP Method:** GET  
**URL:** `/api/groups/{groupId}/settlements`  
**Authentication:** JWT required (Bearer token)  
**Authorization:** User must be a member of the group

**Request:**
- Path parameter: `groupId` (UUID)
- No request body

**Response Body:**
```json
[
  {
    "fromUserId": "uuid",  // Debtor (who should pay)
    "toUserId": "uuid",    // Creditor (who should receive)
    "amount": 123.45       // BigDecimal
  }
]
```

**HTTP Status Codes:**
- 200 OK: Successfully returned suggestions
- 401 Unauthorized: No JWT or invalid JWT
- 403 Forbidden: User not a member of the group
- 404 Not Found: Group does not exist
- 503 Service Unavailable: Balance Service unavailable

**Behavior:**
- Stateless, read-only operation
- Fetches raw balances from Balance Service internal API
- Applies debt simplification algorithm
- Returns minimum payment suggestions to settle all debts
- Does NOT create any settlement records

---

### Endpoint 2: Record Settlement

**HTTP Method:** POST  
**URL:** `/api/groups/{groupId}/settlements`  
**Authentication:** JWT required (Bearer token)  
**Authorization:** 
- User must be a member of the group
- Authenticated user becomes `fromUserId` (payer)
- Recipient (`toUserId`) must also be a member

**Request Body:**
```json
{
  "toUserId": "uuid",     // Required, recipient UUID
  "amount": 123.45        // Required, BigDecimal, > 0.01, max 2 decimals
}
```

**Response Body:**
```json
{
  "id": "uuid",
  "fromUserId": "uuid",   // Always from JWT (authenticated user)
  "toUserId": "uuid",
  "amount": 123.45,
  "status": "COMPLETED",  // Always COMPLETED when created
  "createdAt": "2026-10-04T12:34:56.789Z",
  "updatedAt": "2026-10-04T12:34:56.789Z"
}
```

**HTTP Status Codes:**
- 201 Created: Settlement recorded successfully
- 400 Bad Request: Validation errors (missing fields, invalid amount, payer = recipient)
- 401 Unauthorized: No JWT or invalid JWT
- 403 Forbidden: User not a member of the group, or recipient not a member
- 404 Not Found: Group does not exist

**Validation Rules:**
- `toUserId` is required
- `amount` must be > 0.01
- `amount` must have at most 2 decimal places
- `fromUserId` cannot equal `toUserId` (error: "Payer and recipient cannot be the same user")
- Both payer and recipient must be group members

**Important:**
- `fromUserId` is **ALWAYS** taken from the validated JWT
- `fromUserId` is **NEVER** sent in the request body
- Frontend should NOT allow user to specify who they are paying "from"
- User can only record settlements where they are the payer

---

### Endpoint 3: Get Settlement History

**HTTP Method:** GET  
**URL:** `/api/groups/{groupId}/settlements/history`  
**Authentication:** JWT required (Bearer token)  
**Authorization:** User must be a member of the group

**Request:**
- Path parameter: `groupId` (UUID)
- No request body

**Response Body:**
```json
[
  {
    "id": "uuid",
    "fromUserId": "uuid",
    "toUserId": "uuid",
    "amount": 123.45,
    "status": "COMPLETED",  // Currently always COMPLETED
    "createdAt": "2026-10-04T12:34:56.789Z",
    "updatedAt": "2026-10-04T12:34:56.789Z"
  }
]
```

**Ordering:** Descending by `createdAt` (newest first)

**HTTP Status Codes:**
- 200 OK: Successfully returned history (may be empty array)
- 401 Unauthorized: No JWT or invalid JWT
- 403 Forbidden: User not a member of the group
- 404 Not Found: Group does not exist

---

## 2. Settlement Entity Model

**Table:** `settlements`  
**Database:** `hishob_settlement` (PostgreSQL)

### Fields

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | UUID | PRIMARY KEY, AUTO | Settlement unique identifier |
| `groupId` | UUID | NOT NULL | Group this settlement belongs to |
| `fromUserId` | UUID | NOT NULL | Payer (debtor) user ID |
| `toUserId` | UUID | NOT NULL | Recipient (creditor) user ID |
| `amount` | BigDecimal(19,2) | NOT NULL, > 0 | Payment amount |
| `status` | SettlementStatus | NOT NULL | PENDING or COMPLETED |
| `createdAt` | Instant | NOT NULL, IMMUTABLE | Creation timestamp |
| `updatedAt` | Instant | NOT NULL | Last update timestamp |

### Status Values

**Enum:** `SettlementStatus`

1. **PENDING** 
   - Reserved for future use
   - Currently NOT used
   - Intended for: payment confirmation flows, approval workflows

2. **COMPLETED**
   - Default and only status when settlements are created via POST
   - Affects balances immediately
   - Included in balance adjustment calculations
   - Returned by internal `/completed` endpoint

### Relationships

**None** - No JPA relationships defined. The entity stores UUIDs only:
- `groupId` references a group (not enforced at DB level)
- `fromUserId` references a user (not enforced at DB level)
- `toUserId` references a user (not enforced at DB level)

Group/user existence and membership are validated via internal API calls to Group Service, not FK constraints.

---

## 3. Settlement Lifecycle

### How a Settlement is Created

**User Action:**
```
POST /api/groups/{groupId}/settlements
Authorization: Bearer <JWT>
Body: { "toUserId": "recipient-uuid", "amount": 123.45 }
```

**Server Processing:**
1. Extract `fromUserId` from validated JWT (authenticated user)
2. Validate group exists (call Group Service internal API)
3. Validate `fromUserId` is a group member
4. Validate `toUserId` is a group member
5. Validate `fromUserId ≠ toUserId`
6. Validate `amount > 0.01` and `amount` has max 2 decimals
7. Create settlement record with `status = COMPLETED`
8. Save to database
9. Return settlement in response with HTTP 201

**Result:**
- Settlement is immediately COMPLETED
- No approval/confirmation step
- Affects balances immediately
- Visible in history immediately

### PENDING Behavior

**Current Implementation:** NOT USED

Settlements are always created with `status = COMPLETED`.

**Future Use Cases:**
- Payment gateway integration (PENDING until payment confirmed)
- Two-party confirmation (PENDING until recipient confirms)
- Admin approval (PENDING until admin approves)

### COMPLETED Behavior

**Current Implementation:** ONLY STATUS USED

When a settlement is COMPLETED:
1. ✅ Included in balance calculations
2. ✅ Returned by `/internal/groups/{groupId}/settlements/completed`
3. ✅ Affects creditor's balance: `balance -= amount` (credit reduced)
4. ✅ Affects debtor's balance: `balance += amount` (debt reduced)
5. ✅ Visible in settlement history
6. ✅ Permanent record

### Cancellation / Reversal

**Current Implementation:** NOT SUPPORTED

There is NO endpoint to:
- Cancel a settlement
- Delete a settlement
- Mark settlement as CANCELLED
- Reverse a settlement

**Workaround:**
- Record an opposite settlement (reverse transaction)
- Example: If Alice paid Bob $100 by mistake, Bob can pay Alice $100

**Future Enhancement:**
- Add CANCELLED status
- Add `DELETE /api/groups/{groupId}/settlements/{settlementId}`
- Add business rules (who can cancel, time limits, etc.)

### Editing / Updating

**Current Implementation:** NOT SUPPORTED

There is NO endpoint to:
- Update settlement amount
- Change fromUserId or toUserId
- Update status manually

Settlements are **immutable** after creation (except for internal `updatedAt` timestamp maintenance).

---

## 4. Settlement Authorization

### Who Can Create a Settlement

**Rule:** Any group member can create a settlement where **they are the payer**.

**Constraints:**
- User must be authenticated (JWT)
- User must be a member of the group
- User becomes `fromUserId` automatically (from JWT)
- User cannot specify a different `fromUserId`
- Recipient (`toUserId`) must be a group member

**Example:**
- Alice (member of group) creates settlement: Alice → Bob, $50
- ✅ Allowed (Alice is payer from JWT)

**Not Allowed:**
- Alice tries to create: Bob → Carol, $50
- ❌ Not allowed (Alice cannot make Bob the payer)

### Who Can View Settlement History

**Rule:** Any group member can view all settlements in the group.

**Constraints:**
- User must be authenticated (JWT)
- User must be a member of the group
- Returns ALL settlements in the group (not filtered by user)

**Returned Data:**
- All settlements where group members are involved
- Includes settlements where current user is payer
- Includes settlements where current user is recipient
- Includes settlements between other members

### Who Can Update/Complete It

**Current Implementation:** NOT APPLICABLE

Settlements are created as COMPLETED and are immutable.

No update endpoint exists.

### Group Membership Checks

**Enforced By:** Group Service internal API calls

**Checks Performed:**
1. **Group exists:** `GET /internal/groups/{groupId}/exists`
   - Returns 204 if exists
   - Returns 404 if not found

2. **User is member:** `GET /internal/groups/{groupId}/members/{userId}/exists`
   - Returns 204 if member
   - Returns 404 if not a member

3. **Recipient is member:** Same check for `toUserId`

**Sequence:**
```
Settlement Service
  ↓
  GET /internal/groups/{groupId}/exists (Group Service)
  ↓
  GET /internal/groups/{groupId}/members/{fromUserId}/exists (Group Service)
  ↓
  GET /internal/groups/{groupId}/members/{toUserId}/exists (Group Service)
  ↓
  (If all pass) → Save settlement
```

---

## 5. How Settlements Affect Balances

### Balance Calculation Flow

**Without Settlements:**
```
User Net Balance = Total Paid - Total Share
```

**With Settlements:**
```
Step 1: Calculate expense-derived balance
  netBalance = totalPaid - totalShare

Step 2: Fetch COMPLETED settlements from Settlement Service

Step 3: Apply each settlement adjustment:
  For settlement (fromUserId → toUserId, amount):
    fromUserId.balance += amount  (debt reduced)
    toUserId.balance -= amount    (credit reduced)

Final: Effective Net Balance (after settlement adjustments)
```

### Example

**Initial Balances (expense-derived):**
- Alice: -$100 (owes $100)
- Bob: +$100 (should receive $100)

**Settlement Recorded:**
- Alice → Bob: $100

**Adjusted Balances:**
- Alice: -$100 + $100 = $0 (SETTLED)
- Bob: +$100 - $100 = $0 (SETTLED)

### Internal API Used by Balance Service

**Endpoint:** `GET /internal/groups/{groupId}/settlements/completed`  
**Authentication:** `X-Internal-Token` header  
**Not routed through Gateway:** Direct service-to-service call

**Response:**
```json
[
  {
    "fromUserId": "uuid",
    "toUserId": "uuid",
    "amount": 123.45
  }
]
```

**Filters:**
- Only COMPLETED settlements
- PENDING settlements are EXCLUDED
- Ordered by creation time (though order doesn't affect balance calculation)

### Conservation Property

**Rule:** Sum of all effective balances = 0

**Verification:**
```
Σ(expense-derived balances) = 0  (enforced by Expense Service)
Σ(settlement adjustments) = 0     (every +amount has matching -amount)
Therefore: Σ(effective balances) = 0
```

Balance Service validates this after applying settlements.

---

## 6. Settlement History Endpoint

### Endpoint Details

**URL:** `GET /api/groups/{groupId}/settlements/history`

**Response Structure:**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "fromUserId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "toUserId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "amount": 250.00,
    "status": "COMPLETED",
    "createdAt": "2026-10-03T18:30:00.000Z",
    "updatedAt": "2026-10-03T18:30:00.000Z"
  },
  {
    "id": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
    "fromUserId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "toUserId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "amount": 150.00,
    "status": "COMPLETED",
    "createdAt": "2026-10-02T14:15:00.000Z",
    "updatedAt": "2026-10-02T14:15:00.000Z"
  }
]
```

**Ordering:** Newest first (descending `createdAt`)

**Empty Group:** Returns `[]` (empty array, not 404)

**Data Included:**
- All settlements in the group
- Not filtered by current user
- User IDs are UUIDs (frontend must map to names using member list)

---

## 7. Settlement Suggestion Endpoint

### Endpoint Details

**URL:** `GET /api/groups/{groupId}/settlements`

**Purpose:** Debt simplification suggestions (read-only, stateless)

**Response Structure:**
```json
[
  {
    "fromUserId": "debtor-uuid",
    "toUserId": "creditor-uuid",
    "amount": 123.45
  }
]
```

### Suggestion → Settlement Conversion

**Can a suggestion be directly converted to a settlement request?**

**Answer:** ✅ **YES, with validation**

**Conversion Logic:**

**IF** suggestion has:
```json
{
  "fromUserId": "current-user-uuid",  // Authenticated user
  "toUserId": "recipient-uuid",
  "amount": 123.45
}
```

**AND** `fromUserId === authenticated user UUID`

**THEN** frontend can enable "Settle Up" button and POST:
```json
POST /api/groups/{groupId}/settlements
Body: {
  "toUserId": "recipient-uuid",
  "amount": 123.45
}
```

**IF** `fromUserId !== authenticated user UUID`

**THEN** current user **CANNOT** create this settlement (they are not the debtor).

**Frontend Logic:**
```typescript
// For each suggestion
const canSettle = suggestion.fromUserId === currentUser.id;

if (canSettle) {
  // Show "Settle Up" button
  // On click: POST with { toUserId: suggestion.toUserId, amount: suggestion.amount }
} else {
  // Show "Waiting for payment" or grayed out
  // This user cannot create this settlement
}
```

### Important Constraints

1. **User can only settle their own debts**
   - Cannot create settlements where someone else is the payer
   - Suggestions for other debtors are informational only

2. **Amount can be different**
   - User can pay partial amount
   - User can pay full suggested amount
   - User can overpay (not recommended, but not prevented)
   - Backend validates amount > 0.01 only

3. **Recipient must match**
   - `toUserId` must be a group member
   - Backend validates recipient membership

4. **Multiple partial payments**
   - User can pay $50, then later pay another $50
   - Each creates a separate settlement record
   - Balances update cumulatively

---

## 8. Internal Settlement Endpoints

### For Balance Service

**Endpoint:** `GET /internal/groups/{groupId}/settlements/completed`  
**Controller:** `SettlementInternalController`  
**Authentication:** `X-Internal-Token` header  
**Access:** NOT routed through API Gateway

**Purpose:**
- Balance Service fetches completed settlements
- Applies settlement adjustments to expense-derived balances
- Returns effective balances to frontend

**Response:**
```json
[
  {
    "fromUserId": "uuid",
    "toUserId": "uuid",
    "amount": 123.45
  }
]
```

**Filters:**
- Repository: `findByGroupIdAndStatus(groupId, SettlementStatus.COMPLETED)`
- PENDING settlements are EXCLUDED
- Only COMPLETED settlements affect balances

**Error Handling:**
- If Settlement Service is unavailable: Balance Service returns HTTP 503
- Balance Service NEVER silently assumes zero settlements

### For Settlement Service (Self-Call)

**Endpoint:** `GET /internal/groups/{groupId}/balances`  
**Called By:** Settlement Service → Balance Service  
**Purpose:** Get raw balances for debt simplification algorithm

**Response:**
```json
[
  {
    "userId": "uuid",
    "netBalance": 123.45
  }
]
```

**Important:**
- This endpoint returns **raw expense-derived balances**
- Does NOT include settlement adjustments
- Prevents circular dependency (Balance → Settlement → Balance)

**Circular Dependency Prevention:**
```
Public Balance API:
  Balance Service → Expense Service (expenses)
                  → Settlement Service (completed settlements)
                  → Return adjusted balances

Settlement Suggestion API:
  Settlement Service → Balance Service /internal/balances (raw balances only)
                      → Return suggestions

No cycle because Balance Service /internal endpoint does NOT call Settlement Service.
```

---

## 9. Existing Frontend

### Current State: NO SETTLEMENT FILES EXIST

**Directories Checked:**
- `src/types/` — No settlement types file
- `src/services/` — No settlementService.ts
- `src/components/` — No settlements directory
- `src/pages/` — No settlement pages

### Reusable from Existing Frontend

**Types:**
- ✅ `types/balances.ts` has `SettlementSuggestion`
  ```typescript
  export interface SettlementSuggestion {
    fromUserId: string;
    toUserId: string;
    amount: number;
  }
  ```

**Components:**
- ✅ `components/balances/SettlementSuggestions.tsx` 
  - Displays settlement suggestions
  - Shows "who pays whom" with arrows
  - Currently read-only (no "Settle Up" button)

**Services:**
- ✅ `services/balanceService.ts` has `getSettlementSuggestions(groupId)`
  - Fetches `GET /api/groups/{groupId}/settlements`
  - Returns `SettlementSuggestion[]`

**Pages:**
- ✅ `pages/groups/GroupBalancesPage.tsx`
  - Already loads settlement suggestions
  - Displays via `SettlementSuggestions` component
  - Good integration point for "Settle Up" action

**Utilities:**
- ✅ `utils/errorUtils.ts` — Error message extraction
- ✅ `services/api.ts` — Axios instance with JWT interceptor

**Common Components:**
- ✅ `components/common/LoadingState.tsx`
- ✅ `components/common/EmptyState.tsx`
- ✅ `components/common/FormField.tsx`

---

## 10. Phase 14 Frontend Requirements

### What Needs to Be Built

Based on backend capabilities, Phase 14 should implement:

#### 1. Settlement Types ✅ NEEDED

**File:** `src/types/settlements.ts`

```typescript
// SettlementSuggestion already exists in balances.ts - can reuse or move

export interface RecordSettlementRequest {
  toUserId: string;
  amount: number;
}

export interface SettlementHistoryItem {
  id: string;
  fromUserId: string;
  toUserId: string;
  amount: number;
  status: 'PENDING' | 'COMPLETED';  // Currently only COMPLETED
  createdAt: string;  // ISO 8601
  updatedAt: string;
}
```

#### 2. Settlement Service ✅ NEEDED

**File:** `src/services/settlementService.ts`

```typescript
export async function recordSettlement(
  groupId: string, 
  request: RecordSettlementRequest
): Promise<SettlementHistoryItem>

export async function getSettlementHistory(
  groupId: string
): Promise<SettlementHistoryItem[]>

// getSettlementSuggestions already exists in balanceService.ts
```

#### 3. Settle Up Button/Modal ✅ NEEDED

**Integration Point:** `SettlementSuggestions` component

**Features:**
- Add "Settle Up" button for suggestions where `fromUserId === currentUser.id`
- Disable/hide button for suggestions where current user is not the debtor
- Click → Open confirmation modal
- Modal shows: "Pay $X to [Name]?"
- Confirm → `POST /api/groups/{groupId}/settlements`
- Success → Refresh balances + show success message
- Error → Display error message

**Component:** `components/settlements/SettleUpButton.tsx` or modal in `SettlementSuggestions.tsx`

#### 4. Settlement History View ✅ NEEDED

**Option A:** Add tab/section to GroupBalancesPage  
**Option B:** Separate route `/groups/{id}/settlements/history`

**Features:**
- List all settlements in group (newest first)
- Show: Date, From User, To User, Amount, Status
- Map user IDs to names using member list
- Highlight current user's settlements
- Empty state: "No settlements yet"
- Visual distinction: payments made vs received

**Component:** `components/settlements/SettlementHistory.tsx`

#### 5. Settlement Confirmation Modal ✅ NEEDED

**Component:** `components/settlements/ConfirmSettlementModal.tsx`

**Props:**
```typescript
{
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  recipientName: string;
  amount: number;
  loading: boolean;
}
```

**Content:**
- "Confirm Payment"
- "Pay $[amount] to [recipientName]?"
- Warning: "This will be recorded as completed immediately"
- Buttons: Cancel, Confirm

#### 6. Status Indicators ✅ NEEDED (Optional)

**Component:** `components/settlements/SettlementStatusBadge.tsx`

**Purpose:** Show COMPLETED status in history

**Future:** Show PENDING when that status is used

---

## Summary of Backend Support

| Feature | Backend Support | Frontend Status |
|---------|----------------|-----------------|
| View settlement suggestions | ✅ Supported | ✅ Implemented |
| Record settlement (user as payer) | ✅ Supported | ❌ Not implemented |
| View settlement history | ✅ Supported | ❌ Not implemented |
| Partial payments | ✅ Supported | N/A |
| Custom amounts | ✅ Supported | N/A |
| Update settlement | ❌ Not supported | N/A |
| Cancel settlement | ❌ Not supported | N/A |
| Delete settlement | ❌ Not supported | N/A |
| PENDING status | ❌ Not used | N/A |
| Reverse settlement | ❌ Not supported | Workaround: record opposite |
| Settlement confirmation flow | ❌ Not supported | Direct COMPLETED |
| Payment gateway integration | ❌ Not supported | N/A |

---

## Key Constraints for Frontend

1. **User can only create settlements where they are the payer**
   - Frontend must check: `suggestion.fromUserId === currentUser.id`
   - Only show "Settle Up" for user's own debts

2. **No editing or deletion**
   - Settlements are immutable
   - No edit/delete buttons needed

3. **Always COMPLETED immediately**
   - No "pending confirmation" state
   - Settlement affects balances instantly
   - No approval workflow UI needed

4. **Amount validation**
   - Minimum: 0.01
   - Maximum decimal places: 2
   - Can differ from suggestion amount

5. **User ID mapping**
   - Backend returns UUIDs
   - Frontend must map to member names using group member list

6. **Refresh after settlement**
   - Must reload balances (settlement affects them)
   - Must reload settlement suggestions (debts may be different)
   - Must reload settlement history (new settlement appears)

---

## Recommended Phase 14 Scope

### Minimal Implementation (Must Have)

1. ✅ Settlement types and service
2. ✅ "Settle Up" button on suggestions (for current user's debts only)
3. ✅ Confirmation modal
4. ✅ Record settlement via POST
5. ✅ Refresh balances after settlement

### Full Implementation (Should Have)

6. ✅ Settlement history view
7. ✅ Status badges (COMPLETED)
8. ✅ User name mapping
9. ✅ Success/error messages
10. ✅ Loading states

### Future Enhancements (Could Have)

11. Edit settlement amount before confirming
12. Notes/description field (requires backend change)
13. Partial payment suggestions
14. Settlement analytics/reports
15. Export history

---

**Inspection Complete**  
**Date:** 2026-10-04  
**Backend Status:** Fully implemented and tested  
**Frontend Status:** Settlement suggestions display only, no settlement recording
