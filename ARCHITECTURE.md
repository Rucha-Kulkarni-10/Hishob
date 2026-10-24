# HISHOB — Architecture

This document describes the high-level architecture of the HISHOB platform.

---

## Overview

HISHOB is a Splitwise-style shared expense management platform. It allows users to create groups, record shared expenses, split costs using multiple strategies, calculate balances, simplify debts, and record settlements.

HISHOB is being implemented from scratch using a clean microservices architecture.

The application is built using a **microservices architecture** with Java Spring Boot on the backend and a React-based HISHOB Web frontend.

---

## Architecture Style

The system is decomposed into independently deployable services, each responsible for a specific business domain. Services communicate through well-defined REST APIs. Synchronous inter-service communication uses Spring `RestClient`.

We deliberately avoid unnecessary infrastructure complexity. Technologies such as message brokers (Kafka, RabbitMQ), caching layers (Redis), and container orchestration (Kubernetes) are not included at this stage. They will be introduced only when a clear need is demonstrated.

---

## Service Categories

### Infrastructure Services

These services support the operation of the platform but do not contain business logic.

#### Discovery Server (Eureka)

- **Purpose:** Service registration and discovery.
- Provides a service registry where all microservices register themselves on startup.
- Enables dynamic service discovery so that services and the API Gateway can locate each other by name rather than hardcoded URLs.
- **Address:** http://localhost:8761
- Deployed HISHOB microservices (auth-service, expense-service, settlement-service, api-gateway) register themselves with Eureka on startup in local development. Eureka and Config Server are local-only: deployed environments set `EUREKA_*` to false and route via public HTTPS service URLs.

#### Config Server (Spring Cloud Config)

- **Purpose:** Centralized configuration for HISHOB microservices.
- **Port:** 8888
- **Development backend:** Native filesystem — configuration stored under `infrastructure/config-server/config/`.
- Provides centralized externalized configuration for all services.
- Each service fetches its configuration from the Config Server on startup.
- Allows environment-specific configuration (dev, staging, production) without rebuilding services.
- Registers itself with Eureka so services can discover it by name.
- **Secrets policy:** Secrets, credentials, JWT signing keys, and API keys will **never** be committed to Git. In production the Config Server will integrate a secure secret source; in development they will be supplied via `.env` files or environment variables excluded from Git.

#### API Gateway (Spring Cloud Gateway)

- **Port:** 8087
- **Purpose:** Single HTTP entry point for the HISHOB website.
- **Routing:** Centralized reactive HTTP gateway routing incoming frontend client requests to downstream microservices based on request path and service discovery.
- **Routing Table:**
  - `/api/auth/**` → Auth Service (`auth-service`) **(active via Eureka lb://)**
  - `/api/expenses/**` → Expense Service (`expense-service`) **(active via Eureka lb://)**
  - `/api/groups/*/expenses/**` → Expense Service (`expense-service`) **(active via Eureka lb://)**
  - `/api/groups/*/balances` → Settlement Service (`settlement-service`, route id `balance-service`) **(active via Eureka lb://)**
  - `/api/groups/*/settlements/history` → Settlement Service (`settlement-service`) **(active via Eureka lb://)**
  - `/api/groups/*/settlements` → Settlement Service (`settlement-service`) **(active via Eureka lb://)**
  - `/api/groups/**` → Expense Service (`expense-service`, route id `expense-service-groups`) **(active via Eureka lb://)**
  - `/api/notifications/**` → Auth Service (`auth-service`, route id `notification-service`) **(active via Eureka lb://)**
  > **Note:** Routes marked as active are currently routed through Eureka service discovery in local development. Path-based routing order ensures more specific predicates (`/api/groups/*/settlements/history`, `/api/groups/*/settlements`, `/api/groups/*/expenses/**`, `/api/groups/*/balances`) are declared before the `/api/groups/**` catch-all. Group CRUD and membership reads/writes are owned by Expense Service; the legacy Group Service no longer receives gateway traffic. Balance routes use `SETTLEMENT_SERVICE_URL` (fallback `lb://settlement-service`) and notification routes use `AUTH_SERVICE_URL` (fallback `lb://auth-service`): every route resolves to exactly one of the three consolidated backend services (Auth, Expense, Settlement); the table above lists the core group-oriented routes, and the remaining `/api/personal-*`, `/api/friends` and `/api/users` routes follow the same owners. No `/internal/**` route exists in the Gateway — internal service endpoints are never exposed to public clients.
- **Future Cross-Cutting Capabilities:** The Gateway will eventually be extended to handle cross-cutting concerns such as centralized authentication / JWT validation, CORS configuration, rate limiting, and observability (metrics & distributed tracing). These concerns will be introduced in subsequent phases.

---

### Business Services

Each business service is responsible for a single domain and owns its data exclusively. No business service directly accesses another service's database.

#### Auth Service

- **Domain:** Users and authentication.
- **Port:** 8081
- **Responsibilities:** User registration, login, JWT token generation and validation, password hashing with BCrypt, current user identity retrieval.
- **Database:** `hishob_auth` (PostgreSQL) — owns the `users` table exclusively.
- **Critical Rule:** No other HISHOB microservice may directly access this database. Future services must communicate with Auth Service via APIs or events.
- **Managed explicitly by Auth Service:** user identity, credentials, authentication state.

#### Group Service (legacy — frozen for writes)

- **Status:** Legacy. Group and membership ownership moved to Expense Service. The Gateway no longer routes `/api/groups/**` here, `render.yaml` no longer supplies a `GROUP_SERVICE_URL` override or a `hishob-group-service` deployment, and `start-hishob.ps1` no longer launches it. The module remains in the Maven reactor for local reference only.
- **Domain:** Groups and membership (read-only).
- **Port:** 8082
- **Enforced write freeze:** `SecurityConfig` denies `POST /api/groups/**` with 403 for every caller, authenticated or not. `GET /api/groups/**` and `GET /internal/groups/**` remain available for read-back and migration verification only.
- **Responsibilities (historical):** Group creation, group ownership (OWNER), membership management (MEMBER role), adding members, listing a user's groups (only groups where they are a member), viewing group details and group members. Removing members, editing group metadata and deleting groups were never implemented.
- **Database:** `hishob_group` (PostgreSQL) — still the pre-separation store; `hishob_expense` now holds the authoritative copy of `groups` and `group_members`. This service must not be used for new writes.
- **Remaining `hishob_group` write paths (not yet retired):** the service's own `ddl-auto: update` startup schema sync, and `scripts/migrate-groups.ps1`. Both are outside the request path and are listed as open items.
- **Data ownership rules:** No JPA relationship to Auth Service `User` entity; member identity is stored only as a `UUID userId` reference. `(groupId, userId)` is enforced unique at the database level and validated at the service layer.
- **Identity mechanism:** Extracts authenticated `userId` and `email` claims from a signed JWT (HMAC-SHA-256) issued by Auth Service. Uses its own JWT validation filter only — no user database is duplicated. Plain `X-User-Id` headers are never trusted.
- **Permission model (read paths):** OWNER can add members; MEMBER cannot. Non-members are forbidden from viewing group details or members.
- **Dependencies:** Calls Auth Service REST API (`GET /api/auth/users/{userId}/exists`) via Spring `RestClient` to validate target-user existence when adding a member. No direct database access to `hishob_auth`; no OpenFeign; no message broker.

#### Expense Service

- **Domain:** Groups, memberships, shared expenses and splits. Expense Service is the single authoritative owner of group and membership data.
- **Port:** 8083
- **Responsibilities:** Group creation with OWNER membership for the creator, listing a user's groups, group detail and member listing, adding members with OWNER-only authorization. Recording shared expenses, splitting costs using EQUAL (deterministic remainder-cent distribution), EXACT (sum-match validation), or PERCENTAGE (100%-total validation) strategies. Tracking who paid (`paidBy`) and who created the expense (`createdBy`). Enforcing creator-only update/delete authorization. Any group member may create or read expenses. Group metadata edits, member removal and role changes are not implemented in any service.
- **Database:** `hishob_expense` (PostgreSQL) — owns the `groups`, `group_members`, `expenses` and `expense_splits` tables exclusively. No User table; member identity is a `UUID userId` reference resolved through Auth Service.
- **Group validation mechanism:** Group existence and membership checks are served in-process by `GroupService.validateGroupExists`, `validateMember` and `getGroupMemberIds` — there is no HTTP hop to a separate group service. `GET /internal/groups/**` exposes the same facts to Settlement Service (including its merged balance calculation) over `X-Internal-Token` authentication.
- **Monetary precision:** All monetary values use `BigDecimal(precision=19, scale=2)` — never `double` or `float`. Equal-split rounding distributes remainder cents to the first N participants deterministically so the sum always equals the exact expense total.
- **Identity mechanism:** Extracts authenticated `userId` and `email` claims from a signed JWT (HMAC-SHA-256) issued by Auth Service. `createdBy` is always taken from the validated JWT, never from the request body.
- **Dependencies:** Calls Auth Service REST API for user existence, user summaries, and notification dispatch (`POST /internal/notifications` guarded by `X-Internal-Token`), via Spring `RestClient`. No direct database access to `hishob_auth` or `hishob_group`; no OpenFeign; no message broker.
- **Intentionally deferred:** Balance calculation, debt simplification, and settlement logic are NOT implemented here. They are served by Settlement Service (see below).

#### Balance Service (merged into Settlement Service — legacy module)

- **Status:** All balance endpoints (`GET /api/groups/{groupId}/balances`, `/api/personal-balances`, `/api/personal-balances/simplify`, `/api/friends/{friendUserId}/balance` and their `/internal/**` counterparts) are served in-process by Settlement Service via `BalanceController`, `BalanceInternalController` and `BalanceCalculationService`. The standalone `balance-service` module remains in the Maven reactor for reference only: it is no longer deployed on Render and no longer started by `start-hishob.ps1`. The calculation rules below remain accurate.
- **Domain:** Balance calculation.
- **Port:** 8084 (legacy standalone module only — not deployed or started)
- **Responsibilities:** Computing effective net balances for each member in a group. Determining creditor (GETS) and debtor (OWES) status. Enforcing balance conservation (sum of all effective net balances = 0). Returning zero-balance entries for members with no expense activity.
- **Database:** None. Balance calculation is stateless — balances are derived on demand from Expense Service data and adjusted using the COMPLETED settlements owned by Settlement Service (read in-process via `SettlementRepository`).
- **Balance calculation (two steps):**
  1. **Expense-derived balance:** `netBalance = totalPaid - totalShare`. Positive → user should receive money (GETS). Negative → user owes money (OWES). Zero → settled (SETTLED).
  2. **Settlement adjustment:** For each COMPLETED settlement read in-process from the Settlement repository:
     - `fromUserId` (payer): `effectiveBalance = expenseBalance + amount` (debt reduced)
     - `toUserId` (receiver): `effectiveBalance = expenseBalance - amount` (credit reduced)
     PENDING settlements are never applied. Only COMPLETED settlements affect balances.
- **Over-settlement guard:** If a COMPLETED settlement amount exceeds a debtor's outstanding expense-derived debt, `BalanceCalculationException` is thrown. This prevents silent balance inversions (e.g., a debtor becoming a creditor due to inconsistent settlement data).
- **Monetary precision:** All calculations use `BigDecimal`. Status resolved via `compareTo(BigDecimal.ZERO)`, never `==`.
- **Data flow (public API):**
  ```
  Expense Service  ──(GET /internal/groups/{groupId}/members)────►  Settlement Svc (balance calc)
  Expense Service  ──(GET /internal/groups/{groupId}/expenses)──►  Settlement Svc (balance calc)
  Settlement repo  ──(in-process read, COMPLETED only)─────────►  Settlement Svc (balance calc)
                                                                          │
                                                                          ▼
                                                          Expense-derived balances
                                                          + Settlement adjustments
                                                          = Effective net balances
  ```
- **Data flow (internal API — legacy standalone topology, now served in-process):**
  ```
  Debt simplification uses raw expense-derived balances computed by
  `BalanceInternalController`/`BalanceCalculationService` inside Settlement
  Service — no settlement adjustment on this path (avoids the historical
  circular dependency).
  ```
- **Identity mechanism:** Extracts authenticated `userId` from a signed JWT. User may only request balances for groups they belong to.
- **Dependencies:** The balance subsystem (in Settlement Service) calls Expense Service internal APIs for group membership (`/internal/groups/**`), group validation and personal expenses via Spring `RestClient` with `X-Internal-Token`, and reads completed settlements in-process from its own repository (the former HTTP hop to `GET /internal/groups/{groupId}/settlements/completed` is gone). If required group or expense data is unavailable, requests fail with an error — never silently assume zero settlements.

#### Settlement Service (owns balances + settlements)

- **Domain:** Balances, settlements and debt simplification. This is the sole active deployment target for all balance routes (the standalone Balance Service is merged here — see above).
- **Port:** 8085
- **Responsibilities:** Computing net balances in-process via the merged `BalanceCalculationService` (formerly the standalone Balance Service), applying a deterministic debtor/creditor matching algorithm to generate simplified settlement suggestions, recording completed settlements, providing settlement history per group.
- **Database:** `hishob_settlement` (PostgreSQL) — owns the `settlements` table exclusively. Fields: `id` (UUID), `groupId` (UUID), `fromUserId` (UUID), `toUserId` (UUID), `amount` (BigDecimal, precision 19 scale 2), `status` (PENDING / COMPLETED), `createdAt`, `updatedAt`.
- **Debt simplification algorithm:** Separates members into creditors (netBalance > 0) and debtors (netBalance < 0), sorted deterministically by userId. Uses a two-pointer greedy match: for each debtor, transfer `min(debtorDebt, creditorCredit)` to the current creditor, reduce both balances, and advance the exhausted pointer. Continues until all balances are zero. Never emits zero-value or self-settlements. Uses `BigDecimal.compareTo()` throughout. Documents that this greedy approach is deterministic but not universally optimal for all possible debt graphs.
- **APIs:**
  - Balance endpoints (`GET /api/groups/{groupId}/balances`, `/api/personal-balances`, `/api/personal-balances/simplify`, `/api/friends/{friendUserId}/balance` and their `/internal/**` counterparts) are served in this service by `BalanceController` and `BalanceInternalController`.
  - `GET  /api/groups/{groupId}/settlements` — returns simplified settlement suggestions derived from current balances (stateless, read-only).
  - `POST /api/groups/{groupId}/settlements` — records a completed settlement; `fromUserId` is always taken from the validated JWT, never from the request body.
  - `GET  /api/groups/{groupId}/settlements/history` — returns all recorded settlements for the group, ordered by creation time descending.
  - `GET  /internal/groups/{groupId}/settlements/completed` — **internal only** (requires `X-Internal-Token`, not exposed through Gateway). Returns all COMPLETED settlements for a group. Retained for internal callers; the merged in-process balance calculation reads the Settlement repository directly. PENDING settlements are always excluded.
- **Settlement status:** `COMPLETED` when recorded via POST. `PENDING` reserved for future use (e.g., pending confirmation flows).
- **Monetary precision:** All amounts use `BigDecimal(precision=19, scale=2)`. Never `double` or `float`.
- **Identity mechanism:** Extracts authenticated `userId` from a signed JWT. User may only interact with groups they belong to.
- **Security rules:** JWT is mandatory for all public APIs. `X-User-Id` headers are never trusted. `fromUserId` is always sourced from the validated JWT. Internal endpoints (`/internal/**`) are protected by `X-Internal-Token` and are not accessible through the API Gateway.
- **Dependencies:**
  - Net balances for simplification are computed in-process by `BalanceCalculationService` (the former HTTP hop to the standalone Balance Service `GET /internal/groups/{groupId}/balances` is gone).
  - Calls Expense Service internal API (`GET /internal/groups/{groupId}/exists`, `GET /internal/groups/{groupId}/members/{userId}/exists`, `GET /internal/groups/{groupId}/members`) via Spring `RestClient` with `X-Internal-Token` to validate group existence, membership and recipient membership.
  - Dispatches settlement notifications to Auth Service (`POST /internal/notifications` with `X-Internal-Token`) via `NotificationServiceClient`; dispatch failures are swallowed so the settlement transaction still succeeds.
- **Conservation guarantee:** Sum of settlement amounts sent = sum received = sum of all positive net balances = absolute sum of all negative net balances.
- **Intentionally deferred:** Payment gateway integration, UPI, bank integration, actual money transfer, reports, analytics, Docker, deployment.

#### Notification Service (merged into Auth Service — legacy module)

- **Domain:** In-app notifications.
- **Status:** Notifications are owned and served by Auth Service (`/api/notifications/**` via JWT-authenticated reads, `/internal/notifications` for dispatch from Expense/Settlement over `X-Internal-Token`). The API Gateway routes `/api/notifications/**` to `auth-service`, and `start-hishob.ps1` no longer launches the standalone notification service. The `notification-service` module remains in the Maven reactor and its `hishob_notification` data is preserved for reference — it is **not** deleted and its records are **not** migrated in this phase.
- **Responsibilities (historical):** Storing and delivering notifications triggered by events across the system (expense added, settlement recorded, group invite, etc.). Provides endpoints to fetch unread notifications and mark them as read.
- **Database:** `hishob_notification_db` (legacy, preserved) — the active store is now the `notifications` table owned by Auth Service.

---

## Data Ownership

Each business service owns its database exclusively. No service reads from or writes to another service's database.

| Service | Database |
|---------|----------|
| Auth Service | `hishob_auth` — `users` and the active `notifications` table |
| Group Service (legacy, writes frozen) | `hishob_group` — historical store; `groups` and `group_members` were copied into `hishob_expense` on 2026-10-18 and are no longer written |
| Expense Service | `hishob_expense` — authoritative `groups`, `group_members`, `expenses`, `expense_splits` |
| Settlement Service | `hishob_settlement` — `settlements` (balance calculation is stateless) |
| Notification Service (legacy, merged into Auth) | `hishob_notification` — historical store; new notifications are written to `hishob_auth`; ~135 legacy rows are pending an approved migration |

---

## Inter-Service Communication

- **Synchronous:** Spring `RestClient` for request-response communication between services (e.g., Expense and Settlement services validating users and dispatching notifications through Auth Service internal endpoints; Settlement Service — including the merged balance calculation — validating group existence and membership via Expense Service internal endpoints). Balance↔Settlement data no longer crosses the network: both live inside Settlement Service.
- **Asynchronous:** Not introduced at this stage. If event-driven communication becomes necessary, a message broker will be evaluated.
- **Circular dependency avoidance (historical, preserved in-process):** The public balance path (`BalanceCalculationService`) applies settlement adjustments, while `BalanceInternalController` returns raw expense-derived balances without touching settlement data. Keeping these two paths separate originally prevented a Balance → Settlement → Balance HTTP call cycle across two services; both classes now live inside Settlement Service and the separation is still maintained.

---

## Authentication Model

### User-to-Service Authentication

All user-facing API endpoints are protected by JWT (HMAC-SHA-256) issued by Auth Service. Each service validates the JWT independently using the shared signing key. The authenticated user's `userId` and `email` are extracted from the validated token. No service trusts a plain `X-User-Id` header.

### Service-to-Service Authentication (Internal)

Settlement Service (including its merged balance calculation) calls Expense Service internal endpoints to validate group existence and membership; Expense and Settlement services call Auth Service internal endpoints (user lookups, notification dispatch). These calls are authenticated using a shared service token transmitted in the `X-Internal-Token` header.

- The token is configured via the `INTERNAL_SERVICE_TOKEN` environment variable on every calling and receiving service (Render `hishob-shared` group; local per-service `.env` files).
- The receiving service validates the token in its `InternalAuthFilter` before the request reaches any controller (Auth Service scopes the filter to `/internal/**`, so a valid internal token never authenticates public `/api/**` endpoints).
- Requests to `/internal/**` without a valid token are rejected with HTTP 401.
- The service token is **separate** from the JWT signing secret.
- Internal endpoints are **not routed through the API Gateway** — no `/internal/**` route exists in the Gateway configuration. A public client cannot reach these endpoints via `http://localhost:8087`.
- Development defaults are provided only for local development and must be overridden in any non-development environment via `INTERNAL_SERVICE_TOKEN`.

---

## Frontend

A **React-based HISHOB Web** single-page application (SPA) located in `frontend/hishob-web/` that communicates exclusively with the API Gateway. It is accessed through a standard web browser. The frontend never calls business services directly.

| Technology | Purpose |
|-----------|---------|
| React 19 | Component-based UI |
| Vite 8 | Build tool and dev server |
| TypeScript | Type-safe JavaScript |
| Axios | HTTP requests to the API Gateway |
| React Router 7 | Client-side routing |
| Vanilla CSS | Custom design system (CSS custom properties) |

### Frontend Architecture

```
Browser
  ↓
React Frontend (localhost:5173)
  ↓
API Gateway (localhost:8087)  ←— CORS: allowedOrigins: ["http://localhost:5173"]
  ↓
Consolidated backend services — Auth :8081, Expense :8083, Settlement :8085
```

### Authentication Architecture

```
React Frontend
  ↓
AuthContext (src/context/AuthContext.tsx)
  ↓
authService.ts  →  POST /api/auth/login  or  /api/auth/register
  ↓
API Gateway :8087  →  Auth Service :8081
  ↓
AuthResponse { accessToken, tokenType: "Bearer", user }
  ↓
tokenStorage.ts  →  localStorage["hishob_access_token"]

Subsequent requests:
Axios interceptor (api.ts)  →  Authorization: Bearer <JWT>
API Gateway :8087  →  microservices
```

### Frontend Security Rules

- The frontend never calls microservices directly (auth-service, group-service, etc.)
- All requests go through the API Gateway on port 8087
- `X-Internal-Token` is NEVER included in any frontend code
- JWT is stored in localStorage (`hishob_access_token`) — isolated in `tokenStorage.ts`
- Passwords are never stored in any browser storage
- JWT is never logged to the browser console
- Database credentials are never included in frontend code
- `VITE_*` env vars are bundled into the build — only the Gateway URL is acceptable
- `/internal/**` endpoints are never called by the frontend
- Unauthenticated users cannot access `/dashboard` via the route guard (`ProtectedRoute`)

### Frontend Folder Architecture

```
frontend/hishob-web/src/
├── components/
│   ├── common/       FormField (accessible labeled input with error display)
│   └── layout/       AppLayout (sticky nav, brand, logout)
├── context/          AuthContext (user state, login, register, logout, isLoading)
├── hooks/            useAuth (convenience hook; separated per react-refresh rule)
├── pages/
│   ├── auth/         LoginPage, RegisterPage
│   └── dashboard/    DashboardPage (placeholder)
├── routes/           ProtectedRoute, PublicRoute (route guards)
├── services/
│   ├── api.ts        Centralized Axios instance with JWT interceptor
│   └── authService.ts  register(), login(), getCurrentUser()
├── types/            auth.ts (User, AuthResponse, RegisterRequest, LoginRequest, ApiError)
└── utils/
    ├── tokenStorage.ts  getToken(), setToken(), removeToken(), hasToken()
    └── errorUtils.ts    getErrorMessage() — converts errors to user-friendly strings
```

### Implemented Routes (Phase 10)

| Route | Behavior |
|-------|----------|
| `/login` | Public only — redirects to `/dashboard` if authenticated |
| `/register` | Public only — redirects to `/dashboard` if authenticated |
| `/dashboard` | Protected — redirects to `/login` if unauthenticated |
| `/` | Redirects to `/login` |
| `*` | Redirects to `/login` |

---

## Key Architectural Rules

1. **Each business service owns its own data.** No shared databases.
2. **Use `BigDecimal` for all monetary calculations.** Never use `double` or `float`.
3. **Use DTOs for API communication.** Do not expose JPA entities directly.
4. **Controllers remain thin.** Business logic belongs in service classes.
5. **Balance and debt-simplification logic is isolated.** Kept in dedicated classes within their respective services.
6. **Backend authorization is enforced.** JWT-based authentication on every protected endpoint.
7. **Passwords are never stored in plain text.** BCrypt is used for password hashing.
8. **Avoid unnecessary infrastructure.** Do not introduce technologies until they are actually needed.
9. **COMPLETED settlements affect balances; PENDING do not.** Balance calculation uses only COMPLETED settlements, filtered at the repository level using `SettlementStatus.COMPLETED`.
10. **No circular dependencies.** `BalanceInternalController` returns raw expense balances (no settlement adjustment); `BalanceCalculationService` adjusts with settlements for the public API. These two paths are deliberately separate — originally across the Balance and Settlement services, now in-process inside Settlement Service — to preserve the Balance → Settlement → Balance cycle avoidance.
