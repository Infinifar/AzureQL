package com.autopanel.core.ui.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalizedTextTest {

    @Test
    fun `english quantity selects singular and plural forms`() {
        assertEquals("1 task", englishQuantity(1, "task"))
        assertEquals("0 tasks", englishQuantity(0, "task"))
        assertEquals("2 selected variables", englishQuantity(2, "selected variable"))
        assertEquals("1 dependency", englishQuantity(1, "dependency", "dependencies"))
    }

    @Test
    fun `client business and transient messages have complete English copy`() {
        val messages = listOf(
            "获取任务列表失败",
            "标签被 1 个任务引用，不能删除",
            "已导出 1 条任务",
            "已导入 2 条变量",
            "获取脚本内容失败",
            "已取消编辑，本地文件已还原。",
            "上传已提交但尚未确认，本地修改已保留，请稍后重试",
            "获取订阅列表失败",
            "订阅已加入运行队列",
            "获取依赖列表失败",
            "依赖任务实时连接中断，仍可查看各项 HTTP 提交结果",
            "获取系统配置失败",
            "更新 Node.js 镜像失败（HTTP 500）",
            "获取通知设置失败",
            "通知服务连接超时，请检查青龙容器网络",
            "测试通知发送成功，设置已保存",
            "账户信息已保存",
            "备份、恢复或网络导出仍在进行，无法删除当前账户",
            "WebDAV 设置已保存，连接测试成功",
            "S3 认证或权限校验失败，请检查访问密钥和存储桶权限",
            "网络连接失败，请检查服务器状态和网络后重试",
            "服务已恢复，请重新登录",
            "重启指令已发送，青龙即将重启"
        )

        messages.forEach { message ->
            val english = localizedMessage(message, english = true)
            assertFalse("Untranslated client copy: $english", english.contains(Regex("[\\p{IsHan}]")))
        }
    }

    @Test
    fun `Chinese mode leaves message unchanged`() {
        val message = "已导出 1 条任务"
        assertEquals(message, localizedMessage(message, english = false))
    }
}
