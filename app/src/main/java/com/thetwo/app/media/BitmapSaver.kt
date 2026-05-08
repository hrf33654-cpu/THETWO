package com.thetwo.app.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BitmapSaver {
    fun saveCapture(context: Context, bitmap: Bitmap): Result<String> {
        val displayName = "thetwo_${timestamp()}.jpg"
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveWithMediaStore(context, bitmap, displayName)
            } else {
                saveWithLegacyExternalDir(context, bitmap, displayName)
            }
        }
    }

    private fun saveWithMediaStore(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
    ): String {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/THETWO")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = requireNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values),
        ) { "无法创建系统相册记录" }

        resolver.openOutputStream(uri)?.use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) { "图片压缩失败" }
        } ?: error("无法写入系统相册")

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri.toString()
    }

    private fun saveWithLegacyExternalDir(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
    ): String {
        val picturesDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "THETWO")
        if (!picturesDir.exists()) {
            picturesDir.mkdirs()
        }
        val file = File(picturesDir, displayName)
        FileOutputStream(file).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) { "图片压缩失败" }
        }
        return file.absolutePath
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}
