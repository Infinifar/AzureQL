# AzureQL MCP compatibility

This document records the Android MCP technical preview and its verified dependency baseline. It is not a promise that the preview is ready for unrestricted automation.

## Selected baseline

| Component | AzureQL baseline | Reason |
|---|---:|---|
| Android | 12+ / API 31+ | Existing AzureQL minimum |
| compileSdk / targetSdk | 37 | Existing project target |
| AGP / Gradle | 9.2.1 / 9.5.0 | Supports API 37 and the current stable Compose generation |
| Kotlin | 2.4.10 | Latest Kotlin 2.4 bug-fix release |
| MCP Kotlin SDK | 0.15.0 | Current official SDK release |
| Ktor | 3.5.2 | Latest 3.5 bug-fix release; SDK 0.15.0 was published against 3.5.1 |
| Coroutines / Serialization | 1.11.0 / 1.11.0 | Aligned with the SDK 0.15 generation |
| Server engine | Netty | Supported by Ktor's embedded-server API and already proven on device |
| Transport | Stateless Streamable HTTP at `/mcp` | Official SDK transport without persistent server-side MCP sessions |
| Bind address | `127.0.0.1` only | Enforced by `McpServerConfig` |

The protocol detail remains behind `McpServerEngine`, so QingLong repositories and Compose UI do not depend on MCP transport types.

## Why stateless Streamable HTTP?

AzureQL uses application-level persisted Operations for Phase 2 confirmation/idempotency, so it still does not depend on protocol-level resumable sessions. SDK 0.15.0 has a confirmed upstream issue in the stateful helper where standalone GET/SSE connections can retain sockets and coroutines. AzureQL therefore uses `mcpStatelessStreamableHttp`, which creates and closes a protocol session per request and rejects standalone GET requests.

SDK 0.15 installs the required MCP content negotiation inside its Ktor helper. AzureQL no longer installs a second `ContentNegotiation` plugin or couples the engine to the SDK's internal JSON instance.

## Phase 2 surface

- User-started `specialUse` foreground service.
- `START_NOT_STICKY`; the service does not silently restart after process death.
- Loopback-only HTTP endpoint: `http://127.0.0.1:18765/mcp`.
- Per-Agent 256-bit bearer Token; only its SHA-256 hash is persisted.
- Default read scopes, current-account binding, four-request concurrency cap and local bounded audit.
- Ten read-only tools for server status, tasks, scripts, dependencies, masked environment metadata and bounded logs.
- Thirteen Phase 2 tools: one owner-scoped Operation query plus twelve controlled script/task/dependency/environment mutations.
- Every mutation requires per-Agent Phase 2 Scope, a stable idempotency key, a phone confirmation and an exact retry with the issued Operation ID.
- Pending Operations expire after ten minutes; completed results are retained for 24 hours and replayed without a second QingLong call.
- Script update uses a required SHA-256 precondition; environment values never enter Operation records, responses or audit.
- Lists are capped at 100; script output is capped at 64 KiB and rejects unsafe relative paths.
- No QingLong token, password, environment value, certificate or private key access.
- No LAN binding, public network access, arbitrary shell or destructive tools.

## Desktop test connection

```bash
adb forward tcp:18765 tcp:18765
```

Then connect an MCP client or MCP Inspector to:

```text
http://127.0.0.1:18765/mcp
```

The client must send `Authorization: Bearer <Agent Token>` on every Streamable HTTP request. A direct browser GET without authorization is rejected before protocol handling.

## Validation ledger

The following gates must be kept current as the SDK 0.15 migration is tested:

- [x] `:core:mcp:testDebugUnitTest`
- [x] `:feature:mcp:testDebugUnitTest`
- [x] Android manifest and resource merge
- [x] Debug APK assembly, all debug unit tests and `lintDebug`
- [x] CI-parity Compose Android test source compilation
- [ ] Android 12+ device: start/stop and notification
- [ ] MCP client through `adb forward`
- [x] Official SDK client discovers and calls the authenticated per-Agent tool surface
- [x] Origin rejection, account-switch isolation, concurrency limit, path traversal, UTF-8 truncation and environment-value redaction tests
- [x] Dependency exact-match, accessible-log-tree validation, log line/byte tail limits and Agent rename tests
- [x] Operation confirmation, denial, owner isolation, idempotency conflict, result replay and interrupted-process recovery tests
- [x] Port released after engine stop in the JVM integration test
- [x] Release APK assembly with R8

## Remaining gates

Before publishing Phase 2, run the Android device matrix for all controlled tools, notification delivery, phone approval/denial, exact retry and account-switch denial. Before LAN mode is visible, TLS and explicit risk confirmation are required.
