# AzureQL dependency upgrade — 2026-08

## Applied stable versions

| Area | Before | After |
|---|---:|---:|
| Android Gradle Plugin | 9.1.1 | 9.2.1 |
| Kotlin / Compose compiler plugin | 2.2.21 | 2.4.10 |
| Compose BOM | 2025.04.01 | 2026.08.00 |
| MCP Kotlin SDK | 0.10.0 | 0.15.0 |
| Ktor | 3.2.3 | 3.5.2 |
| kotlinx.coroutines | 1.10.2 | 1.11.0 |
| kotlinx.serialization | 1.9.0 | 1.11.0 |
| AndroidX Core / SplashScreen | 1.15.0 / 1.0.1 | 1.19.0 / 1.2.0 |
| AndroidX Lifecycle / Activity | 2.8.7 / 1.9.3 | 2.11.0 / 1.13.0 |
| Navigation Compose / DataStore | 2.8.5 / 1.1.1 | 2.10.0 / 1.2.1 |
| MockK / SLF4J | 1.13.13 / 2.0.17 | 1.14.11 / 2.0.18 |

## Already current

- KSP 2.3.9 (KSP2)
- Dagger/Hilt 2.60.1
- AndroidX Hilt 1.3.0
- Room 2.8.4
- WorkManager 2.11.2
- Gradle Wrapper 9.5.0
- compileSdk / targetSdk 37
- JDK 17 bytecode target

## Deliberately deferred

Retrofit 3, OkHttp 5 and Coil 3 are independent major migrations. They are not required by MCP SDK 0.15 or Kotlin 2.4, so they remain on their existing major lines for this compatibility pass. They should be upgraded separately with API migration and focused network/image tests.

## Required validation

- MCP SDK client initialization, tool discovery and 100 sequential calls.
- MCP service stop releases its loopback port.
- Feature-level unit tests for MCP settings and foreground-service state.
- Debug assembly, all debug unit tests and Android lint.
- Compose Android test source compilation.
- Release assembly and signing configuration check.
- Real-device MCP connection after the APK is installed.
