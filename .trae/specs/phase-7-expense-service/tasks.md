# HISHOB Phase 7 - Expense Service - Implementation Plan

## Task 1: Infrastructure scaffolding (application.yml, config, security skeleton, principal, repositories)
- **Status**: `pending`
- **Priority**: high
- **Depends On**: None
- **Description**:
  - Create `services/expense-service/src/main/resources/application.yml` with port 8083, Eureka, Config Server import, PostgreSQL datasource for `hishob_expense`, JWT secret property, group-service base URL.
  - Copy pattern from group-service but adapt for expense-service.
  - Create `infrastructure/config-server/config/expense-service.yml` (empty or minimal external config placeholder).
  - Create `ExpenseRepository` and `ExpenseSplitRepository` interfaces extending JpaRepository<UUID>.
    - ExpenseRepository: findById, findByGroupIdOrderByCreatedAtDesc.
    - ExpenseSplitRepository: findByExpenseId, deleteByExpenseId.
  - Create `AuthenticatedUser` principal class (userId UUID + email String).
  - Create security package: `JwtService`, `JwtAuthenticationFilter`, `SecurityConfig` following Group Service pattern.
- **Acceptance Criteria Addressed**: AC-1, AC-2, AC-16, AC-18
- **Test Requirements**:
  - `rule` TR-1.1: application.yml contains `server.port: 8083`, `spring.application.name: expense-service`, config import, datasource targeting hishob_expense. Evidence: file content grep.
  - `rule` TR-1.2: JWT filter rejects missing/invalid Bearer with 403 on secured paths. Evidence: Spring context test with unauthorized request.
  - `rubric` TR-1.3: Code pattern fidelity. Scale 1-5; 1=inconsistent; 3=mostly matches; 5=identical to group-service security structure; threshold >= 4. Evidence: source diff against group-service.
- **Notes**: Existing entity classes (Expense, ExpenseSplit, SplitType) are kept as-is because they already match the Phase 7 spec.

## Task 2: DTOs, request validation, and exceptions
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 1
- **Description**:
  - Create `CreateExpenseRequest`: groupId UUID, description String (NotBlank, max 300), totalAmount BigDecimal (@DecimalMin "0.01"), paidBy UUID, splitType SplitType, splits List<ExpenseSplitRequest> (@NotEmpty, @Valid).
  - Create `ExpenseSplitRequest`: userId UUID, amount BigDecimal (nullable, @DecimalMin "0.01"), percentage BigDecimal (nullable, @DecimalMin "0" @DecimalMax "100").
  - Create `ExpenseResponse` with fromEntity factory: id, groupId, description, totalAmount, paidBy, createdBy, splitType, splits, createdAt, updatedAt.
  - Create nested `ExpenseSplitResponse`: userId, amount, percentage (nullable), splitType.
  - Create `ErrorResponse` matching group-service shape (timestamp, status, error, message, path, validationErrors nullable).
  - Create exception classes: ExpenseNotFoundException, PermissionDeniedException, InvalidSplitException, InvalidGroupException, DuplicateParticipantException.
  - Create `GlobalExceptionHandler` with handlers for each custom exception, MethodArgumentNotValidException (field errors map), AuthenticationException (401), AccessDeniedException (403), and generic 500.
- **Acceptance Criteria Addressed**: AC-1, AC-12, AC-13
- **Test Requirements**:
  - `rule` TR-2.1: CreateExpenseRequest with totalAmount=0 triggers validation error; description blank triggers validation; splits empty triggers validation. Evidence: unit test with Validator factory.
  - `rule` TR-2.2: ExpenseResponse.fromEntity does NOT include password, JWT, or internals; includes splits list. Evidence: unit test serializing response to JSON, checking fields.

## Task 3: GroupServiceClient (RestClient cross-service validation)
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 2
- **Description**:
  - Create `GroupServiceClient` @Component using `RestClient` with base URL from property `hishob.group-service.base-url` (default http://localhost:8082).
  - Method `validateGroupExists(UUID groupId)`: calls GET /internal/groups/{groupId}/exists; 204 => ok, 404 => throws InvalidGroupException with message.
  - Method `validateMember(UUID groupId, UUID userId, String roleLabel)`: calls GET /internal/groups/{groupId}/members/{userId}/exists; 204 => ok, 404 => throws PermissionDeniedException or InvalidSplitException with roleLabel context ("payer"/"participant"/"creator").
  - Wrap non-404 HttpClientErrorException and generic Exception as "Unable to reach Group Service" / "Unable to validate group membership" message (consistent with AuthServiceClient in group-service).
- **Acceptance Criteria Addressed**: AC-6, AC-19
- **Test Requirements**:
  - `rule` TR-3.1: When 404 returned by internal endpoint, validateGroupExists throws InvalidGroupException; validateMember throws appropriate exception. Evidence: MockRestServiceServer-based tests.
  - `rule` TR-3.2: No direct JPA access to Group entity; only RestClient calls. Evidence: Grep of expense-service source for "GroupMember" or "Group" @Entity.

## Task 4: Split calculation and validation utility/service
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 2, Task 3
- **Description**:
  - Create `SplitService` @Component (not @Service to avoid name collision; or name it SplitCalculationService).
  - Constant: `MONETARY_SCALE = 2`, `RoundingMode = RoundingMode.HALF_UP` for interim, but final allocation deterministic.
  - `calculateEqualSplits(BigDecimal total, int n, List<UUID> users)`:
    1. Base = total.divide(n, 2, DOWN) => base each.
    2. Remainder = total.subtract(base.multiply(n)).
    3. Remainder expressed in cents (scale-by-power 2): remainderCents = remainder.scaleByPowerOfTen(2).intValueExact().
    4. Allocate 1 extra cent to first `remainderCents` users in the order provided.
    5. Verify sum.equals(total). Returns List<ExpenseSplit> with EQUAL type.
  - `validateExactSplits(BigDecimal total, List<ExpenseSplitRequest>)`: sum amounts and compare with total using compareTo == 0; if not, throw InvalidSplitException. Also reject amount == null or amount <= 0.
  - `validatePercentageSplits(BigDecimal total, List<ExpenseSplitRequest>)`:
    1. Sum percentages; reject if compareTo(100) != 0.
    2. Convert each: amount = total.multiply(percentage).divide(100, 2, HALF_UP).
    3. Compute remainder cents; distribute remainder to first users like equal case so final sum == total.
    4. Return list of ExpenseSplit with PERCENTAGE type + amount + percentage.
  - `validateNoDuplicateUsers(List<UUID>)`: check size vs distinct; throw DuplicateParticipantException.
- **Acceptance Criteria Addressed**: AC-3, AC-4, AC-5, AC-12, AC-15
- **Test Requirements**:
  - `rule` TR-4.1: 100 / 3 => [33.34, 33.33, 33.33]; sum == 100.00. Evidence: assertEquals on each value and sum.
  - `rule` TR-4.2: 1000 / 6 sum equals 1000.00 exactly. Evidence: BigDecimal compareTo.
  - `rule` TR-4.3: EXACT [600, 300] with total 1000 => InvalidSplitException. Evidence: unit test assertion.
  - `rule` TR-4.4: PERCENTAGE [50, 49] sum 99 => InvalidSplitException; [50, 50] => amounts [500.00, 500.00] for total 1000. Evidence: assertions.
  - `rule` TR-4.5: Duplicate participants => DuplicateParticipantException. Evidence: unit test.

## Task 5: ExpenseService business logic (CRUD + authZ + validation orchestration)
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 3, Task 4
- **Description**:
  - Create `ExpenseService` @Transactional.
  - `createExpense(CreateExpenseRequest req, UUID currentUserId)`:
    1. Validate current user is member via GroupServiceClient (PermissionDenied if not).
    2. Validate group exists via GroupServiceClient.
    3. Validate paidBy is member via GroupServiceClient.
    4. Collect split userIds; validate all are members via individual calls.
    5. Validate no duplicate participants.
    6. Dispatch to split calculation based on splitType.
    7. Build Expense entity; build ExpenseSplits; expense.addSplit for each.
    8. expenseRepository.save(expense) with cascade saves splits.
    9. Return ExpenseResponse.fromEntity(saved).
  - `listGroupExpenses(UUID groupId, UUID currentUserId)`: validate membership; repo.findByGroupIdOrderByCreatedAtDesc; map to responses.
  - `getExpenseDetail(UUID expenseId, UUID currentUserId)`: load expense or throw ExpenseNotFound; validate current user is member of expense.getGroupId(); return response.
  - `updateExpense(UUID expenseId, CreateExpenseRequest req, UUID currentUserId)`: load expense; ensure createdBy == currentUserId (403); re-run all validations (group, paidBy member, participants, splits); update fields + clearSplits + add new splits; save; return response.
  - `deleteExpense(UUID expenseId, UUID currentUserId)`: load expense; ensure creator; expenseRepository.delete(expense) (orphanRemoval=true handles splits).
- **Acceptance Criteria Addressed**: AC-3 through AC-11, AC-12 (app-level), AC-14, AC-6, AC-7
- **Test Requirements**:
  - `rule` TR-5.1: createExpense sets createdBy from currentUserId parameter regardless of anything. Evidence: pass currentUserId Y alongside a hypothetical different X; assert stored createdBy == Y.
  - `rule` TR-5.2: updateExpense by non-creator throws PermissionDenied. Evidence: unit test.
  - `rule` TR-5.3: deleteExpense by non-creator throws PermissionDenied; by creator succeeds. Evidence: unit test.
  - `rule` TR-5.4: getExpenseDetail by non-member throws PermissionDenied. Evidence: unit test.
  - `rubric` TR-5.5: Transactional rollback. Scale 1-5; 1=no @Transactional; 3=on create only; 5=create+update+delete all @Transactional and failure mid-process (split validation after group valid) leaves no rows; threshold >= 4. Evidence: source inspection + test simulating failure.

## Task 6: Controller layer + principal injection
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 5
- **Description**:
  - Create `ExpenseController` @RestController:
    - `POST /api/expenses` => `createExpense(@Valid @RequestBody, @AuthenticationPrincipal AuthenticatedUser)` => 201.
    - `GET /api/groups/{groupId}/expenses` => `listGroupExpenses(...)` => 200.
    - `GET /api/expenses/{expenseId}` => `getExpenseDetail(...)` => 200.
    - `PUT /api/expenses/{expenseId}` => `updateExpense(...)` => 200.
    - `DELETE /api/expenses/{expenseId}` => `deleteExpense(...)` => 204.
- **Acceptance Criteria Addressed**: AC-3 through AC-11, AC-16
- **Test Requirements**:
  - `rule` TR-6.1: Request without Authorization header to GET/POST/PUT/DELETE returns 403. Evidence: integration test (web layer).

## Task 7: Unit tests (service + split calculator)
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 5
- **Description**:
  - `SplitCalculationServiceTest` (or SplitServiceTest):
    1. EQUAL 100/3 exact values and sum.
    2. EQUAL 1000/6 sum.
    3. EXACT valid sum (600+400 == 1000).
    4. EXACT invalid sum throws.
    5. PERCENTAGE 50+50 valid converts correctly.
    6. PERCENTAGE 50+49 throws.
    7. Duplicate users throws.
    8. Negative amount throws.
    9. Zero amount throws.
    10. Decimal PERCENTAGE 33.33 + 33.33 + 33.34 = 100; amounts sum == total.
  - `ExpenseServiceTest` (Mockito):
    11. Create happy path - persists expense + splits with correct createdBy, paidBy, group.
    12. Non-member cannot create (PermissionDenied).
    13. Payer not in group => appropriate exception.
    14. Participant not in group => appropriate exception.
    15. Creator can update - splits replaced.
    16. Non-creator update denied.
    17. Creator can delete.
    18. Non-creator delete denied.
    19. Member can list group expenses.
    20. Member can view expense detail; non-member denied.
    21. Invalid group => InvalidGroupException.
- **Acceptance Criteria Addressed**: AC-20, AC-3 through AC-15
- **Test Requirements**:
  - `rule` TR-7.1: Total >= 21 test methods across unit test classes. Evidence: surefire report count.

## Task 8: Integration tests (MockMvc + H2 + JWT)
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 6
- **Description**:
  - Create `ExpenseControllerIntegrationTest` @SpringBootTest, H2 test application.yml (under test/resources).
  - MockitoBean for GroupServiceClient.
  - JWT helper using same TEST_SECRET as group-service tests; ReflectionTestUtils to inject into JwtService.
  - Helper: `asMember(UUID group, UUID user)` configures GroupServiceClient stubs to say group exists and user is member.
  - Test cases:
    1. Unauthenticated => 403.
    2. Create EQUAL happy path - 201, sum correct, splits size matches, createdBy from JWT.
    3. Create EXACT happy path.
    4. Create PERCENTAGE happy path.
    5. EXACT sum mismatch => 400.
    6. PERCENTAGE sum != 100 => 400.
    7. Negative totalAmount => 400.
    8. Zero totalAmount => 400.
    9. Duplicate participants => 400.
    10. Payer not in group => 403 or 400 (depending on exception).
    11. Participant not in group => appropriate status.
    12. Non-member tries to create => 403.
    13. Non-member tries GET expense => 403.
    14. Member GET group expenses - sees list.
    15. Creator PUT - succeeds; old splits gone.
    16. Non-creator PUT - 403.
    17. Creator DELETE - 204; repository empty.
    18. Non-creator DELETE - 403.
    19. Invalid group ID => 404/400.
- **Acceptance Criteria Addressed**: AC-20, AC-21
- **Test Requirements**:
  - `rule` TR-8.1: Running `mvnw.cmd -pl services/expense-service test` passes all integration tests with exit code 0. Evidence: surefire log.

## Task 9: API Gateway routes and Config Server file
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 6
- **Description**:
  - Edit `gateway/api-gateway/src/main/resources/application.yml`:
    - Add route id=expense-service-expenses: uri lb://expense-service, predicate Path=/api/expenses/**.
    - Add route id=expense-service-group-expenses: uri lb://expense-service, predicate Path=/api/groups/*/expenses/**.
    - Ensure ordering: /api/groups/*/expenses/** must come before the generic /api/groups/** route (or the more specific is checked first - Spring Cloud Gateway respects declaration order).
  - Copy expense-service.yml to Config Server resources/config/expense-service.yml if needed (mirror structure).
- **Acceptance Criteria Addressed**: AC-17, AC-18
- **Test Requirements**:
  - `rule` TR-9.1: Gateway application.yml contains both expense-service routes. Evidence: grep of file.
  - `rule` TR-9.2: expense-service.yml exists in Config Server native config locations (config/ and src/main/resources/config/). Evidence: file system listing.

## Task 10: Full root build, diagnostics, README/ARCHITECTURE updates
- **Status**: `pending`
- **Priority**: medium
- **Depends On**: Task 7, Task 8, Task 9
- **Description**:
  - Run `.\mvnw.cmd clean verify` from HISHOB root. Fix any build/test failures.
  - Update README.md Phase 7 entry to Status: Complete with bullet points: Expense Service 8083, hishob_expense DB, responsibilities list, deferred balance/settlement.
  - Update ARCHITECTURE.md with Expense Service ownership.
  - Run GetDiagnostics or IDE-level compile check; clean any warnings.
- **Acceptance Criteria Addressed**: AC-1, AC-21, AC-22
- **Test Requirements**:
  - `rule` TR-10.1: Full build BUILD SUCCESS; all 6 reactor modules SUCCESS. Evidence: final Maven log lines.
  - `rubric` TR-10.2: Pattern fidelity across entire service. Scale 1-5; threshold >= 4. Evidence: cross-service structure comparison.
