# HISHOB Phase 7 - Expense Service - Product Requirements Document

## Overview
- **Summary**: Create the Expense Service microservice for HISHOB, a shared expense management platform. The service owns Expense and ExpenseSplit entities, provides CRUD APIs for expenses, supports three split types (EQUAL, EXACT, PERCENTAGE), validates group membership via Group Service internal APIs, and performs precise BigDecimal monetary calculations with deterministic rounding.
- **Purpose**: Establish the expense and split record-keeping layer that will serve as the source of truth for future Balance Service and Settlement Service calculations.
- **Target Users**: Authenticated HISHOB users who create, view, update, and delete expenses within groups they belong to.

## Goals
- Create expense records with payer, participants, and validated splits.
- Support three split strategies: EQUAL, EXACT, and PERCENTAGE.
- Guarantee that split amounts sum exactly to the expense total using deterministic rounding.
- Validate all cross-service references (group existence, membership) via Group Service APIs.
- Enforce authorization rules (members read, creator updates/deletes).
- Register with Eureka and route through API Gateway.
- Pull external configuration from Config Server.

## Non-Goals
- No balance or debt calculation (deferred to Balance Service).
- No settlement or payment logic (deferred to Settlement Service).
- No notifications (deferred to Notification Service).
- No direct database access to `hishob_auth` or `hishob_group`.
- No messaging infrastructure (Kafka, RabbitMQ, Redis).
- No frontend code.
- No OpenFeign (use Spring RestClient exclusively).

## Background & Context
- Phases 1-6 are complete: Discovery (8761), Config (8888), Gateway (8080), Auth (8081), Group (8082).
- Group Service exposes internal endpoints: `/internal/groups/{groupId}/exists` and `/internal/groups/{groupId}/members/{userId}/exists`.
- A partial `expense-service` module exists with pom.xml, Expense entity, ExpenseSplit entity, SplitType enum, and application entry point.
- All services follow a consistent pattern: JWT stateless auth, DTOs over entities, GlobalExceptionHandler, RestClient for inter-service calls, H2 in tests.
- Money is always represented with `BigDecimal` (precision 19, scale 2).

## Functional Requirements
- **FR-1**: Module `services/expense-service` is a valid child of the root Maven aggregator and builds with `.\mvnw.cmd clean verify`.
- **FR-2**: Expense Service runs on port 8083, registers as `EXPENSE-SERVICE` with Eureka, imports Config Server via `spring.config.import`, and uses database `hishob_expense`.
- **FR-3**: `POST /api/expenses` creates an Expense plus ExpenseSplits transactionally.
- **FR-4**: `GET /api/groups/{groupId}/expenses` returns expenses for a group (member-only).
- **FR-5**: `GET /api/expenses/{expenseId}` returns a single expense with splits (member-only).
- **FR-6**: `PUT /api/expenses/{expenseId}` updates an expense (creator-only, full re-validation).
- **FR-7**: `DELETE /api/expenses/{expenseId}` deletes an expense and its splits (creator-only).
- **FR-8**: Three split types are supported: EQUAL (round-robin remainder distribution), EXACT (sum == total), PERCENTAGE (100% total, then convert).
- **FR-9**: Group and membership validation is performed via Group Service internal API calls using RestClient.
- **FR-10**: JWT stateless authentication is enforced; `createdBy` is derived from the authenticated principal, never from request JSON.
- **FR-11**: API Gateway routes `/api/expenses/**` and `/api/groups/*/expenses/**` to `lb://expense-service`.
- **FR-12**: Duplicate participants are rejected (application + DB unique constraint).
- **FR-13**: Expense and ExpenseSplit entities are not exposed directly; DTOs (`CreateExpenseRequest`, `ExpenseSplitRequest`, `ExpenseResponse`, split sub-DTO) are used.
- **FR-14**: Cross-service internal endpoints required by Expense Service exist in Group Service (already present: exists endpoints).

## Non-Functional Requirements
- **NFR-1**: Monetary precision. All split amount sums must equal `totalAmount` exactly using two-decimal scale.
- **NFR-2**: Transactional integrity. Partial expense+split creation is impossible; rollback on any failure.
- **NFR-3**: Security. No password fields, no JWT internals in responses; all endpoints except `/error` and `/actuator/**` require authentication.
- **NFR-4**: Test coverage. Unit tests with mocked GroupServiceClient; integration tests with H2 and JWT auth; at least 21 distinct scenarios.
- **NFR-5**: Code consistency. Follows package structure, exception handling, and naming patterns established by Group Service.
- **NFR-6**: Config Server external config file `expense-service.yml` is present in Config Server native config.

## Constraints
- **Technical**: Java 21, Spring Boot 4.1.1, Spring Cloud 2025.1.3 (Oakwood), JJWT 0.12.6, PostgreSQL, H2 for tests.
- **Business**: Only group members create/view expenses; only the creator updates/deletes. Payer and participants must be members.
- **Dependencies**: Eureka for discovery, Config Server for external config, Auth Service for JWT signing key, Group Service internal APIs for validation.

## Assumptions
- The partial `Expense`, `ExpenseSplit`, and `SplitType` entity classes already present are kept as the basis (their structure matches the spec: UUID IDs, BigDecimal(19,2), unique (expense_id,userId)).
- Group Service internal endpoints already satisfy the needs (single-member existence check is sufficient; multiple users can be checked with individual calls).
- JWT secret is shared with Auth Service via `JWT_SECRET` env var / Config Server.

## Open Questions
- None. All requirements are fully specified in the Phase 7 document.

## Acceptance Criteria

### AC-1: Module structure and build
- **Type**: `rule`
- **Given**: Root Maven aggregator pom.xml
- **When**: Running `.\mvnw.cmd -pl services/expense-service -am clean verify`
- **Then**: The module compiles, tests pass, and the reactor includes the expense-service module
- **Pass Condition**: Exit code 0, expense-service artifact produced
- **Evidence**: Maven build log excerpt

### AC-2: Port, Eureka, Config, Database
- **Type**: `rule`
- **Given**: application.yml in expense-service and expense-service.yml in Config Server
- **When**: Service starts with env defaults (no overrides)
- **Then**: Listens on 8083; Eureka registration as EXPENSE-SERVICE; config import from localhost:8888; datasource targets hishob_expense
- **Pass Condition**: application.yml and config file contain the required keys
- **Evidence**: File contents inspection

### AC-3: POST create expense EQUAL split — correct rounding and sum
- **Type**: `rule`
- **Given**: Valid group with 3 members; authenticated member caller; totalAmount=100; splitType=EQUAL
- **When**: Create expense request is processed
- **Then**: Three splits are generated (33.34, 33.33, 33.33); sum equals 100.00 exactly; paidBy is member; createdBy equals caller
- **Pass Condition**: BigDecimal.compareTo(sum, 100.00) == 0
- **Evidence**: Unit test assertion + integration test JSON response

### AC-4: POST create expense EXACT split — sum validated
- **Type**: `rule`
- **Given**: Total 1000; EXACT splits 600 and 400 (valid); vs 600 and 300 (invalid)
- **When**: Both requests are submitted
- **Then**: Valid case returns 201; invalid case returns 400 with message about split total mismatch
- **Pass Condition**: HTTP status assertions and BigDecimal sum comparison
- **Evidence**: Integration test status + jsonPath assertions

### AC-5: POST create expense PERCENTAGE split — 100% enforced
- **Type**: `rule`
- **Given**: Total 1000; PERCENTAGE splits 50% + 50% (valid); 50% + 49% (invalid)
- **When**: Both requests are submitted
- **Then**: Valid case produces splits 500.00 + 500.00; invalid case returns 400
- **Pass Condition**: Percentage sum exactly 100; monetary sum equals total
- **Evidence**: Unit + integration tests

### AC-6: Group and membership validation via GroupServiceClient (RestClient)
- **Type**: `rule`
- **Given**: Create expense where paidBy is not a group member
- **When**: Request processed
- **Then**: 400 / 403 returned; no DB rows created; GroupServiceClient.isMember called for paidBy and every split user
- **Pass Condition**: Mockito verify on client; DB count == 0
- **Evidence**: Unit test mocks and integration test H2 DB state

### AC-7: createdBy derived from authenticated principal
- **Type**: `rule`
- **Given**: A request that includes a `createdBy` JSON field set to user X
- **When**: Request is made by authenticated user Y
- **Then**: Stored expense.createdBy == Y; the X value from JSON is ignored
- **Pass Condition**: DB row assertion + response jsonPath
- **Evidence**: Integration test with two distinct UUIDs

### AC-8: GET /api/groups/{groupId}/expenses — member-only
- **Type**: `rule`
- **Given**: Group G with member M and non-member N; two expenses created in G
- **When**: M calls GET and N calls GET
- **Then**: M sees both expenses (200, array size 2); N receives 403
- **Pass Condition**: HTTP status and array size assertions
- **Evidence**: Integration test

### AC-9: GET /api/expenses/{id} — member-only, includes splits
- **Type**: `rule`
- **Given**: Expense in group with two splits
- **When**: Member calls GET; non-member calls GET
- **Then**: Member receives 200 with splits array populated; non-member 403
- **Pass Condition**: Status, jsonPath for id/groupId/splits.size()
- **Evidence**: Integration test

### AC-10: PUT /api/expenses/{id} — creator-only, full revalidation
- **Type**: `rule`
- **Given**: Expense created by user A; user B is also member
- **When**: B attempts PUT; A attempts PUT with invalid EXACT split sum
- **Then**: B receives 403; A receives 400; no data persisted from failed A request
- **Pass Condition**: Status codes; DB unchanged
- **Evidence**: Integration test

### AC-11: DELETE /api/expenses/{id} — creator-only, cascade splits
- **Type**: `rule`
- **Given**: Expense with 3 splits created by A; member B exists
- **When**: B calls DELETE then A calls DELETE
- **Then**: B receives 403; A receives 204; after A's call expense_splits and expenses rows are gone
- **Pass Condition**: Status + repository count == 0
- **Evidence**: Integration test + repository assertions

### AC-12: Duplicate participants rejected (app + DB)
- **Type**: `rule`
- **Given**: EXACT request with user U appearing twice in splits
- **When**: Request processed
- **Then**: 400 returned; unique constraint on (expense_id, userId) is defined in @Table
- **Pass Condition**: HTTP 400; @UniqueConstraint present in ExpenseSplit entity
- **Evidence**: Source code inspection + integration test

### AC-13: Reject totalAmount <= 0, empty description, empty splits
- **Type**: `rule`
- **Given**: Three requests: negative amount, blank description, empty splits array
- **When**: Each is submitted with valid auth
- **Then**: All three return 400 with appropriate validation messages
- **Pass Condition**: Status 400 + validationErrors or message in response
- **Evidence**: Integration test parameterized cases

### AC-14: Transactional rollback — splits after expense
- **Type**: `rule`
- **Given**: Mock ExpenseRepository.save succeeds; then an ExpenseSplit with duplicate userId triggers constraint violation
- **When**: Create expense is attempted
- **Then**: Expense row is not left dangling (rollback); repository count == 0 for both tables
- **Pass Condition**: No expense row exists after failed creation
- **Evidence**: Unit test with explicit save order and transactional rollback check

### AC-15: BigDecimal correctness — 100/3 and 1000/6
- **Type**: `rule`
- **Given**: EQUAL split for total=100 count=3; total=1000 count=6
- **When**: Split calculator runs
- **Then**: 100/3 => [33.34, 33.33, 33.33] sum 100.00; 1000/6 sum equals 1000.00 exactly
- **Pass Condition**: BigDecimal.compareTo of sum vs total == 0
- **Evidence**: Unit test assertions on each split value

### AC-16: JWT stateless auth — invalid token yields 403
- **Type**: `rule`
- **Given**: Request with malformed Bearer token (or no token) to /api/expenses
- **When**: Processed
- **Then**: 403 Forbidden
- **Pass Condition**: HTTP 403
- **Evidence**: Integration test

### AC-17: API Gateway routes for expense-service
- **Type**: `rule`
- **Given**: Gateway application.yml
- **When**: Inspected
- **Then**: Contains route `/api/expenses/**` => `lb://expense-service`; contains `/api/groups/*/expenses/**` => `lb://expense-service`; group-service route still present
- **Pass Condition**: YAML contains the two new routes
- **Evidence**: File read of gateway yml

### AC-18: Config Server includes expense-service.yml
- **Type**: `rule`
- **Given**: Config Server native config directory
- **When**: Listed and read
- **Then**: expense-service.yml exists alongside auth-service.yml and group-service.yml
- **Pass Condition**: File exists; valid YAML
- **Evidence**: File presence + content

### AC-19: No User/Group/GroupMember entities; no cross-DB FKs
- **Type**: `rule`
- **Given**: expense-service source tree and ExpenseSplit FK definition
- **When**: Inspected
- **Then**: No JPA entity classes for User, Group, or GroupMember; ExpenseSplit FK only references expense.id
- **Pass Condition**: Grep for @Entity classes == Expense, ExpenseSplit only; FK in @JoinColumn points to expense table
- **Evidence**: Source file listing + entity source

### AC-20: 21+ passing tests (unit + integration)
- **Type**: `rubric`
- **Dimension**: Test coverage breadth and determinism
- **Scale**: 1-5
- **Anchors**: 1 = <10 tests and many scenarios missing; 3 = ~15 tests covering main happy paths only, no mocks for GroupServiceClient; 5 = >=21 tests covering all AC-3 through AC-15 scenarios, GroupServiceClient mocked in unit tests, integration tests use H2+JWT
- **Pass Threshold**: >= 4
- **Evidence**: Surefire report summary + test source file listing

### AC-21: End-to-end happy path (build verification)
- **Type**: `rule`
- **Given**: Full repository at implementation end
- **When**: `.\mvnw.cmd clean verify` from root
- **Then**: All 6 reactor modules (Discovery, Config, Gateway, Auth, Group, Expense) build and test successfully; exit code 0
- **Pass Condition**: BUILD SUCCESS; no failures
- **Evidence**: Final lines of Maven reactor output

### AC-22: Codebase consistency (pattern fidelity)
- **Type**: `rubric`
- **Dimension**: Adherence to existing Group Service / Auth Service patterns
- **Scale**: 1-5
- **Anchors**: 1 = Inconsistent package names, custom exception handler not modeled on GlobalExceptionHandler, missing AuthenticatedUser principal; 3 = Most patterns followed but minor mismatches (e.g., different ErrorResponse shape); 5 = Identical package layout, matching DTO static factory pattern (fromEntity), equivalent SecurityFilterChain, same RestClient client structure, same jjwt usage
- **Pass Threshold**: >= 4
- **Evidence**: Source file structure diff against group-service
