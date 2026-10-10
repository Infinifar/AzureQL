# AzureQL MCP compatibility

Last reviewed: 2026-10-10

This document records the verified MCP dependency and transport baseline. AzureQL does not depend on unreleased SDK snapshots.

## Current baseline

| Component | AzureQL baseline | Notes |
|---|---:|---|
| Android | 12+ / API 31+ | Application minimum |
| compileSdk / targetSdk | 37 | Current application target |
| Kotlin | 2.4.10 | Project toolchain |
| MCP Kotlin SDK | 0.15.0 | Latest official stable release as of this review |
| Ktor | 3.5.2 | Compatible with the SDK 0.15 generation |
| Server engine | CIO | Verified on Android and used instead of Netty native transports |
| Transport | Stateless Streamable HTTP at `/mcp` | One protocol session per request |
| Network | Loopback by default; optional trusted-LAN binding | LAN mode is explicit and has no TLS yet |

MCP protocol types remain behind `McpServerEngine`; Compose screens and QingLong repositories do not depend on SDK transport types.

## Why stateless Streamable HTTP?

AzureQL persists its own Operations for confirmation, idempotency and result replay, so it does not require a resumable MCP server session. `mcpStatelessStreamableHttp` also avoids the unresolved stateful standalone GET/SSE lifecycle leak tracked in [kotlin-sdk#922](https://github.com/modelcontextprotocol/kotlin-sdk/issues/922).

SDK 0.15 closes the per-request stateless session and installs its own MCP JSON negotiation. AzureQL therefore does not add a second `ContentNegotiation` plugin or access the SDK's internal JSON instance.

## SDK update status

- 0.15.0 remains the latest stable Kotlin SDK release.
- The redesigned `io.modelcontextprotocol/tasks` extension for the 2026-07-28 protocol is not yet available in a stable Kotlin SDK; implementation is tracked in [kotlin-sdk#817](https://github.com/modelcontextprotocol/kotlin-sdk/issues/817).
- The related extension framework and new stateless protocol work are also still tracked upstream in [#804](https://github.com/modelcontextprotocol/kotlin-sdk/issues/804) and [#815](https://github.com/modelcontextprotocol/kotlin-sdk/issues/815).
- AzureQL will keep its application-level Operation model until those capabilities reach a stable SDK release and client interoperability can be tested.

## Current AzureQL surface

- User-started `specialUse` foreground service with `START_NOT_STICKY`.
- Per-Agent 256-bit bearer Token; only its SHA-256 hash is persisted.
- Agent binding to the current QingLong account, scoped permissions, rate limits and bounded local audit.
- Ten bounded data-read tools, one owner-only Operation query and twelve controlled mutation/execution tools.
- Per-operation confirmation by default. An Agent with controlled access may separately enable silent approval after device authentication; all scopes, limits, serialization, idempotency, conflict checks and audit remain active.
- Loopback access by default. Trusted-LAN mode binds all interfaces but accepts only validated device interface hosts and matching origins.
- No arbitrary shell, arbitrary HTTP proxy, unmodelled delete operations, config writes, backup restore or QingLong credential access.

## Connection

The local endpoint is:

```text
http://127.0.0.1:18765/mcp
```

Every request must include `Authorization: Bearer <Agent Token>`. Direct browser GET requests are rejected before tool handling; authenticated GET returns `405` because the endpoint accepts JSON-RPC POST only.

For desktop testing, a local port forward may be used:

```bash
adb forward tcp:18765 tcp:18765
```

Trusted-LAN mode exposes the same path on the IPv4/IPv6 addresses shown by the app. It uses cleartext HTTP bearer authentication and must not be exposed to public or untrusted networks.

## Validation ledger

- [x] `:core:mcp:testDebugUnitTest` and `:feature:mcp:testDebugUnitTest`
- [x] Android manifest/resource merge, all Debug unit tests and `lintDebug`
- [x] Compose Android test-source compilation and R8 Release assembly
- [x] Android 16 foreground-service start/stop and notification flow
- [x] Official SDK client authentication, initialize, `tools/list` and tool calls through a local port forward
- [x] Read and controlled-tool device acceptance, approval/denial, idempotent replay and secret redaction
- [x] Trusted-LAN reachability with authentication still enforced
- [x] Origin rejection, concurrency/rate limits, path traversal, UTF-8 limits and account-isolation automation
- [ ] Physical-device account switch using the previous Agent Token and approved Operation
- [ ] Physical-device ten-minute Operation expiry wait
- [ ] TLS or an equivalent secure tunnel for LAN transport

The remaining device checks do not block the existing loopback MCP feature. The Tasks extension and secure LAN transport remain separate future work.
