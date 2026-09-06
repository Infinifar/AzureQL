# AzureQL v2.3.2 — What’s Changed / 更新内容

## 中文

### 连续滑动导航

- 首页、任务、脚本、订阅、环境和设置现在可以连续左右滑动切换，页签与底部导航保持同步。
- 脚本与订阅继续共享同一个脚本管理状态；到达内部页面边界后，滑动会自然交接到相邻主页面。
- 从任务编辑入口打开脚本时，仍会携带脚本路径并自动切换到脚本页。

### 脚本与订阅管理

- 脚本根目录及文件夹菜单新增“新建文件夹”，支持嵌套目录，并拦截非法名称、路径分隔符和同名冲突。
- 文件与文件夹的三点菜单新增“复制路径”，保留文件长按复制路径操作。
- 订阅卡片不再显示或占用仓库链接区域，状态和定时规则收进紧凑内容区。

### 界面布局

- 任务与环境变量搜索框缩短，搜索按钮进一步左移，减少右侧拥挤。
- 任务编辑弹窗改为与环境变量编辑相同的 Material 3 标准宽度；实例模式使用等宽单行布局，避免文字换行错位。
- 环境变量卡片与订阅卡片统一紧凑内边距；备注不再额外撑高操作行，启停继续使用尺寸更小的原生开关。

### 实时日志修复

- 修复任务结束时完整日志快照被误判为增量内容、造成日志重复显示的问题。
- 仅当青龙明确返回游标时追加日志；无游标响应作为完整快照替换，同时保留终态最后一次补拉，避免遗漏末尾输出。

### 验证与兼容性

- 新增脚本文件夹、路径复制、环境变量开关和任务终态日志快照回归测试。
- 本地发布验证通过 44 个测试套件、258 项单元测试（0 失败、0 错误、0 跳过），Android Lint、备份/环境变量/脚本 Compose 测试源码编译及 Release APK 构建全部成功。
- Motorola XT2551-3（Android 16）通过环境变量卡片 4 项 UI 测试、正反向连续滑动导航及底栏跳转冒烟；任务终态日志由用户实机确认正常。
- 支持 Android 12（API 31）及以上，青龙 v2.17 及以上。

## English

### Continuous swipe navigation

- Home, Tasks, Scripts, Subscriptions, Variables, and Settings can now be traversed continuously with horizontal swipes, synchronized with tabs and bottom navigation.
- Scripts and Subscriptions keep a single shared feature state; swipes hand off naturally to adjacent top-level pages at the inner pager boundaries.
- Opening a script from a task still carries its path and switches directly to the Scripts page.

### Script and subscription management

- Added folder creation from the script root and directory menus, including nested-directory support and validation for invalid names, path separators, and duplicate entries.
- Added Copy path to the overflow menu for both files and folders while retaining long-press copy for files.
- Subscription cards no longer display or reserve space for repository URLs; status and schedule are presented in a compact content area.

### UI refinements

- Shortened the Tasks and Variables search fields and moved the submit icon further left to reduce right-edge crowding.
- Task editing now uses the same standard Material 3 dialog width as variable editing. Equal-width, single-line instance controls prevent wrapping and alignment shifts.
- Variable and subscription cards now share compact padding. Notes no longer increase the action-row height, and enable/disable remains a smaller native switch.

### Live-log correctness

- Fixed completed-task log snapshots being mistaken for incremental chunks and appended repeatedly.
- Logs are appended only when QingLong returns an explicit cursor. Cursorless responses replace the current snapshot, while the final post-completion fetch remains in place so trailing output is not lost.

### Validation and compatibility

- Added regression coverage for script folders, path copying, the variable toggle, and terminal task-log snapshots.
- Local release validation passed 44 suites and 258 unit tests with zero failures, errors, or skips. Android Lint, Compose test-source compilation for Backup/Variables/Scripts, and the Release APK build also completed successfully.
- A Motorola XT2551-3 running Android 16 passed all four variable-card UI tests, forward/reverse continuous-swipe navigation, and bottom-navigation smoke checks. Terminal task-log behavior was also confirmed on device by the user.
- Requires Android 12 (API 31) or later and QingLong v2.17 or later.

## Third-party software / 第三方组件

Sora Editor and the selected Monarch language definitions remain under their respective upstream licenses. Both permit commercial use when their license conditions are followed. See [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).

Sora Editor 与所选 Monarch 语法定义继续适用各自的上游许可证；遵守许可证条件时均允许商业使用。详见 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)。
