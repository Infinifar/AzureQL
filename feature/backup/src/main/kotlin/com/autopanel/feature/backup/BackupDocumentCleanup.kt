package com.autopanel.feature.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

internal fun deleteBackupDocument(context: Context, uri: Uri): Boolean {
    val resolver = context.contentResolver
    val deletedAsDocument = runCatching {
        DocumentsContract.isDocumentUri(context, uri) &&
            DocumentsContract.deleteDocument(resolver, uri)
    }.getOrDefault(false)
    if (deletedAsDocument) return true

    return runCatching { resolver.delete(uri, null, null) > 0 }
        .getOrDefault(false)
}
