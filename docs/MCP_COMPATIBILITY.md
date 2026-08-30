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

Phase 0 exposes only a read-only connectivity tool and does not require resumable server sessions. SDK 0.15.0 has a confirmed upstream issue in the stateful helper where standalone GET/SSE connections can retain sockets and coroutines. AzureQL therefore uses `mcpStatelessStreamableHttp`, which creates and closes a protocol session per request and rejects standalone GET requests.

SDK 0.15 installs the required MCP content negotiation inside its Ktor helper. AzureQL no longer installs a second `ContentNegotiation` plugin or couples the engine to the SDK's internal JSON instance.

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

The client must use Streamable HTTP POST requests. A direct browser GET is expected to return HTTP 405 in stateless mode.

## Validation ledger

The following gates must be kept current as the SDK 0.15 migration is tested:

- [x] `:core:mcp:testDebugUnitTest`
- [x] `:feature:mcp:testDebugUnitTest`
- [x] Android manifest and resource merge
- [x] Debug APK assembly, all debug unit tests and `lintDebug`
- [x] CI-parity Compose Android test source compilation
- [ ] Android 12+ device: start/stop and notification
- [ ] MCP client through `adb forward`
- [x] 100 sequential `hello` calls through the official SDK client
- [x] Port released after engine stop in the JVM integration test
- [x] Release APK assembly with R8

## Upgrade gates

Before exposing QingLong read tools, Phase 1 must add per-Agent authentication, scopes, request limits, Origin/Host validation and audit logging. Before LAN mode is visible, TLS and explicit risk confirmation are required.
