# AGENTS Guidelines for QingLong App Android

Kotlin + Jetpack Compose + Material 3 重构版青龙面板客户端。

## 1. Project Specifications
- **Minimum SDK:** 31 (Android 12)
- **Target SDK:** 35
- **Language:** Kotlin 2.0+
- **Build System:** Gradle Kotlin DSL + Convention Plugins

## 2. Architecture & Design Patterns
- **Presentation Layer:** Jetpack Compose + ViewModel + StateFlow/SharedFlow
- **Domain Layer:** UseCases (纯 Kotlin, 无 Android 依赖)
- **Data Layer:** Repository Pattern (Retrofit + DataStore + Room)
- **Dependency Injection:** Hilt

## 3. Asynchronous Programming
- Kotlin Coroutines + Flow 独占
- 注入 Dispatchers, 不硬编码 Dispatchers.IO

## 4. UI Framework
- Jetpack Compose + Material 3 (Material You)
- 类型安全 Navigation Compose (@Serializable routes)

## 5. Testing Philosophy
- Unit: JUnit + MockK + Turbine
- Integration: Hilt + MockWebServer
- UI: Compose Test Rule

## 6. Module Structure
- `:app` — Application entry, DI setup
- `:core:model` — Domain models (Pure Kotlin)
- `:core:data` — Repositories, DataSources
- `:core:domain` — UseCases, Repository interfaces
- `:core:ui` — Shared Composables, Theme
- `:feature:login` — Login + Two-Factor Auth
- `:feature:backup` — QingLong server backup and restore
