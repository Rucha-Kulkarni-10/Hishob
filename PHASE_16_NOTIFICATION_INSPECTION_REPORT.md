# Phase 16: Notifications - Inspection Report

**Date:** October 2, 2026  
**Status:** 🔍 **INSPECTION COMPLETE - NO CODE MODIFIED**

---

## Executive Summary

The **Notification Service does NOT exist** in the HISHOB project. While the service directory `services/notification-service/` exists, it is **completely empty** with no implementation.

### Key Findings

❌ **Notification Service Status: NOT IMPLEMENTED**
- Directory exists but contains zero files
- No backend code (controllers, services, repositories, entities)
- No database schema or configuration
- No API endpoints
- No Eureka registration
- No Gateway routing
- No Config Server configuration
- No inter-service communication

❌ **Frontend Notification Status: NOT IMPLEMENTED**
- No notification components
- No notification types
- No notification service
- No notification routes
- No notification icon/badge in header
- No notification page

✅ **What EXISTS:**
- Architecture documentation describing planned Notification Service
- Service directory placeholder
- References in README indicating "Phase 11: Notification Service implementation - Planned"

---

## 1. Notification Service Backend Status

### Directory Structure
```
services/notification-service/
└── (EMPTY)
```

**Files found:** 0  
**Implementation status:** 0%

### Expected Structure (Based on Other Services)
```
services/notification-service/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/hishob/notification/
│   │   │   ├── NotificationServiceApplication.java
│   │   │   ├── controller/
│   │   │   │   ├── NotificationController.java
│   │   │   │   └── NotificationInternalController.java (optional)
│   │   │   ├── service/
│   │   │   │   └── NotificationService.java
│   │   │   ├── repository/
│   │   │   │   └── NotificationRepository.java
│   │   │   ├── entity/
│   │   │   │   └── Notification.java
│   │   │   ├── dto/
│   │   │   │   ├── NotificationResponse.java
│   │   │   │   └── CreateNotificationRequest.java
│   │   │   ├── enums/
│   │   │   │   ├── NotificationType.java
│   │   │   │   └── NotificationStatus.java
│   │   │   ├── principal/
│   │   │   │   └── AuthenticatedUser.java
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   └── JwtValidationFilter.java
│   │   │   └── exception/
│   │   │       └── NotificationException.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-dev.yml
│   └── test/
│       └── java/com/hishob/notification/
│           ├── NotificationServiceApplicationTests.java
│           ├── controller/
│           └── service/
└── target/
```

**None of this exists.**

---

## 2. Existing API Endpoints

### Public APIs
**NONE EXIST**

### Internal APIs
**NONE EXIST**

### Expected Public Notification APIs (Based on Architecture Doc)

According to `ARCHITECTURE.md`, the planned endpoints would be:

#### GET /api/notifications
**Purpose:** Fetch notifications for the authenticated user  
**Authentication:** JWT required  
**Query Params:**
- `unreadOnly` (optional): boolean, filter to unread only
- `limit` (optional): int, pagination limit
- `offset` (optional): int, pagination offset

**Expected Response:**
```json
[
  {
    "id": "uuid",
    "userId": "uuid",
    "groupId": "uuid",
    "type": "EXPENSE_ADDED",
    "message": "John added a new expense: Dinner - ₹500.00",
    "isRead": false,
    "createdAt": "2026-10-02T14:30:00Z",
    "metadata": {
      "expenseId": "uuid",
      "groupName": "Weekend Trip"
    }
  }
]
```

#### POST /api/notifications/{id}/mark-read
**Purpose:** Mark a single notification as read  
**Authentication:** JWT required  
**Response:** 204 No Content

#### POST /api/notifications/mark-all-read
**Purpose:** Mark all user's notifications as read  
**Authentication:** JWT required  
**Response:** 204 No Content

#### GET /api/notifications/unread-count
**Purpose:** Get count of unread notifications  
**Authentication:** JWT required  
**Response:**
```json
{
  "count": 5
}
```

**NONE OF THESE EXIST.**

---

## 3. Notification Events

### Currently Triggered Events
**NONE**

No service currently communicates with or triggers notifications because:
- Notification Service doesn't exist
- No inter-service notification client exists
- No event publishing mechanism exists

### Expected Notification Events (Based on Domain Logic)

#### Group Events
- **Member added to group:** "You have been added to {groupName} by {ownerName}"
- **Group created:** (optional, maybe not needed)

#### Expense Events
- **Expense created:** "{userName} added expense: {description} - ₹{amount}"
- **Expense updated:** "{userName} updated expense: {description}"
- **Expense deleted:** "{userName} deleted expense: {description}"

#### Settlement Events
- **Settlement recorded:** "{fromUserName} paid {toUserName} ₹{amount} in {groupName}"

#### Balance Events
- **Balance reminder:** (optional) "You owe ₹{amount} in {groupName}"

**NONE CURRENTLY IMPLEMENTED.**

---

## 4. Database & Entity Status

### Database: `hishob_notification`
**Status:** Does NOT exist

### Expected Notification Entity

Based on architecture document and typical notification systems:

```java
@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;  // Recipient

    @Column(nullable = true)
    private UUID groupId;  // Group context (nullable for global notifications)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;  // EXPENSE_ADDED, SETTLEMENT_RECORDED, etc.

    @Column(nullable = false, length = 500)
    private String message;  // Human-readable notification text

    @Column(nullable = false)
    private Boolean isRead = false;  // Read/unread status

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = true)
    private Instant readAt;  // When marked as read

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;  // Extra context (expenseId, settlementId, etc.)
}
```

**Expected Enum: NotificationType**
```java
public enum NotificationType {
    EXPENSE_ADDED,
    EXPENSE_UPDATED,
    EXPENSE_DELETED,
    SETTLEMENT_RECORDED,
    MEMBER_ADDED,
    BALANCE_REMINDER
}
```

**NONE OF THIS EXISTS.**

---

## 5. Inter-Service Communication

### Do Other Services Call Notification Service?
**NO**

Checked all services:
- **Auth Service:** No notification client ❌
- **Group Service:** No notification client ❌
- **Expense Service:** No notification client ❌
- **Balance Service:** No notification client ❌
- **Settlement Service:** No notification client ❌

**Search results:** Zero references to "notification" in any `.java` files across all services.

### Expected Inter-Service Pattern

When implemented, services would call Notification Service via:

1. **Synchronous REST calls** (Spring RestClient)
   ```java
   POST /internal/notifications
   X-Internal-Token: {service-token}
   Body: {
     "userId": "uuid",
     "groupId": "uuid",
     "type": "EXPENSE_ADDED",
     "message": "John added...",
     "metadata": {...}
   }
   ```

2. **Asynchronous events** (if message broker added later)
   - Kafka / RabbitMQ events
   - Services publish, Notification Service consumes

**Current pattern: NONE**

---

## 6. Eureka / Config / Gateway Configuration

### Eureka Registration
**Status:** Not configured

`services/notification-service/` has no `application.yml`, so no Eureka registration.

**Expected configuration:**
```yaml
spring:
  application:
    name: notification-service

eureka:
  client:
    register-with-eureka: true
    fetch-registry: true
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

### Config Server
**Status:** Not configured

No `infrastructure/config-server/config/notification-service.yml` file exists.

**Expected files:**
- `config/notification-service.yml` (service-specific config)
- Database connection for `hishob_notification`
- JWT validation settings
- Internal service token

### API Gateway Routing
**Status:** Not configured

`infrastructure/config-server/config/api-gateway.yml` has no notification route.

**Expected route:**
```yaml
- id: notification-service
  uri: lb://notification-service
  predicates:
    - Path=/api/notifications/**
```

**Placement:** Should be added BEFORE the catch-all `/api/groups/**` route.

---

## 7. Database Configuration

### PostgreSQL Database: `hishob_notification`
**Status:** Does NOT exist

**Expected database structure:**

```sql
CREATE DATABASE hishob_notification;

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    group_id UUID,
    type VARCHAR(50) NOT NULL,
    message VARCHAR(500) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP,
    metadata JSONB,
    
    INDEX idx_notifications_user_id (user_id),
    INDEX idx_notifications_user_id_is_read (user_id, is_read),
    INDEX idx_notifications_created_at (created_at DESC)
);
```

**Indexes rationale:**
- `user_id`: Fetch all notifications for a user
- `user_id + is_read`: Fetch unread notifications efficiently
- `created_at DESC`: Newest-first ordering

---

## 8. Frontend Status

### Components
**Status:** NONE exist

Expected components:
- `components/notifications/NotificationList.tsx` ❌
- `components/notifications/NotificationItem.tsx` ❌
- `components/notifications/NotificationBell.tsx` ❌
- `components/notifications/UnreadBadge.tsx` ❌

### Types
**Status:** NONE exist

No `frontend/hishob-web/src/types/notifications.ts` file.

**Expected types:**
```typescript
export type NotificationType = 
  | 'EXPENSE_ADDED'
  | 'EXPENSE_UPDATED'
  | 'EXPENSE_DELETED'
  | 'SETTLEMENT_RECORDED'
  | 'MEMBER_ADDED'
  | 'BALANCE_REMINDER';

export interface Notification {
  id: string;
  userId: string;
  groupId: string | null;
  type: NotificationType;
  message: string;
  isRead: boolean;
  createdAt: string;
  readAt: string | null;
  metadata?: Record<string, unknown>;
}

export interface UnreadCountResponse {
  count: number;
}
```

### Services
**Status:** NONE exist

No `frontend/hishob-web/src/services/notificationService.ts` file.

**Expected service:**
```typescript
export async function getNotifications(unreadOnly?: boolean): Promise<Notification[]>
export async function markAsRead(notificationId: string): Promise<void>
export async function markAllAsRead(): Promise<void>
export async function getUnreadCount(): Promise<number>
```

### Pages
**Status:** NONE exist

No `pages/notifications/NotificationsPage.tsx` file.

### Routes
**Status:** NONE configured

No `/notifications` route in `App.tsx`.

**Expected route:**
```tsx
<Route path="/notifications" element={<NotificationsPage />} />
```

### Header / Navigation
**Status:** No notification icon

`components/layout/AppLayout.tsx` currently has:
- Brand logo
- Dashboard link
- Groups link
- User name
- Logout button

**Missing:**
- Notification bell icon 🔔
- Unread count badge
- Dropdown menu for recent notifications

---

## 9. What Can Be Implemented with Current Backend

### Answer: NOTHING

The current backend has **ZERO notification capability**.

To implement any notification functionality, the following must be built from scratch:

1. **Notification Service backend** (entire microservice)
2. **Database schema** (`hishob_notification`)
3. **API endpoints** (public + internal)
4. **Inter-service clients** (Expense/Settlement/Group services calling Notification Service)
5. **Gateway routing** (add notification route)
6. **Config Server files** (notification-service.yml)
7. **Frontend components** (all notification UI)
8. **Frontend service** (notificationService.ts)
9. **Frontend types** (notifications.ts)
10. **Frontend routes** (notifications page)

**There is NO shortcut.** Notification functionality requires a complete Phase 16 implementation.

---

## 10. What Backend Work Is Missing

### Complete Notification Service Implementation

#### A. Service Infrastructure (Foundational)

1. **Spring Boot Application**
   - `NotificationServiceApplication.java`
   - Port: **8086** (next available after Settlement:8085)
   - Eureka client registration
   - Config Server client

2. **Database Setup**
   - PostgreSQL database: `hishob_notification`
   - Spring Data JPA configuration
   - Hibernate DDL auto (validate in prod, create-drop in dev)
   - Connection pooling (HikariCP)

3. **Security Configuration**
   - JWT validation filter
   - Public endpoints: `/api/notifications/**` (JWT required)
   - Internal endpoints: `/internal/notifications/**` (X-Internal-Token required)
   - CORS configuration (handled at Gateway)

4. **Config Server Integration**
   - `config/notification-service.yml`
   - Database connection properties
   - JWT signing secret (shared with other services)
   - Internal service token (shared with other services)
   - Eureka configuration

5. **Gateway Routing**
   - Add notification route to `api-gateway.yml`
   - Place before catch-all `/api/groups/**`

#### B. Domain Layer

1. **Entity**
   - `Notification.java`
   - Fields: id, userId, groupId, type, message, isRead, createdAt, readAt, metadata
   - Indexes on userId, isRead, createdAt

2. **Enums**
   - `NotificationType.java`: EXPENSE_ADDED, EXPENSE_UPDATED, EXPENSE_DELETED, SETTLEMENT_RECORDED, MEMBER_ADDED, BALANCE_REMINDER
   - `NotificationStatus.java` (optional): READ, UNREAD

3. **DTOs**
   - `NotificationResponse.java` (maps from Notification entity)
   - `CreateNotificationRequest.java` (for internal API)
   - `UnreadCountResponse.java` (simple count wrapper)

#### C. Repository Layer

1. **NotificationRepository.java**
   ```java
   public interface NotificationRepository extends JpaRepository<Notification, UUID> {
       List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);
       List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(UUID userId);
       Long countByUserIdAndIsReadFalse(UUID userId);
       @Modifying
       @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
       int markAllAsReadForUser(@Param("userId") UUID userId);
   }
   ```

#### D. Service Layer

1. **NotificationService.java**
   ```java
   public class NotificationService {
       // Get notifications for user (with optional unreadOnly filter)
       List<NotificationResponse> getNotifications(UUID userId, boolean unreadOnly);
       
       // Mark single notification as read
       void markAsRead(UUID notificationId, UUID userId);  // userId for ownership check
       
       // Mark all user's notifications as read
       void markAllAsRead(UUID userId);
       
       // Get unread count
       Long getUnreadCount(UUID userId);
       
       // Internal: Create notification (called by other services)
       NotificationResponse createNotification(CreateNotificationRequest request);
   }
   ```

#### E. Controller Layer

1. **NotificationController.java** (Public API)
   ```java
   @RestController
   @RequestMapping("/api/notifications")
   public class NotificationController {
       @GetMapping
       ResponseEntity<List<NotificationResponse>> getNotifications(
           @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
           @AuthenticationPrincipal AuthenticatedUser currentUser
       );
       
       @PostMapping("/{id}/mark-read")
       ResponseEntity<Void> markAsRead(
           @PathVariable UUID id,
           @AuthenticationPrincipal AuthenticatedUser currentUser
       );
       
       @PostMapping("/mark-all-read")
       ResponseEntity<Void> markAllAsRead(
           @AuthenticationPrincipal AuthenticatedUser currentUser
       );
       
       @GetMapping("/unread-count")
       ResponseEntity<UnreadCountResponse> getUnreadCount(
           @AuthenticationPrincipal AuthenticatedUser currentUser
       );
   }
   ```

2. **NotificationInternalController.java** (Internal API)
   ```java
   @RestController
   @RequestMapping("/internal/notifications")
   public class NotificationInternalController {
       @PostMapping
       ResponseEntity<NotificationResponse> createNotification(
           @Valid @RequestBody CreateNotificationRequest request,
           @RequestHeader("X-Internal-Token") String token
       );
   }
   ```

#### F. Inter-Service Integration

**Expense Service Changes:**
1. Add `NotificationServiceClient.java`
2. Call `POST /internal/notifications` after:
   - Expense created
   - Expense updated
   - Expense deleted
3. Notify all group members (fetch from Group Service)

**Settlement Service Changes:**
1. Add `NotificationServiceClient.java`
2. Call `POST /internal/notifications` after:
   - Settlement recorded
3. Notify both parties (fromUserId, toUserId)

**Group Service Changes:**
1. Add `NotificationServiceClient.java`
2. Call `POST /internal/notifications` after:
   - Member added to group
3. Notify the newly added member

**Balance Service:**
- Optional: Balance reminders (future enhancement)
- Not needed for MVP

#### G. Testing

1. **Unit Tests**
   - `NotificationServiceTest.java`
   - Test service logic, filtering, marking as read

2. **Integration Tests**
   - `NotificationControllerIntegrationTest.java`
   - Test full request/response cycle with database
   - Test JWT authentication
   - Test authorization (user can only access own notifications)

3. **Internal Controller Tests**
   - `NotificationInternalControllerTest.java`
   - Test X-Internal-Token authentication
   - Test notification creation

---

## 11. Frontend Work Missing

### Complete Frontend Implementation

#### A. Type Definitions

**File:** `frontend/hishob-web/src/types/notifications.ts`

```typescript
export type NotificationType = 
  | 'EXPENSE_ADDED'
  | 'EXPENSE_UPDATED'
  | 'EXPENSE_DELETED'
  | 'SETTLEMENT_RECORDED'
  | 'MEMBER_ADDED'
  | 'BALANCE_REMINDER';

export interface Notification {
  id: string;
  userId: string;
  groupId: string | null;
  type: NotificationType;
  message: string;
  isRead: boolean;
  createdAt: string;
  readAt: string | null;
  metadata?: {
    expenseId?: string;
    settlementId?: string;
    groupName?: string;
    [key: string]: unknown;
  };
}

export interface UnreadCountResponse {
  count: number;
}
```

#### B. Service Layer

**File:** `frontend/hishob-web/src/services/notificationService.ts`

```typescript
import api from './api';
import type { Notification, UnreadCountResponse } from '@/types/notifications';

export async function getNotifications(unreadOnly = false): Promise<Notification[]> {
  const response = await api.get<Notification[]>('/api/notifications', {
    params: { unreadOnly }
  });
  return response.data;
}

export async function markAsRead(notificationId: string): Promise<void> {
  await api.post(`/api/notifications/${notificationId}/mark-read`);
}

export async function markAllAsRead(): Promise<void> {
  await api.post('/api/notifications/mark-all-read');
}

export async function getUnreadCount(): Promise<number> {
  const response = await api.get<UnreadCountResponse>('/api/notifications/unread-count');
  return response.data.count;
}
```

#### C. Components

1. **NotificationBell.tsx** (Header icon with badge)
   - Bell icon 🔔
   - Unread count badge (red circle)
   - Dropdown menu on click (recent 5 notifications)
   - "View All" link to `/notifications`

2. **NotificationList.tsx** (Full list page)
   - Display all notifications
   - Group by date (Today / Yesterday / Older)
   - Mark as read on click
   - "Mark all as read" button
   - Filter: All / Unread
   - Empty state: "No notifications yet"

3. **NotificationItem.tsx** (Single notification)
   - Icon based on type (expense/settlement/member)
   - Message text
   - Timestamp (relative: "5 min ago")
   - Unread indicator (blue dot)
   - Click to mark as read + navigate to related entity

#### D. Pages

**File:** `frontend/hishob-web/src/pages/notifications/NotificationsPage.tsx`

- Full-page notification list
- Filter tabs: All / Unread
- Mark all as read button
- Loading state
- Empty state
- Error handling

#### E. Layout Integration

**File:** `frontend/hishob-web/src/components/layout/AppLayout.tsx`

**Changes needed:**
- Add `NotificationBell` component to header
- Position between nav links and user section
- Poll unread count every 30 seconds (or use WebSocket if added later)

#### F. Routing

**File:** `frontend/hishob-web/src/App.tsx`

**Add route:**
```tsx
<Route path="/notifications" element={<NotificationsPage />} />
```

#### G. CSS Styles

**File:** `frontend/hishob-web/src/index.css`

**Add styles:**
- `.notification-bell` (icon button)
- `.notification-badge` (unread count)
- `.notification-dropdown` (menu)
- `.notification-list` (full page list)
- `.notification-item` (single item)
- `.notification-item--unread` (unread state)

---

## 12. Recommended Implementation Order

### Phase 16A: Backend Foundation (Week 1)

1. Create Notification Service structure
2. Setup database (`hishob_notification`)
3. Implement entity, repository, DTOs
4. Implement service layer
5. Implement public API controller
6. Implement internal API controller
7. Add Eureka registration
8. Add Config Server files
9. Add Gateway routing
10. Write unit tests
11. Write integration tests

**Deliverable:** Working Notification Service backend, testable via Postman

### Phase 16B: Inter-Service Integration (Week 1)

1. Add notification clients to Expense/Settlement/Group services
2. Trigger notifications on:
   - Expense created/updated/deleted
   - Settlement recorded
   - Member added
3. Test end-to-end notification creation

**Deliverable:** Backend services automatically create notifications

### Phase 16C: Frontend Implementation (Week 2)

1. Create types, service layer
2. Implement NotificationBell component
3. Add bell to AppLayout header
4. Implement NotificationsPage
5. Implement NotificationList, NotificationItem
6. Add routing
7. Add CSS styles
8. Manual testing with backend

**Deliverable:** Full notification UI, users can view and manage notifications

### Phase 16D: Polish & Testing (Week 2)

1. Real-time updates (polling every 30s)
2. Navigation to related entities (click expense notification → expense page)
3. Notification grouping/summarization
4. Performance optimization (pagination, caching)
5. E2E testing

**Deliverable:** Production-ready notification system

---

## 13. Optional Enhancements (Not MVP)

### Email Notifications
**Status:** Not mentioned in architecture  
**Recommendation:** Defer to Phase 17+

Would require:
- Email service integration (SendGrid, AWS SES, etc.)
- Email templates
- User email preferences (opt-in/opt-out)
- Notification delivery tracking

### WhatsApp Notifications
**Status:** Not mentioned in architecture  
**Recommendation:** Defer to Phase 17+

Would require:
- WhatsApp Business API integration
- Phone number verification
- User WhatsApp preferences
- Message templates approval

### Push Notifications
**Status:** Not mentioned in architecture  
**Recommendation:** Defer to Phase 17+

Would require:
- Service Worker (PWA)
- Push notification API integration (Firebase, OneSignal)
- Device token management
- Browser permission flow

### WebSocket / Real-Time Updates
**Status:** Not mentioned in architecture  
**Recommendation:** Consider for Phase 16D polish

Current approach: Poll every 30 seconds (acceptable for MVP)

WebSocket would provide:
- Instant notification delivery
- No polling overhead
- Better UX

Implementation:
- Spring WebSocket/STOMP
- React WebSocket client
- Fallback to polling if WebSocket fails

---

## 14. Risks & Considerations

### Performance

**Notification volume:**
- Active user in 10 groups
- Each group has 10 members
- 5 expenses per day per group
- = 500 notifications per day per user

**Mitigation:**
- Pagination (limit 50 per request)
- Delete old read notifications (e.g., after 30 days)
- Indexes on (userId, isRead, createdAt)

### Privacy

**Question:** Should user A know when user B adds an expense in a shared group?

**Decision:** Yes, that's the point of notifications. But:
- Only group members receive notifications (enforced at creation)
- Users cannot see other users' notifications (enforced at fetch)

### Notification Fatigue

**Problem:** Too many notifications = users ignore them

**Mitigation:**
- User preferences (future): Disable certain notification types
- Grouping: "3 new expenses in Weekend Trip" instead of 3 separate notifications
- Digest: Daily summary email (future) instead of per-event

### Scale

**Single database table for all notifications:**
- Simple to implement
- May need sharding later if millions of users

**Current approach:** Acceptable for MVP (< 10,000 users)

---

## 15. Files to Create/Modify

### Backend - New Files (Notification Service)

#### Service Structure
1. `services/notification-service/pom.xml`
2. `services/notification-service/src/main/java/com/hishob/notification/NotificationServiceApplication.java`

#### Domain
3. `services/notification-service/src/main/java/com/hishob/notification/entity/Notification.java`
4. `services/notification-service/src/main/java/com/hishob/notification/enums/NotificationType.java`

#### DTOs
5. `services/notification-service/src/main/java/com/hishob/notification/dto/NotificationResponse.java`
6. `services/notification-service/src/main/java/com/hishob/notification/dto/CreateNotificationRequest.java`
7. `services/notification-service/src/main/java/com/hishob/notification/dto/UnreadCountResponse.java`

#### Repository
8. `services/notification-service/src/main/java/com/hishob/notification/repository/NotificationRepository.java`

#### Service
9. `services/notification-service/src/main/java/com/hishob/notification/service/NotificationService.java`

#### Controllers
10. `services/notification-service/src/main/java/com/hishob/notification/controller/NotificationController.java`
11. `services/notification-service/src/main/java/com/hishob/notification/controller/NotificationInternalController.java`

#### Security
12. `services/notification-service/src/main/java/com/hishob/notification/config/SecurityConfig.java`
13. `services/notification-service/src/main/java/com/hishob/notification/config/JwtValidationFilter.java`
14. `services/notification-service/src/main/java/com/hishob/notification/config/InternalAuthFilter.java`
15. `services/notification-service/src/main/java/com/hishob/notification/principal/AuthenticatedUser.java`

#### Config
16. `services/notification-service/src/main/resources/application.yml`
17. `infrastructure/config-server/config/notification-service.yml`

#### Tests
18. `services/notification-service/src/test/java/com/hishob/notification/NotificationServiceApplicationTests.java`
19. `services/notification-service/src/test/java/com/hishob/notification/service/NotificationServiceTest.java`
20. `services/notification-service/src/test/java/com/hishob/notification/controller/NotificationControllerIntegrationTest.java`
21. `services/notification-service/src/test/java/com/hishob/notification/controller/NotificationInternalControllerTest.java`

**Total new backend files: 21**

### Backend - Modified Files (Inter-Service Clients)

#### Expense Service
22. `services/expense-service/src/main/java/com/hishob/expense/client/NotificationServiceClient.java` (new)
23. `services/expense-service/src/main/java/com/hishob/expense/service/ExpenseService.java` (modify)

#### Settlement Service
24. `services/settlement-service/src/main/java/com/hishob/settlement/client/NotificationServiceClient.java` (new)
25. `services/settlement-service/src/main/java/com/hishob/settlement/service/SettlementService.java` (modify)

#### Group Service
26. `services/group-service/src/main/java/com/hishob/group/client/NotificationServiceClient.java` (new)
27. `services/group-service/src/main/java/com/hishob/group/service/MemberService.java` (modify)

#### Gateway
28. `infrastructure/config-server/config/api-gateway.yml` (modify - add notification route)

**Total modified/new integration files: 7**

### Frontend - New Files

#### Types
29. `frontend/hishob-web/src/types/notifications.ts`

#### Services
30. `frontend/hishob-web/src/services/notificationService.ts`

#### Components
31. `frontend/hishob-web/src/components/notifications/NotificationBell.tsx`
32. `frontend/hishob-web/src/components/notifications/NotificationDropdown.tsx`
33. `frontend/hishob-web/src/components/notifications/NotificationList.tsx`
34. `frontend/hishob-web/src/components/notifications/NotificationItem.tsx`
35. `frontend/hishob-web/src/components/notifications/UnreadBadge.tsx`

#### Pages
36. `frontend/hishob-web/src/pages/notifications/NotificationsPage.tsx`

**Total new frontend files: 8**

### Frontend - Modified Files

37. `frontend/hishob-web/src/components/layout/AppLayout.tsx` (add NotificationBell to header)
38. `frontend/hishob-web/src/App.tsx` (add notifications route)
39. `frontend/hishob-web/src/index.css` (add notification styles)

**Total modified frontend files: 3**

### Documentation

40. `PHASE_16_COMPLETION_REPORT.md` (create after implementation)

---

## 16. Total Implementation Effort

**Backend:**
- Notification Service: ~21 files, ~2000 LOC
- Inter-service integration: ~7 files, ~300 LOC
- **Total backend: 28 files, ~2300 LOC**

**Frontend:**
- New components/services: ~8 files, ~800 LOC
- Modified files: ~3 files, ~200 LOC delta
- **Total frontend: 11 files, ~1000 LOC**

**Total Phase 16: 39 files, ~3300 LOC**

**Estimated time:** 2-3 weeks for experienced developer

---

## 17. Dependencies & Prerequisites

### Must Be Completed First
✅ All current phases (Auth, Group, Expense, Balance, Settlement, Dashboard)  
✅ Database infrastructure (PostgreSQL)  
✅ API Gateway routing  
✅ Config Server setup  
✅ Eureka service discovery  

### No New Infrastructure Needed
- No message broker required (direct REST calls for MVP)
- No Redis required (database-backed notifications)
- No email service required (in-app only for MVP)
- No WebSocket required (polling acceptable for MVP)

---

## Conclusion

### Current Status: 0% Implemented

**Notification Service:** Does NOT exist  
**Frontend Notifications:** Does NOT exist  
**Inter-Service Integration:** Does NOT exist  

### To Implement MVP Notifications (Phase 16):

**Must Build:**
1. Complete Notification Service backend (microservice from scratch)
2. Database schema and repository
3. Public + Internal APIs
4. Inter-service notification clients (Expense/Settlement/Group)
5. Gateway routing configuration
6. Complete frontend notification UI
7. Header notification bell with unread badge
8. Notifications page

**Can Defer:**
- Email notifications
- WhatsApp notifications
- Push notifications
- WebSocket real-time updates (polling is acceptable)
- Advanced features (grouping, preferences, digests)

**Implementation Order:**
1. Phase 16A: Notification Service backend
2. Phase 16B: Inter-service integration
3. Phase 16C: Frontend UI
4. Phase 16D: Polish & testing

---

**Inspection Status: COMPLETE ✅**  
**Code Modified: NONE ✅**  
**Next Step: Approve Phase 16 implementation plan and begin Phase 16A**
