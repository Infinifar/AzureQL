# GitLab 日常构建说明

## 仓库分工

- GitLab `private/azureql` 的 `main` 分支用于日常提交验证、Debug APK 和 Release 变体构建。
- GitHub `Infinifar/AzureQL` 保留为正式签名 APK 与公开 GitHub Release 的发布入口。
- GitLab 原有 `master` 是独立 Android 模板历史，保留为回退分支；当前 AzureQL 代码从 `main` 开始，不对
  `master` 做强制覆盖或历史改写。

## Pipeline 产物

`verify_debug` 在每次 push/Merge Request 执行：

- Debug APK 构建；
- 全量 `testDebugUnitTest`；
- Android Lint；
- Backup、Log、Settings 三组 Compose AndroidTest 源码编译；
- Debug APK、JUnit XML、测试报告和 Lint HTML 保存 14 天。

`build_release` 在默认分支和 tag 自动执行，其他分支可手动执行：

- 未配置签名变量时生成经过 Release/R8 流程的未签名测试产物；
- 四个签名变量全部存在时生成签名 APK 并使用 `apksigner` 验证；
- Release APK 保存 30 天，但 GitLab Pipeline 不创建公开 Release，也不替代 GitHub 正式发布。

## 可选签名变量

如确需在 GitLab 生成可安装的签名 Release，在项目 CI/CD Variables 中一次性配置以下四项，并设为 Masked；
受保护分支使用时同时设为 Protected：

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

四项只配置一部分会让任务安全失败。Keystore 在 Runner 临时工作区生成，仓库通过 `.gitignore` 禁止提交
`release.keystore`。正式公开构建仍应使用 GitHub Actions 的签名与发布流程。

## 本机 Remote

远程地址只保存不含账号和 Token 的 HTTPS URL：

```text
gitlab  https://gitlab.infinifar.top/private/azureql.git
origin  https://github.com/Infinifar/AzureQL.git
```

认证应交给系统凭据管理器、短时 HTTP Header 或 CI 变量，不把访问令牌拼进 remote URL、脚本、文档或日志。
