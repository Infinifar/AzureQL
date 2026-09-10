package com.autopanel.feature.backup

import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document

internal fun isSupportedBackupFile(fileName: String): Boolean {
    val normalized = fileName.lowercase()
    return normalized.endsWith(".tgz") || normalized.endsWith(".tar.gz") ||
        normalized.endsWith(".gz")
}

internal fun validateRemoteBackupFileName(value: String): String {
    require(value.isNotBlank() && value != "." && value != "..") { "远端备份文件名无效" }
    require('/' !in value && '\\' !in value && value.none(Char::isISOControl)) {
        "远端备份文件名无效"
    }
    require(isSupportedBackupFile(value)) { "远端文件不是受支持的备份格式" }
    return value
}

internal fun copyBackupStream(
    input: InputStream,
    output: OutputStream,
    totalBytes: Long?,
    maxBytes: Long,
    onProgress: (bytesTransferred: Long, totalBytes: Long?) -> Unit
) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var transferred = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        transferred += count
        if (transferred > maxBytes) {
            throw IllegalArgumentException("备份数据超过大小上限，下载已中止")
        }
        output.write(buffer, 0, count)
        onProgress(transferred, totalBytes)
    }
}

internal fun InputStream.readBytesUpTo(maxBytes: Int): ByteArray {
    require(maxBytes > 0)
    val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (output.size() <= maxBytes) {
        val remaining = maxBytes + 1 - output.size()
        val count = read(buffer, 0, minOf(buffer.size, remaining))
        if (count < 0) break
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

internal fun parseNetworkStorageXml(bytes: ByteArray): Document {
    val text = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
    require(
        !text.contains("<!DOCTYPE", ignoreCase = true) &&
            !text.contains("<!ENTITY", ignoreCase = true)
    ) { "网络存储返回了不安全的 XML" }
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
        runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
    }
    return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
}
