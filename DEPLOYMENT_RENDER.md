# HISHOB Render Free-Tier Deployment Guide

This guide details the deployment preparation, operational trade-offs, and step-by-step procedure for hosting HISHOB on Render's Free Tier (₹0/month budget) with the React/Vite frontend hosted on Vercel.

---

## 1. Scope & Architecture

### Deployed Services
The active deployment is the consolidated four-service backend plus one PostgreSQL database. Groups and memberships are owned by the Expense Service, balances by the Settlement Service, and notifications by the Auth Service.

| Service | Type | Plan | Port | Schema / Storage | Public Render URL |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **hishob-gateway** | Web Service (Docker) | Free | 8080 (dynamic) | None (Stateless) | `https://hishob-gateway.onrender.com` |
| **hishob-auth-service** | Web Service (Docker) | Free | 8081 (dynamic) | `hishob_auth` (users + notifications) | `https://hishob-auth-service.onrender.com` |
| **hishob-expense-service** | Web Service (Docker) | Free | 8083 (dynamic) | `hishob_expense` (groups, memberships, expenses) | `https://hishob-expense-service.onrender.com` |
| **hishob-settlement-service** | Web Service (Docker) | Free | 8085 (dynamic) | `hishob_settlement` (settlements; balance calculation is stateless) | `https://hishob-settlement-service.onrender.com` |
| **hishob-postgres** | Managed PostgreSQL | Free | 5432 | 4 schemas in `hishob_db` | Internal & External connection strings |

### Intentionally Omitted Services
To prevent instance-hour exhaustion and avoid broken private networking on Render's free tier, the following components are excluded from this deployment:
1. **Eureka Discovery Server (`discovery-server`)**: Local development only. Disabled via `EUREKA_CLIENT_ENABLED=false`. All inter-service and gateway routing is environment-driven via public HTTPS endpoints.
2. **Config Server (`config-server`)**: Local development only. Disabled via Spring Boot's `optional:configserver:` import. Configuration is managed centrally via Render's `hishob-shared` environment group.
3. **Notification Service (`notification-service`)**: Merged into Auth Service. Notifications are stored in `hishob_auth` and served through `hishob-auth-service` (`/api/notifications/**` via the Gateway, dispatch via `POST /internal/notifications` with `X-Internal-Token`). The standalone module remains in the Maven reactor for local reference only. Its legacy `hishob_notification` store still holds historical rows; migrating them is a separate approved task.
4. **Group Service (`group-service`)**: Merged into Expense Service. `render.yaml` no longer supplies a `GROUP_SERVICE_URL` override, and no gateway route resolves to `hishob-group-service`. The module remains in the Maven reactor (writes frozen) and the legacy `hishob_group` database is preserved unchanged.
5. **Balance Service (`balance-service`)**: Merged into Settlement Service. All balance endpoints are served by `hishob-settlement-service` (`BalanceController`, `BalanceInternalController`, `BalanceCalculationService`). `BALANCE_SERVICE_URL` has been removed from `render.yaml`. The module remains in the Maven reactor for local reference only.

### Notification Availability
* **What Works**: Registration, authentication, JWT verification, user profile updates, friend requests/approvals, group creation/management, expense creation, splits (equal, exact, percentage), personal expenses, debt calculations, balance simplification, settlement processing, and in-app notifications (served by Auth Service — Expense and Settlement dispatch through `POST /internal/notifications`, wrapped in defensive `try-catch` blocks so dispatch failures never break a business transaction).
* **Known Gap**: ~135 historical notification rows still live in the legacy `hishob_notification` store and have **not** been migrated into `hishob_auth`; only notifications created after the cutover appear in the UI. The migration requires separate approval.

---

## 2. Render Free-Tier Limitations & Operational Constraints

1. **Cold Starts**: Render free web services spin down to 0 instances after **15 minutes of inactivity**. The first incoming request takes **30 to 60 seconds** to spin up the container.
2. **Free Instance-Hour Quota**: Render grants **750 free instance hours** per month per account.
   * Four 24/7 web services would consume $4 \times 720 = 2,880$ hours, which would exceed the quota if run continuously.
   * Because services spin down when idle, this test deployment is intended for on-demand evaluation and smoke testing.
3. **PostgreSQL Expiration**: Render Free PostgreSQL databases expire and are deleted **30 days** after creation. This is not durable storage: a paid database plan (or an external managed PostgreSQL instance) is a required hosting decision before real user data relies on this stack.
4. **No Private Networking on Free Tier**: Free web services cannot receive traffic over Render's internal private network (`*.render.internal`). Consequently, all inter-service REST calls (e.g. Settlement Service calling Expense or Auth services) must use the public HTTPS URLs protected by the internal service token (`X-Internal-Token`).
5. **Memory Constraints**: Free tier containers have **512 MB RAM** and **0.1 vCPU**. The JVM runtime is explicitly tuned with conservative memory settings:
   `-XX:+UseSerialGC -Xmx220m -Xms128m -XX:MaxMetaspaceSize=96m`

---

## 3. Pre-Deployment Setup & Blueprint Launch

### Step 1: Database Schema Initialization (Manual Step)
Render PostgreSQL free instances grant standard user privileges but deny database creation (`CREATE DATABASE`). All services share the `hishob_db` database using isolated schemas.

> [!IMPORTANT]
> PostgreSQL does not create schemas automatically when JDBC connections specify `?currentSchema=...`. You must create the schemas once using the supplied SQL script before the microservices connect.

1. In the Render Dashboard, create the PostgreSQL database `hishob-postgres` or wait for the Blueprint to provision it.
2. Copy the **External Database URL** (e.g., `postgresql://hishob_user:password@dpg-xxx.oregon-postgres.render.com/hishob_db`).
3. Execute `scripts/init-schemas.sql` using `psql`:
   ```bash
   psql "postgresql://hishob_user:<PASSWORD>@<HOST>/hishob_db?sslmode=require" -f scripts/init-schemas.sql
   ```
4. Verify the schemas were created:
   ```sql
   SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'hishob_%';
   ```
   Expected output: `hishob_auth`, `hishob_group`, `hishob_expense`, `hishob_settlement`. (`hishob_group` remains provisioned for the preserved legacy data; no deployed service writes to it.)

### Step 2: Deploy Using Render Blueprint (`render.yaml`)
1. Push your repository to GitHub.
2. In the Render Dashboard:
   * Click **New +** $\rightarrow$ **Blueprint**.
   * Connect your `Hishob` repository.
   * Render will detect `render.yaml` and display the 4 web services, 1 database, and `hishob-shared` environment variable group.
3. Render automatically generates strong, cryptographically secure values for:
   * `JWT_SECRET`
   * `INTERNAL_SERVICE_TOKEN`
4. Click **Apply**.
5. Once Render allocates the service names, verify that the public service URLs match the values in `render.yaml`:
   * `AUTH_SERVICE_URL: https://hishob-auth-service.onrender.com`
   * `EXPENSE_SERVICE_URL: https://hishob-expense-service.onrender.com`
   * `SETTLEMENT_SERVICE_URL: https://hishob-settlement-service.onrender.com`
   * `GROUP_SERVICE_URL`, `BALANCE_SERVICE_URL` and `NOTIFICATION_SERVICE_URL` are no longer published. Expense Service owns groups and memberships (gateway `/api/groups/**` catch-all and all group-validation calls resolve through `EXPENSE_SERVICE_URL`); Settlement Service owns balances; Auth Service owns notifications and `/internal/notifications` dispatch. Routing any of these back to the legacy services would reintroduce split-brain data.
   *(If Render appended a unique suffix, update the corresponding URL in the `hishob-shared` environment group in the Render Dashboard).*

---

## 4. Frontend (Vercel) & CORS Configuration

### Step 1: Configure Vercel
1. Navigate to your frontend project in the [Vercel Dashboard](https://vercel.com).
2. Go to **Settings** $\rightarrow$ **Environment Variables**.
3. Set or update `VITE_API_BASE_URL`:
   * Key: `VITE_API_BASE_URL`
   * Value: `https://hishob-gateway.onrender.com` (pointing to the deployed API Gateway, **not** individual microservices).
4. Trigger a frontend deployment in Vercel to bake in the updated environment variable.

### Step 2: Configure CORS Origins
In `render.yaml`, the API Gateway has `CORS_ALLOWED_ORIGINS` configured:
```yaml
CORS_ALLOWED_ORIGINS: "https://hishob-one.vercel.app,http://localhost:5173"
```
This is a comma-separated list matched against the browser's `Origin` header by the `CorsWebFilter`
in `ApiGatewayApplication`. Any origin not listed here gets **no `Access-Control-Allow-Origin` header**,
so the preflight is rejected and the frontend sees `net::ERR_FAILED` — the origin value must equal the
deployed Vercel URL exactly, including scheme and no trailing slash.

If you add a custom domain or need Vercel preview deployments to work, append each exact origin to this
list (and to the Gateway's environment settings in the Render Dashboard until the Blueprint is synced).
Do not widen this to `https://*.vercel.app`: origin patterns are honoured by `UrlBasedCorsConfigurationSource`,
but a wildcard lets any Vercel project call the gateway and turns into a hard browser error the moment
`Access-Control-Allow-Credentials` is enabled. Credentials are deliberately off — the frontend sends the
JWT in the `Authorization` header, not a cookie.

Verify a deployed gateway from the command line (expect `200` and the echoed origin):
```bash
curl -i -X OPTIONS https://hishob-gateway.onrender.com/api/auth/login \
  -H "Origin: https://hishob-one.vercel.app" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: authorization,content-type"
```
An unlisted origin must return `403` with no `Access-Control-Allow-Origin` header — that is the
fail-closed behaviour, not a bug.

---

## 5. Cold-Start Warm-Up Script

Render free instances spin down after 15 minutes of inactivity. To wake up all 4 services simultaneously before running tests or demos:

Run the PowerShell concurrent warm-up script from the repository root:
```powershell
.\scripts\warmup-services.ps1
```

Custom URLs or timeout can be passed as parameters:
```powershell
.\scripts\warmup-services.ps1 -GatewayUrl "https://my-gateway.onrender.com" -TimeoutSec 90
```

The script sends concurrent non-intrusive `GET /health` requests to all 4 services without sending credentials or altering any data.

---

## 6. Local Testing & Verification Before Deployment

Before triggering any remote deployment, verify that all backend modules compile and pass their tests locally.

### Targeted Test Suite (Deployed Modules)
Run tests for the services included in this deployment:
```powershell
.\mvnw.cmd test -pl gateway/api-gateway,services/auth-service,services/expense-service,services/settlement-service
```
The legacy `group-service`, `balance-service` and `notification-service` modules remain in the reactor; they are still covered by the full suite (`.\mvnw.cmd test`) but are not deployed.

### Individual Service Security Verification
Verify internal token authentication (`missing → 401`, `wrong → 401`, `valid → 200/204`):
```powershell
.\mvnw.cmd test -pl services/auth-service -Dtest=FriendshipInternalControllerSecurityTest
.\mvnw.cmd test -pl services/expense-service -Dtest=ExpenseInternalControllerSecurityTest
.\mvnw.cmd test -pl services/settlement-service -Dtest=SettlementInternalControllerTest
```
Legacy-module security tests (`GroupInternalControllerSecurityTest`, `BalanceInternalControllerSecurityTest`) still run under the full suite but correspond to modules excluded from deployment.

### Local Development Preserved
Local startup via `start-hishob.ps1` launches the consolidated stack: Config Server (:8888), Eureka (:8761), Auth (:8081), Expense (:8083), Settlement (:8085) and the API Gateway (:8087).
* The legacy Group (:8082), Balance (:8084) and Notification (:8086) services are no longer started by default; `stop-hishob.ps1` still sweeps their ports/JARs as a manual-rollback safety net.
* Local ports remain the default fallback when `PORT` is not set.
* Eureka registration and Config Server remain enabled locally (deployment overrides them via env vars).
* Local PostgreSQL database names remain default fallbacks (`hishob_auth`, `hishob_group`, etc.).
