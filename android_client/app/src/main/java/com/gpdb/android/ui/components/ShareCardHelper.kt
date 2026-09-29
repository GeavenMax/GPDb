package com.gpdb.android.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object ShareCardHelper {

    /**
     * 从海报 Bitmap 中采样提取主色调
     */
    fun extractDominantColor(bitmap: Bitmap): Color {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            if (width <= 0 || height <= 0) return Color(0xFF1E222B)

            var rSum = 0L
            var gSum = 0L
            var bSum = 0L
            var count = 0

            // 采样 40 个分散像素点
            val stepX = (width / 7).coerceAtLeast(1)
            val stepY = (height / 7).coerceAtLeast(1)

            for (x in 0 until width step stepX) {
                for (y in 0 until height step stepY) {
                    val pixel = bitmap.getPixel(x, y)
                    rSum += (pixel shr 16) and 0xFF
                    gSum += (pixel shr 8) and 0xFF
                    bSum += pixel and 0xFF
                    count++
                }
            }

            if (count == 0) return Color(0xFF1E222B)
            val avgR = (rSum / count).toInt().coerceIn(0, 255)
            val avgG = (gSum / count).toInt().coerceIn(0, 255)
            val avgB = (bSum / count).toInt().coerceIn(0, 255)

            // 提升适当饱和度，让背景渐变更具质感与氛围
            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV(avgR, avgG, avgB, hsv)
            hsv[1] = (hsv[1] * 1.3f).coerceIn(0.25f, 0.75f) // 适度饱满
            hsv[2] = (hsv[2] * 0.85f).coerceIn(0.2f, 0.6f)  // 偏暗雅致

            val finalColor = android.graphics.Color.HSVToColor(hsv)
            Color(finalColor)
        } catch (e: Exception) {
            Color(0xFF1E222B)
        }
    }

    /**
     * 保存 Bitmap 到系统相册 (Pictures/GPDb)
     */
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, title: String): Boolean {
        return try {
            val fileName = "GPDb_${System.currentTimeMillis()}.png"
            var outputStream: OutputStream? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GPDb")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return false

                outputStream = resolver.openOutputStream(uri)
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    outputStream.flush()
                }

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val gpdbDir = File(picturesDir, "GPDb").apply { if (!exists()) mkdirs() }
                val imageFile = File(gpdbDir, fileName)
                outputStream = FileOutputStream(imageFile)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()

                // 通知媒体库扫描
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                mediaScanIntent.data = Uri.fromFile(imageFile)
                context.sendBroadcast(mediaScanIntent)
            }

            outputStream?.close()
            Toast.makeText(context, "已成功保存分享卡片至系统相册", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "保存卡片失败: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * 通过 FileProvider 唤起系统一键分享
     */
    fun shareBitmap(context: Context, bitmap: Bitmap, title: String) {
        try {
            val cacheFolder = File(context.cacheDir, "shared_cards").apply { if (!exists()) mkdirs() }
            val shareFile = File(cacheFolder, "gpdb_share_${System.currentTimeMillis()}.png")
            FileOutputStream(shareFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                shareFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "《$title》- 分享自 GPDb 个人数字影库")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "分享电影档案卡片"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "调起分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
