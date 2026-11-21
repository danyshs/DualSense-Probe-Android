package dev.dsprobe.app.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

private const val LOG_DIR = "logs"
private const val LOG_FILE = "dsprobe-log.txt"

/**
 * Writes the log under the app's own files and offers it through the share sheet; only that one
 * file is exposed.
 */
fun shareLog(context: Context, text: String, chooserTitle: String) {
    val dir = File(context.filesDir, LOG_DIR)
    check(dir.isDirectory || dir.mkdirs()) { "cannot create the log folder" }
    val file = File(dir, LOG_FILE)
    file.writeText(text)
    val uri = FileProvider.getUriForFile(context, context.packageName + ".logs", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}
