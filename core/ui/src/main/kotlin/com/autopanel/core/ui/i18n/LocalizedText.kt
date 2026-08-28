package com.autopanel.core.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** Resolves small, code-owned UI copy against the Activity's active locale. */
@Composable
fun localizedText(chinese: String, english: String): String =
    if (isEnglishUi()) english else chinese

@Composable
fun isEnglishUi(): Boolean = LocalConfiguration.current.locales[0].language == "en"

/** Translates client-generated transient messages while leaving server detail intact. */
fun localizedMessage(message: String, english: Boolean): String {
    if (!english) return message
    return messageReplacements.fold(message) { value, (chinese, translated) ->
        value.replace(chinese, translated)
    }.replace("：", ": ").replace("，", ", ")
}

private val messageReplacements = listOf(
    "本地编辑后的文件不是有效的 UTF-8 文本，已阻止回传。" to
        "The locally edited file is not valid UTF-8, so uploading has been blocked.",
    "文件不是有效的 UTF-8 文本，已阻止回传" to
        "The file is not valid UTF-8, so uploading has been blocked",
    "文件不是有效的 UTF-8 文本。为避免乱码和误覆盖，仅保留原始文件下载。" to
        "The file is not valid UTF-8. To avoid corruption or accidental overwrites, only the original file can be downloaded.",
    "文件超过 10 MB，当前仅按段预览和下载，不允许从客户端回传。" to
        "The file exceeds 10 MB. It can only be previewed in sections or downloaded, not uploaded from the app.",
    "大文件已缓存到应用私有目录并按段预览。请选择“本地编辑”，返回后再上传修改。" to
        "The large file is cached privately and previewed in sections. Choose Edit locally, then return to upload changes.",
    "已检测到本地修改。上传前会检查服务端脚本是否同时发生变化。" to
        "Local changes detected. The server version will be checked before uploading.",
    "大文件请使用本地编辑器修改后回传" to
        "Use a local text editor to modify this large file, then return to upload it",
    "未找到可编辑文本文件的本地应用" to "No local app can edit text files",
    "本地脚本缓存已失效，请重新打开脚本" to "The local script cache expired. Reopen the script.",
    "脚本超过 50 MB，无法创建本地预览缓存" to
        "The script exceeds 50 MB and cannot be cached for preview",
    "本地修改超过 10 MB，无法安全回传" to
        "Local changes exceed 10 MB and cannot be uploaded safely",
    "读取本地修改失败" to "Failed to read local changes",
    "检查本地修改失败" to "Failed to check local changes",
    "加载分段失败" to "Failed to load section",
    "保存前检查服务端版本失败" to "Failed to check the server version before saving",
    "保存脚本失败" to "Failed to save script",
    "文件包含无法按 UTF-8 解码的字符。为避免覆盖原文件，当前仅允许查看和下载。" to
        "The file contains invalid UTF-8. To prevent data loss, it is read-only and can only be viewed or downloaded.",
    "文件超过 2,000,000 个字符。为避免编辑器卡顿和误覆盖，当前仅允许查看和下载。" to
        "The file exceeds 2,000,000 characters. To prevent freezes or accidental overwrites, it is read-only.",
    "脚本内容尚未成功加载，不能编辑" to "The script has not loaded successfully and cannot be edited",
    "环境变量导入完成" to "Variable import completed",
    "重启指令已发送，青龙即将重启" to "Restart command sent. QingLong is restarting.",
    "重启失败" to "Restart failed",
    "备份中没有有效环境变量" to "The backup contains no valid variables",
    "备份文件不存在" to "Backup file not found",
    "备份文件超过" to "Backup file exceeds",
    "备份数据超过" to "Backup data exceeds",
    "备份文件为空" to "The backup file is empty",
    "请输入有效的备份大小上限" to "Enter a valid backup size limit",
    "目标位置可能留有不完整文件" to "the incomplete destination was removed",
    "服务端数据尚未恢复" to "server data has not been restored",
    "无法读取安全登录信息，请重新输入" to "Secure sign-in data could not be read. Enter it again.",
    "服务器地址必须以 http:// 或 https:// 开头" to "The server address must start with http:// or https://",
    "HTTP 会明文传输凭据，请先明确允许不安全 HTTP" to
        "HTTP sends credentials in plain text. Explicitly allow insecure HTTP first.",
    "Client ID 登录不支持两步验证" to "Client ID sign-in does not support two-factor authentication",
    "证书保存失败，请检查文件权限和格式" to "Could not save the certificate. Check its format and file access.",
    "私有 CA 保存失败，请检查文件权限和格式" to "Could not save the private CA. Check its format and file access.",
    "无法读取证书文件" to "Could not read the certificate file",
    "无法读取 CA 文件" to "Could not read the CA file",
    "设备未设置可用的生物识别或锁屏凭据" to "No supported biometric or device credential is configured",
    "已配置客户端证书" to "Client certificate configured",
    "已配置私有 CA" to "Private CA configured",
    "未找到有效的 export 语句" to "No valid export statements found",
    "跳过重复" to "duplicates skipped",
    "无效" to "invalid",
    "操作失败" to "Operation failed",
    "重新安装失败" to "Reinstallation failed",
    "重新安装已提交" to "Reinstallation submitted",
    "重装任务已提交" to "reinstallation submitted",
    "安装任务已提交" to "installation submitted",
    "删除任务已提交" to "deletion submitted",
    "加载日志失败" to "Failed to load logs",
    "加载失败" to "Load failed",
    "导入失败" to "Import failed",
    "导出失败" to "Export failed",
    "下载失败" to "Download failed",
    "保存失败" to "Save failed",
    "删除失败" to "Delete failed",
    "取消置顶失败" to "Unpin failed",
    "置顶失败" to "Pin failed",
    "上传备份失败" to "Backup upload failed",
    "导出备份失败" to "Backup export failed",
    "启动数据恢复失败" to "Could not start restore",
    "备份已保存" to "Backup saved",
    "导出已取消" to "Export cancelled",
    "上传已取消" to "Upload cancelled",
    "日志已删除" to "Log deleted",
    "暂无内容" to "No content",
    "暂无日志" to "No logs",
    "保存成功" to "Saved",
    "已保存" to "Saved",
    "已创建" to "Created",
    "已删除" to "Deleted",
    "已下载到" to "Downloaded to",
    "已导出" to "Exported",
    "已导入" to "Imported",
    "条变量" to " variables",
    "条任务" to " tasks",
    "新增" to "added",
    "失败" to "failed",
    "请输入验证码" to "Enter the verification code",
    "仍需验证" to "Verification is still required",
    "登录配置失败" to "Sign-in configuration failed"
)
