package io.github.fianna76.studypack.ui.module

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import io.github.fianna76.studypack.R
import java.io.File

/** Hands a notes PDF to another viewer, read-only, through the app's FileProvider. */
fun openPdfExternally(context: Context, file: File, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file, "$title.pdf")
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(intent, null))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.note_open_external_none, Toast.LENGTH_LONG).show()
    }
}
