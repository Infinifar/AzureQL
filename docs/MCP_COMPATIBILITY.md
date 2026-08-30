# AzureQL MCP compatibility

This document records the Phase 0 Android MCP spike. It describes observed compatibility, not a promise that the technical preview is ready for unrestricted automation.

## Selected baseline

| Component | AzureQL Phase 0 | Reason |
|---|---:|---|
| Android | 12+ / API 31+ | Existing AzureQL minimum |
| Kotlin | 2.2.21 | Existing project toolchain |
| MCP Kotlin SDK | 0.10.0 | Highest official release built with Kotlin 2.2.21; its published API/language level remains compatible |
| Ktor | 3.2.3 | Version used by MCP Kotlin SDK 0.10.0 |
| Server engine | Netty | Proven in Android MCP projects and supported by Ktor's embedded server API |
| Transport | Streamable HTTP at `/mcp` | Official SDK transport |
| Bind address | `127.0.0.1` only | Enforced by `McpServerConfig` |

The current SDK baseline implements the pre-2026 session-based Streamable HTTP lifecycle. AzureQL keeps that protocol detail behind `McpServerEngine` so a future adapter can implement MCP `2026-07-28` without changing tools or QingLong repositories.

SDK 0.10.0 does not yet auto-install Ktor JSON content negotiation. AzureQL therefore installs `ContentNegotiation` explicitly with the SDK's `McpJson`; omitting it makes the first initialization response fail with HTTP 406.

## Why not MCP Kotlin SDK 0.15.0 yet?

SDK 0.15.0 moved to Kotlin 2.4.0, Ktor 3.5.1, coroutines 1.11.0 and serialization 1.11.0. Pulling it into AzureQL would turn the protocol spike into another whole-project toolchain upgrade. Phase 0 therefore pins 0.10.0 and treats a later SDK upgrade as a separate compatibility gate.

The later migration remains contained: `feature:mcp` depends on the semantic `McpServerEngine` API rather than SDK transport types. Upgrade 0.15.x on a dedicated branch, align Kotlin/Compose/KSP/Hilt and Ktor first, then rerun the protocol integration and Android lifecycle gates. Expected effort is medium rather than a rewrite.

## Phase 0 surface

- User-started `specialUse` foreground service.
- `START_NOT_STICKY`; the service does not silently restart after process death.
- Loopback-only HTTP endpoint: `http://127.0.0.1:18765/mcp`.
- One read-only `hello` tool.
- No QingLong repository access.
- No QingLong token, password, environment value, certificate or private key access.
- No LAN binding, public network access, arbitrary shell or destructive tools.
- No Agent authentication yet. Until Phase 1 authentication lands, this endpoint remains a technical preview intended for loopback/ADB testing only.

## Desktop test connection

```bash
adb forward tcp:18765 tcp:18765
```

Then connect an MCP client or MCP Inspector to:

```text
http://127.0.0.1:18765/mcp
```

## Validation ledger

The following gates must be kept current as Phase 0 is tested:

- [x] `:core:mcp:testDebugUnitTest`
- [x] `:feature:mcp:testDebugUnitTest`
- [x] Android manifest and resource merge
- [x] Debug APK assembly, all debug unit tests and `lintDebug`
- [x] CI-parity Compose Android test source compilation
- [ ] Android 12+ device: start/stop and notification
- [ ] MCP Inspector through `adb forward`
- [x] 100 sequential `hello` calls through the official SDK client
- [x] Port released after engine stop in the JVM integration test

## Upgrade gates

Before exposing QingLong read tools, Phase 1 must add per-Agent authentication, scopes, request limits, Origin/Host validation and audit logging. Before LAN mode is visible, TLS and explicit risk confirmation are required.
