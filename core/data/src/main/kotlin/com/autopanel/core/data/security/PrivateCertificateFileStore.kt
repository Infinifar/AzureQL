package com.autopanel.core.data.security

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Copies user-selected certificate material into the app-private certificate directory. */
@Singleton
class PrivateCertificateFileStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    suspend fun importClientCertificate(uri: Uri): String =
        import(uri, "client_identity", ".p12")

    suspend fun importCustomCa(uri: Uri): String =
        import(uri, "server_ca", ".pem")

    suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        val root = certificateRoot()
        val target = File(path).canonicalFile
        if (target.parentFile == root) target.delete()
    }

    private suspend fun import(uri: Uri, prefix: String, extension: String): String =
        withContext(Dispatchers.IO) {
            val root = certificateRoot().also { directory ->
                check(directory.exists() || directory.mkdirs()) { "无法创建证书目录" }
            }
            val target = File(root, "${prefix}_${UUID.randomUUID()}$extension")
            val partial = File(root, ".${target.name}.part")
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("无法读取证书文件")
                input.use { source ->
                    FileOutputStream(partial).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = source.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= MAX_CERTIFICATE_BYTES) { "证书文件不能超过 16 MiB" }
                            output.write(buffer, 0, read)
                        }
                        require(total > 0L) { "证书文件为空" }
                        output.fd.sync()
                    }
                }
                try {
                    Files.move(
                        partial.toPath(),
                        target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(
                        partial.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
                target.absolutePath
            } catch (error: Exception) {
                partial.delete()
                target.delete()
                throw error
            }
        }

    private fun certificateRoot(): File = File(context.filesDir, "cert").canonicalFile

    private companion object {
        const val MAX_CERTIFICATE_BYTES = 16L * 1024L * 1024L
    }
}
