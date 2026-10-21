# HISHOB

**Free web-based shared expense management platform.**

HISHOB is a Splitwise-style web application that allows users to manage shared expenses within groups, split costs fairly, and track who owes whom. It is a website/web application accessed through a browser, not a native mobile application.

HISHOB is being built from a clean microservices architecture from scratch.

---

## Development Status

> **Current architecture (Phase 3 consolidation):** the active backend is API Gateway + Auth (incl. notifications) + Expense (incl. groups/memberships) + Settlement (incl. balances). The phase notes below are historical records of the original standalone services; where they describe cross-service HTTP hops (Balance↔Settlement, Group Service reads), those calls now happen inside the consolidated services. See ARCHITECTURE.md and DEPLOYMENT_RENDER.md for the current topology.

**Phase 10 — React Frontend Foundation** *(completed)*

React + Vite + TypeScript frontend foundation implemented in `frontend/hishob-web/`. Includes authentication UI (login, registration), JWT management via `tokenStorage.ts`, Axios API client configured against the API Gateway, protected/public routing, and a dashboard placeholder. The frontend communicates exclusively with the API Gateway on port 8087 — no microservice is called directly from the browser. CORS is configured at the Gateway to allow `http://localhost:5173`. All backend errors are converted to user-friendly messages; no stack traces are exposed to the browser.

**Settlement/Balance Integration** *(completed)*

Completed settlements now feed into Balance Service calculations. Settlement Service exposes a new internal endpoint `GET /internal/groups/{groupId}/settlements/completed` (protected by `X-Internal-Token`, not accessible through the Gateway) that returns all COMPLETED settlements for a group. Balance Service's `BalanceCalculationService` fetches these after computing expense-derived balances and applies each settlement: the payer's (`fromUserId`) net balance increases by the settlement amount and the receiver's (`toUserId`) decreases by the same amount. PENDING settlements are excluded at source and never affect balances. Over-settlement (a debtor paying more than they owe) is rejected with a `BalanceCalculationException` to prevent silent balance inversions. Conservation is re-verified after all adjustments. If Settlement Service is unavailable, Balance Service returns 503 — it never silently assumes zero settlements. The `BalanceInternalController` (used by Settlement Service for debt simplification input) intentionally returns raw expense-derived balances only, avoiding a circular dependency.

**Phase 9 — Settlement Service** *(completed)*

The Settlement Service is implemented on port 8085 with PostgreSQL database `hishob_settlement`. It reads net balances from Balance Service via `GET /internal/groups/{groupId}/balances` (using `X-Internal-Token` service authentication) and applies a deterministic debtor/creditor matching algorithm to simplify debts into the minimal set of transactions. Records completed settlements in its own `settlements` table and provides settlement history per group. JWT authentication is mandatory for all public endpoints; the authenticated user's ID is always taken from the validated JWT, never from the request. Only group members can view suggestions, record settlements, or access history (validated via Group Service internal APIs). Gateway routes `/api/groups/*/settlements/history` and `/api/groups/*/settlements` → `lb://settlement-service`, placed before the generic `/api/groups/**` route. Internal endpoints are not exposed through the Gateway. Covered by 14 algorithm unit tests (DebtSimplificationAlgorithmTest), 15 controller integration tests (SettlementControllerIntegrationTest), and 9 internal controller tests (SettlementInternalControllerTest). Deferred to later phases: Notification Service, React frontend, Docker, deployment.

**Phase 8 — Balance Service** *(completed)*

The Balance Service is implemented on port 8084 with no database (stateless, on-demand calculation). It retrieves expense data from Expense Service via `GET /internal/groups/{groupId}/expenses` and group membership data from Group Service via `GET /internal/groups/{groupId}/members`, both using Spring RestClient with `X-Internal-Token` service authentication. Calculates `netBalance = totalPaid - totalShare` per member using `BigDecimal` throughout, then adjusts for completed settlements fetched from Settlement Service. Returns `GETS` (positive), `OWES` (negative), or `SETTLED` (zero) status per user based on the effective post-settlement balance. Enforces balance conservation (sum of all effective net balances must equal zero). All group members appear in the result even with zero activity. Routes through the Gateway via Eureka `lb://balance-service` at `GET /api/groups/{groupId}/balances`. Covered by unit tests (BalanceCalculationServiceTest — 33 tests) and integration tests (BalanceControllerIntegrationTest — 16 tests).

---

## Technology Stack

### Backend

| Technology | Purpose |
|-----------|---------|
| Java 21 | Language |
| Spring Boot 4.1.1 | Application framework |
| Spring Data JPA | Data access |
| Hibernate | ORM |
| Spring Security | Authentication and authorization |
| JWT | Token-based authentication |
| BCrypt | Password hashing |
| Maven | Build and dependency management |

### Microservices Infrastructure

| Technology | Purpose |
|-----------|---------|
| Spring Cloud Gateway | API Gateway |
| Eureka | Service discovery |
| Spring Cloud Config | Centralized configuration |
| OpenFeign | Synchronous inter-service communication |

### Database

| Technology | Purpose |
|-----------|---------|
| PostgreSQL | Relational database (one per business service) |

### Frontend

| Technology | Purpose |
|-----------|---------|
| React 19 | Component-based UI framework |
| Vite 8 | Build tool and dev server |
| TypeScript | Type-safe JavaScript |
| Axios | HTTP requests to the API Gateway |
| React Router 7 | Client-side routing |
| Vanilla CSS | Custom design system with CSS custom properties |

### Testing

| Technology | Purpose |
|-----------|---------|
| JUnit | Unit testing |
| Mockito | Mocking |
| Postman | API testing |

### DevOps

| Technology | Purpose |
|-----------|---------|
| Docker | Containerization |
| Docker Compose | Multi-container orchestration |
| Git / GitHub | Version control |

---

## Planned Microservices

### Infrastructure Services

Config Server and Eureka are **local development only**. Deployed environments (Render free tier) disable them (`EUREKA_CLIENT_ENABLED=false`, `optional:configserver:` import) and drive all routing from environment variables pointing at public HTTPS service URLs.

| Service | Responsibility |
|---------|---------------|
| Discovery Server (Eureka) | Service registry and discovery (local dev only) |
| Config Server | Centralized configuration management (local dev only) |
| API Gateway | Request routing, CORS, JWT validation |

### Business Services

Consolidated target architecture: the active deployed backend is **Auth, Expense and Settlement** (plus the API Gateway). The Group, Balance and Notification services are merged into Expense, Settlement and Auth respectively; their modules remain in the Maven reactor for local reference only and are no longer deployed or started by `start-hishob.ps1`.

| Service | Responsibility | Deployment status |
|---------|---------------|-------------------|
| Auth Service | Registration, login, JWT, user management, in-app notifications | Active (owns notifications) |
| Expense Service | Group + membership management, shared expense recording, split calculation | Active (owns groups) |
| Settlement Service | Balance computation, debt simplification, payment recording, settlement history | Active (owns balances) |
| API Gateway | Request routing, CORS | Active |
| Group Service | (merged into Expense Service) | Legacy module — not deployed |
| Balance Service | (merged into Settlement Service) | Legacy module — not deployed |
| Notification Service | (merged into Auth Service) | Legacy module — not deployed |

---

## Frontend Setup

### Requirements

- Node.js 20+
- npm 10+

### Running the frontend

```bash
cd frontend/hishob-web
cp .env.example .env
npm install
npm run dev
```

Frontend starts at: **http://localhost:5173**

All API requests are routed through the API Gateway at **http://localhost:8087**.

### Environment variables

| Variable | Description | Default |
|----------|-------------|----------|
| `VITE_API_BASE_URL` | HISHOB API Gateway URL | `http://localhost:8087` |

> **Security:** `VITE_*` variables are bundled into the browser build. Never put JWT secrets, database credentials, or internal service tokens into `VITE_*` variables.

### Backend services required for frontend

Start in this order: Config Server → Eureka → Auth Service → API Gateway → Frontend

| Service | Port | Purpose |
|---------|------|---------|
| Config Server | 8888 | Centralized configuration |
| Eureka Discovery Server | 8761 | Service registration |
| Auth Service | 8081 | Login, register, /me |
| API Gateway | 8087 | **The only backend port the frontend calls** |

### Authentication flow

```
Browser (React Frontend :5173)
  ↓  POST /api/auth/login  or  POST /api/auth/register
API Gateway :8087
  ↓  routes /api/auth/** → Auth Service
Auth Service :8081
  ↓  returns { accessToken, tokenType: "Bearer", user }
tokenStorage.ts  →  localStorage["hishob_access_token"]
AuthContext  →  user state updated
ProtectedRoute  →  /dashboard accessible

Subsequent authenticated requests:
Axios interceptor (api.ts)  →  Authorization: Bearer <JWT>
API Gateway :8087  →  microservices
```

---

## Planned Database

Each business service will own its own PostgreSQL database:

| Service | Database |
|---------|----------|
| Auth Service | `hishob_auth` — users and active notifications |
| Expense Service | `hishob_expense` — authoritative groups, memberships, expenses, splits |
| Settlement Service | `hishob_settlement` — settlements (balance calculation is stateless) |
| Group Service | `hishob_group` — legacy store; data copied into `hishob_expense`, no longer written |
| Notification Service | `hishob_notification` — legacy store; historical rows pending an approved migration |

On Render all services share one PostgreSQL instance with isolated schemas (the free tier is deleted after 30 days — see DEPLOYMENT_RENDER.md).

---

## Development Roadmap

| Phase | Scope | Status |
|-------|-------|--------|
| Phase 0 | Repository audit | ✅ Complete |
| Phase 1 | Project foundation and structure | ✅ Complete |
| Phase 1B | Cleanup old project + root Maven wrapper | ✅ Complete |
| Phase 2 | Discovery Server (Eureka) | ✅ Complete |
| Phase 3 | Config Server implementation | ✅ Complete |
| Phase 4 | API Gateway implementation | ✅ Complete |
| Phase 5 | Auth Service implementation | ✅ Complete |
| Phase 6 | Group Service implementation | ✅ Complete |
| Phase 7 | Expense Service implementation | ✅ Complete |
| Phase 8 | Balance Service implementation | ✅ Complete |
| Phase 9 | Settlement Service implementation | ✅ Complete |
| Phase 10 | React Frontend Foundation | ✅ Complete |
| Phase 11 | Notification Service implementation | ✅ Complete — later merged into Auth Service (standalone module retired from deployment) |
| Phase 12 | Frontend — Groups, Expenses, Balances, Settlements | Planned |
| Phase 13 | Docker and deployment | Planned |

> **Note:** Phase ordering may be adjusted as development progresses.

---

## Project Structure

```
HISHOB/
├── pom.xml                          Root Maven aggregator
├── mvnw                             Maven wrapper script (Unix)
├── mvnw.cmd                         Maven wrapper script (Windows)
├── .mvn/                            Maven wrapper configuration
├── README.md
├── ARCHITECTURE.md
├── .gitignore
│
├── infrastructure/
│   ├── discovery-server/            Eureka service registry
│   └── config-server/              Centralized configuration
│
├── gateway/
│   └── api-gateway/                 Request routing and filtering
│
├── services/
│   ├── auth-service/                Authentication, users, notifications
│   ├── group-service/               Legacy — groups/memberships merged into expense-service (reactor only)
│   ├── expense-service/             Groups, memberships, expense recording and splitting
│   ├── balance-service/             Legacy — balance calculation merged into settlement-service (reactor only)
│   ├── settlement-service/          Balances, settlements and history
│   └── notification-service/        Legacy — notifications merged into auth-service (reactor only)
│
└── frontend/
    └── hishob-web/                  React SPA (Phase 10)
        ├── src/
        │   ├── components/          common/FormField, layout/AppLayout
        │   ├── context/             AuthContext
        │   ├── hooks/               useAuth
        │   ├── pages/               auth/ and dashboard/
        │   ├── routes/              ProtectedRoute, PublicRoute
        │   ├── services/            api.ts, authService.ts
        │   ├── types/               auth.ts
        │   └── utils/               tokenStorage.ts, errorUtils.ts
        ├── .env.example
        └── package.json
```

---

## License

This project is not yet licensed.
