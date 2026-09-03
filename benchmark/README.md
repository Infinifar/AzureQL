# AzureQL Macrobenchmark 与 Baseline Profile

本模块固定 `docs/IMPROVEMENT_PLAN.md` 第十五轮中的启动、导航、列表、大日志、大脚本和订阅轮询场景。
性能对比必须在同一台物理设备、相同系统与相同 `CompilationMode` 下运行；模拟器只用于编译和流程验证。

## 当前实测状态（2026-09-03）

Motorola XT2551-3（Android 16 / API 36）已完成全部当前授权场景。交互场景固定使用相同编译模式；
CPU 数字为帧耗时 P50/P95/P99，overrun 为 P99。

| 场景 | CPU P50/P95/P99 | Frame overrun P99 | 结果 |
|---|---:|---:|---|
| 冷启动 | OFF `385.7 ms` / ON `296.6 ms`（中位数） | — | Baseline Profile 缩短约 `23.1%` |
| 连续切 Tab | `2.2/8.1/15.8 ms` | `3.2 ms` | 通过 |
| 500 项任务 | `1.8/3.1/3.5 ms` | `-9.7 ms` | 通过 |
| 大型脚本目录 | `4.6/8.3/12.6 ms` | `0.2 ms` | 通过 |
| 1/5/20 MiB 日志 | `1.4/3.0/13.5`、`1.4/2.9/13.0`、`1.2/2.6/10.2 ms` | `5.4/1.5/7.3 ms` | 通过 |
| 10 MiB 脚本 | `1.2/4.3/12.1 ms` | `4.7 ms` | 通过；修复前为 `175.0 ms` |
| 50 MiB 脚本 | `1.0/2.9/10.6 ms` | `4.5 ms` | 通过 |
| 订阅日志 60 秒 | `1.4/3.9/11.2 ms` | `-0.5 ms` | 通过 |

10 MiB 长单行脚本的 Perfetto Trace 将旧尖峰定位到 Compose 文本测量和 `StaticLayout`。分页预览关闭
软换行、增加横向滚动，并把每页从 32768 调整为 8192 字符后，P99 overrun 约降低 `97.3%`。
当前结果仍包含 `4.5～7.3 ms` 的小幅尾部 overrun，因此只判定灾难性尖峰已消除，不宣称完全无卡顿。

1000 项任务需要创建更多服务器夹具，当前未获该扩容授权；500 项结果已满足当前版本回归基线，
1000 项保留为容量研究，不阻断当前发布。

运行前在 AzureQL 中保留一个已登录的测试账户，并在青龙服务器准备 500/1000 项任务、指定大小的日志和
脚本。大型内容场景通过 instrumentation arguments 指定唯一名称：

```text
azureql.benchmark.taskCount=500 或 1000
azureql.benchmark.scriptDirectory=<大型目录名>
azureql.benchmark.log1MiB=<约 1 MiB 日志文件名>
azureql.benchmark.log5MiB=<约 5 MiB 日志文件名>
azureql.benchmark.log20MiB=<约 20 MiB 日志文件名>
azureql.benchmark.script10MiB=<约 10 MiB 脚本文件名>
azureql.benchmark.script10MiBParent=<可选父目录路径，如 benchmark/files>
azureql.benchmark.script50MiB=<约 50 MiB 脚本文件名>
azureql.benchmark.script50MiBParent=<可选父目录路径>
azureql.benchmark.subscription=<运行中订阅名称>
```

目录路径使用 `/` 分隔；例如 `fixtures/large/tree` 会依次展开三个目录，并把最终目录作为被测对象。
文件名必须与 AzureQL 列表中显示的名称完全一致。测试配置只保存夹具名称，不保存青龙地址、Token 或密码。

## 一键测试

所有场景均提供独立测试方法、只读预检和 PowerShell 执行器。连接已授权实机并准备固定夹具后执行：

```powershell
Copy-Item benchmark/benchmark-fixtures.example.json benchmark/benchmark-fixtures.local.json
# 编辑 local.json，填入测试青龙中的真实名称
./benchmark/scripts/run_macrobenchmarks.ps1 -Scenario all
```

默认流程为：

1. 检查仅连接一台 Android 12+ 授权设备。
2. 构建并用 `adb install -r -t` 覆盖安装两个 APK。
3. 只读预检 500 项分页、脚本目录/文件、日志和订阅是否能从真实 UI 定位。
4. 独立执行启动、切 Tab、任务、目录、三个日志尺寸、两个脚本尺寸和订阅轮询。
5. 每个场景结束后立即把 instrumentation 文本、benchmark JSON 和 Perfetto Trace 拉到
   `artifacts/macrobenchmark/batch-<时间>/device-output/<场景>/`，并生成该场景的
   `artifact-manifest.json`；不得等全部场景结束后再统一拉取，否则后续 instrumentation 可能覆盖前一场景产物。

脚本不会调用 `adb uninstall`、`pm clear` 或 Gradle `connectedCheck`，因此 AzureQL、benchmark 包和
已登录账户都会保留。常用变体：

```powershell
# 只验证所有夹具是否可定位，不下载大型正文
./benchmark/scripts/run_macrobenchmarks.ps1 -Scenario all -PreflightOnly -SkipBuild

# 先跑不依赖服务器大文件的场景
./benchmark/scripts/run_macrobenchmarks.ps1 -Scenario startup,tabs

# 单独复测一个尺寸，复用已经构建和安装的 APK
./benchmark/scripts/run_macrobenchmarks.ps1 -Scenario script-50 -SkipBuild -SkipInstall

# 指定配置或输出目录
./benchmark/scripts/run_macrobenchmarks.ps1 -Scenario log-20 `
  -ConfigPath benchmark/my-fixtures.json `
  -OutputDirectory artifacts/macrobenchmark/log-20-repeat
```

覆盖安装若提示签名不一致，执行器会直接失败，不会通过卸载主包来绕过，因为卸载会丢失登录状态。

### 独立场景与测量边界

| 场景 | 测试方法 | 正式计时内容 |
|---|---|---|
| 启动 | `StartupBenchmark` | Baseline Profile OFF/ON 冷启动 |
| 主导航 | `switchPrimaryTabs` | 首页、任务、环境、设置、依赖、脚本切换 |
| 500/1000 任务 | `scrollTaskListWith500Or1000Items` | 全部页在 setup 加载后连续滚动 |
| 大脚本目录 | `expandAndScrollLargeScriptDirectory` | 展开最终目录并滚动 |
| 1/5/20 MiB 日志 | `openOneMiBLog` / `openFiveMiBLog` / `openTwentyMiBLog` | 打开、载入 256 KiB 窗口并滚动 |
| 10/50 MiB 脚本 | `openAndPageTenMiBScript` / `openAndPageFiftyMiBScript` | 下载本地缓存、解析首段并翻三段 |
| 订阅日志 | `pollSubscriptionLogForSixtySeconds` | 初次加载完成后保持 60 秒轮询 |

日志和脚本按尺寸拆开，避免较小对象的结果掩盖 20/50 MiB 场景。列表查找、父目录展开和页面导航均在
不计时的 `setupBlock` 中；计时区间不包含“为找到夹具而滚动”的噪声。订阅必须处于运行中，否则虽然
日志页可打开，但不会形成有效的 60 秒轮询基线。

基础命令：

```text
gradlew :benchmark:connectedCheck
gradlew :app:generateBaselineProfile
```

## Android Studio 运行

项目已保存两个共享 Run/Debug Configuration：

- `Macrobenchmark - Startup`：在 `AzureQL.benchmark` 模块运行
  `com.autopanel.benchmark.StartupBenchmark`，不需要额外测试数据参数。
- `Baseline Profile - Generate`：在 `AzureQL.app` 模块为所有可发布变体生成 Baseline Profile。

正式测量请连接 Android 12+ 物理设备并在工具栏选择该设备，不使用模拟器记录最终数据。设备上需保留一个已登录的
AzureQL 测试账户。选择上述配置后点击 Run；Macrobenchmark 指标和 System Trace 可在 Android Studio 的测试结果中打开。

运行 `InteractionBenchmark` 时，建议优先使用上述一键执行器。也可在 **Run > Edit Configurations >
Android Instrumented Tests > Instrumentation arguments** 中加入上方测试数据参数，或从命令行传入：

```text
gradlew :benchmark:connectedCheck \
  -Pandroid.testInstrumentationRunnerArguments.azureql.benchmark.taskCount=500 \
  -Pandroid.testInstrumentationRunnerArguments.azureql.benchmark.scriptDirectory=<大型目录名> \
  -Pandroid.testInstrumentationRunnerArguments.azureql.benchmark.log1MiB=<约1MiB日志名> \
  -Pandroid.testInstrumentationRunnerArguments.azureql.benchmark.log5MiB=<约5MiB日志名> \
  -Pandroid.testInstrumentationRunnerArguments.azureql.benchmark.log20MiB=<约20MiB日志名> \
  -Pandroid.testInstrumentationRunnerArguments.azureql.benchmark.script10MiB=<约10MiB脚本名> \
  -Pandroid.testInstrumentationRunnerArguments.azureql.benchmark.script10MiBParent=<可选父目录> \
  -Pandroid.testInstrumentationRunnerArguments.azureql.benchmark.script50MiB=<约50MiB脚本名> \
  -Pandroid.testInstrumentationRunnerArguments.azureql.benchmark.script50MiBParent=<可选父目录> \
  -Pandroid.testInstrumentationRunnerArguments.azureql.benchmark.subscription=<运行中订阅名>
```

对比前后版本时固定同一台设备、Android 版本、测试数据、供电与温度条件以及 `CompilationMode`。先单独比较架构修改，
再独立比较 Baseline Profile OFF/ON，避免把两类收益混在一起。

## 测量边界

- `StartupBenchmark` 使用 `StartupMode.COLD`，测量冷启动 TTID/TTFD。
- `InteractionBenchmark` 在不计时的 `setupBlock` 中启动应用、等待页面稳定并回到首页；
  `FrameTimingMetric` 只覆盖 Tab 切换、滚动、打开文件等 ready-state 用户操作。
- 不得把 `startActivityAndWait()` 重新放回交互场景的正式计时区间，否则启动帧会污染导航结果。

## 保留已安装 APP 和登录状态

部分 Gradle/Android Studio instrumented-test 流程会在收尾卸载测试包。需要连续使用已登录账户准备
多组实机数据时，可先构建并覆盖安装 benchmark APK，再直接运行 instrumentation。`-r` 会保留包数据，
该流程结束后也不会主动卸载 AzureQL：

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
./gradlew :app:assembleBenchmarkRelease :benchmark:assembleBenchmarkRelease
& $adb install -r -t app/build/outputs/apk/benchmarkRelease/app-benchmarkRelease.apk
& $adb install -r -t benchmark/build/outputs/apk/benchmarkRelease/benchmark-benchmarkRelease.apk
& $adb shell am instrument -w -r `
  -e class 'com.autopanel.benchmark.InteractionBenchmark#switchPrimaryTabs' `
  com.autopanel.benchmark/androidx.test.runner.AndroidJUnitRunner
```

如果 AzureQL 主包已经安装且已登录，只修改了 benchmark 代码，可只覆盖安装 benchmark APK。测试输出位于：

```text
/sdcard/Android/media/com.autopanel.benchmark/
```

Session/网络初始化可直接在 Perfetto Trace 中按以下切片名称计数：

```text
AzureQL:Session.load
AzureQL:Session.reload
AzureQL:Credentials.read
AzureQL:Keystore.decrypt
AzureQL:ApiClient.build
AzureQL:TLS.material
```

冷进程应各出现一轮必要初始化；交互场景的正式计时区间中应全部为 0。示例 Trace Processor SQL：

```sql
SELECT name, COUNT(*) AS count, ROUND(SUM(dur) / 1e6, 3) AS total_ms
FROM slice
WHERE name LIKE 'AzureQL:%'
GROUP BY name
ORDER BY name;
```

### `DROP_SHADER_CACHE` 返回 0

部分 OEM 系统会忽略刚安装且仍处于 `notLaunched` 状态应用的 ProfileInstaller benchmark 广播。本模块会在计时开始前
启动一次目标 APK、验证 receiver 已返回 14，再 force-stop，以解除该状态；这次准备不计入启动指标，后续每轮仍按
`StartupMode.COLD` 测量。
实机诊断时可执行：

```text
adb shell am broadcast -a androidx.profileinstaller.action.BENCHMARK_OPERATION \
  -e EXTRA_BENCHMARK_OPERATION DROP_SHADER_CACHE \
  com.autopanel.app/androidx.profileinstaller.ProfileInstallReceiver
```

正常结果为 `Broadcast completed: result=14`。项目显式使用 `androidx.profileinstaller:profileinstaller:1.4.1`，并在
`benchmarkRelease` 的 Manifest 与 R8 输出中保留 `ProfileInstallReceiver`。

`StartupBenchmark` 分别输出 Baseline Profile OFF/ON；其余交互场景固定使用禁用 Baseline Profile 的
相同部分编译模式，用于比较架构修改前后差异。生成后的生产 Profile 应覆盖启动、底部导航和脚本列表滚动。

## 500 项任务夹具与分页预加载

任务列表每页固定加载 50 项。`scrollTaskListWith500Or1000Items` 不会把首批 50 项冒充 500 项：每次
warmup/正式迭代都会在不计时的 `setupBlock` 中滑到列表底部并点击 9 次“加载更多”，待 500 项全部进入
同一个 `LazyColumn` 后回到顶部；`FrameTimingMetric` 随后只覆盖连续滚动。

需要临时补足 500 项时，先取得用户对当前青龙测试账户的明确授权，再使用仅存在于
`benchmarkRelease` 的签名保护入口。正式 release APK 不包含该 Activity：

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb shell am instrument -w -r `
  -e class 'com.autopanel.benchmark.TaskFixtureController#seedTasksToFiveHundred' `
  com.autopanel.benchmark/androidx.test.runner.AndroidJUnitRunner

& $adb shell am instrument -w -r `
  -e class 'com.autopanel.benchmark.InteractionBenchmark#scrollTaskListWith500Or1000Items' `
  -e azureql.benchmark.taskCount 500 `
  com.autopanel.benchmark/androidx.test.runner.AndroidJUnitRunner

# 即使性能测试失败，也必须执行清理。
& $adb shell am instrument -w -r `
  -e class 'com.autopanel.benchmark.TaskFixtureController#cleanupTasks' `
  com.autopanel.benchmark/androidx.test.runner.AndroidJUnitRunner
```

夹具以唯一 `AZUREQL_BENCH_20260902_` 前缀创建年度定时任务，创建后批量禁用，并把精确任务 ID 写入
应用私有文件。清理取“已记录 ID + 当前服务器前缀匹配 ID”的并集，删除后再次查询确认前缀结果为空；
不得用模糊名称删除用户任务。当前控制器只获授权补足到 500 项；1000 项需要重新获得授权并扩展夹具目标。

2026-09-02 Motorola XT2551-3 实测从 298 项补足 202 条禁用夹具，完成后清理结果为
`total=298, deleted=202`。有效的 500 项滚动共 5 轮、1727 帧，0 帧 missed deadline；CPU 帧耗时
P50/P90/P95/P99 为 `1.89/3.13/3.47/5.07 ms`，frame overrun P50/P90/P95/P99 为
`-12.48/-10.33/-9.11/-6.94 ms`，最大 overrun 仍为 `-2.85 ms`。未点击“加载更多”的首轮结果无效，
不得作为 500 项基线。
