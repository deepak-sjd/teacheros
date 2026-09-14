# TeacherOS

Digital operating system for independent tutors and small coaching centers in India. MVP scope: attendance tracking + fee tracking for teachers, with a lightweight parent-facing view. See `docs/` (or the PRD shared separately) for full product context.

## Repo layout

```
backend/    Spring Boot 3.4 / Java 21 API (modular monolith)
mobile/     Flutter teacher app
```

## Backend - local setup

**Requirements:** Java 21, Maven, Docker (for Postgres).

1. Copy the environment template and adjust if needed:
   ```
   cp backend/.env.example backend/.env
   ```
2. Start Postgres (and optionally the backend itself) via Docker Compose:
   ```
   docker compose up postgres -d
   ```
3. Run the backend:
   ```
   cd backend
   mvn spring-boot:run
   ```
   Flyway runs the migrations automatically on startup. The API is available at `http://localhost:8080/api/v1`.

4. OTP codes are logged to the console in dev (`app.otp.delivery-mode=log` / `OTP_DELIVERY_MODE=log`) - no real SMS provider is needed to develop or test the login flow locally. Look for a line like:
   ```
   [DEV OTP] phone=+919000000000 code=123456 (no real SMS sent - dev delivery mode)
   ```

5. API docs (Swagger UI): `http://localhost:8080/swagger-ui.html`

### Running tests

```
cd backend
mvn verify
```

Integration tests use Testcontainers to spin up a real Postgres instance - Docker must be running.

### Running everything via Docker Compose

```
docker compose up --build
```

This starts Postgres and the backend together. Useful for a clean end-to-end check before a pilot demo.

## Mobile - local setup

**Requirements:** Flutter (stable channel).

1. Install dependencies:
   ```
   cd mobile
   flutter pub get
   ```
2. Run against a locally running backend. On the Android emulator, `10.0.2.2` maps to your host machine's `localhost`, which is why it's the dev default in `lib/core/config/env.dart`:
   ```
   flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1
   ```
   On a physical device, use your machine's LAN IP instead of `10.0.2.2`.

3. Run tests:
   ```
   flutter test
   ```

## Opening this project in VS Code

The repo is set up as a single VS Code workspace covering both `backend/` (Maven/Java) and `mobile/` (Flutter/Dart) - you don't need separate windows or a multi-root workspace file.

1. Open the `teacheros/` folder in VS Code (`File > Open Folder`).
2. VS Code will prompt you to install the recommended extensions (`.vscode/extensions.json`) - accept that prompt. It covers the Java/Spring Boot toolchain, Flutter/Dart, and a Postgres client.
3. Run the one-time setup task: `Cmd/Ctrl+Shift+P` → `Tasks: Run Task` → **"Setup: Postgres + backend + mobile deps"**. This starts Postgres in Docker and runs `flutter pub get`.
4. Debugging is pre-configured in `.vscode/launch.json`:
   - **"Backend: TeacherOsApplication (dev)"** - runs the Spring Boot app with breakpoints, hot-reload on save via Spring DevTools-style restart.
   - **"Mobile: TeacherOS (Android emulator, local backend)"** - runs the Flutter app pointed at `10.0.2.2`, which is how the Android emulator reaches your host machine's `localhost`.
   - **"Mobile: TeacherOS (physical device, set your LAN IP)"** - edit the IP in this config to your machine's LAN address before using it on a real phone; `10.0.2.2`/`localhost` won't resolve to your dev machine from a physical device.
   - **"Run backend + mobile together"** (compound) - starts both with one click, useful for daily development once both projects build cleanly.
5. Common commands are also available as tasks (`Tasks: Run Task`) instead of needing a terminal: backend tests, backend build, `flutter analyze`, `flutter test`.

If the Java extension doesn't pick up the Maven project automatically, run `Cmd/Ctrl+Shift+P` → `Java: Reload Projects` once `backend/pom.xml` exists on disk.

## What's implemented vs. not (status as of this scaffold)

**IMPLEMENTED:**
- Phone-OTP auth (request/verify/refresh), tenant provisioning on first login
- Tenant isolation enforced at the repository layer, with an integration test proving it
- Batches, students - full CRUD (tenant-scoped)
- Attendance - bulk marking per session, derived percentage calculation (never stored), unit-tested
- Fees - create dues, record payments, derive status, list pending
- Global error handling with a consistent error envelope, correlation-ID logging
- Flutter: auth flow (phone entry -> OTP verify -> home), secure token storage, Dio client with auto-refresh-on-401, GoRouter auth gating

**NOT YET IMPLEMENTED (by design - see blueprint for reasoning):**
- Attendance/batch/fee screens in Flutter (backend APIs exist; UI is the next slice)
- Parent-facing view (WhatsApp-link or web page)
- Any notification sending beyond logging OTPs to console
- Student portal, homework, tests, AI features, payments processing
- CI deployment step (build/test pipelines exist; deploy step is deliberately not wired up yet)

## Security notes for anyone touching this code

- **Tenant ID always comes from `CurrentTenantProvider`**, which derives it from the authenticated JWT. Never accept a tenant ID from a request body/param/path for scoping a query.
- **Every tenant-owned repository finder takes an explicit `tenantId` argument** (`findByIdAndTenantId`, not `findById`). If you add a new finder, follow this pattern.
- **Refresh tokens are opaque and stored hashed**, not JWTs - this is what makes revocation on logout/rotation actually work.
- **`OTP_DELIVERY_MODE` must not be `log` in production** - `application-prod.yml` sets it to `sms`, but that requires a real provider integration to actually be wired in before going live.
