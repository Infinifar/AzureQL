# 本机测试环境复用说明

本文只记录本项目在当前 Windows 开发机上已经确认过的工具路径和临时目录绕行方式，不包含账号、证书、
Token 或签名密钥。

## ADB

当前终端的 `PATH` 可能找不到 `adb`。直接使用 Android SDK 内的可执行文件：

```powershell
& 'C:\Users\wucha\AppData\Local\Android\Sdk\platform-tools\adb.exe' devices -l
& 'C:\Users\wucha\AppData\Local\Android\Sdk\platform-tools\adb.exe' install -r '<apk-absolute-path>'
```

若 `devices -l` 没有列出设备，应先恢复 USB/Wi-Fi 调试连接；不要把“无设备”误判为安装失败，也不要为此
清除应用数据。覆盖安装必须使用 `install -r`，不得使用 `pm clear` 或先卸载应用，否则账户级网络存储配置会
随应用数据一起删除。

## Gradle 临时目录

紧凑 Gradle 运行器的默认 `%TEMP%\gradle-run` 在本机曾出现 `WinError 5`（无写入权限）。复用已经验证可写的
任务专用目录：

```powershell
python3 'C:\Users\wucha\.codex\skills\gradle-run\scripts\gradle_run.py' `
  --root 'C:\Users\wucha\AppData\Local\Temp\azureql-gradle-run' create

# 复制上一条命令返回的 workflow id，并在 qinglong-app-android 仓库目录执行：
python3 'C:\Users\wucha\.codex\skills\gradle-run\scripts\gradle_run.py' `
  --root 'C:\Users\wucha\AppData\Local\Temp\azureql-gradle-run' run `
  --workflow '<workflow-id>' --scope targeted --question 'backup unit tests' -- `
  .\gradlew.bat :feature:backup:testDebugUnitTest --max-workers=1

python3 'C:\Users\wucha\.codex\skills\gradle-run\scripts\gradle_run.py' `
  --root 'C:\Users\wucha\AppData\Local\Temp\azureql-gradle-run' finish `
  --workflow '<workflow-id>'
```

需要隔离并发构建时，可在 `C:\Users\wucha\AppData\Local\Temp` 下创建新的项目专用子目录。不要复用或改写
`HOME`、`CODEX_HOME` 等系统环境变量，也不要把临时目录放进仓库。
