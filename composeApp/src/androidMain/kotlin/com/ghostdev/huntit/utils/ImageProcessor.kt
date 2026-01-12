package com.ghostdev.huntit.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class ImageProcessor {

    actual suspend fun compressImage(imageData: ByteArray, maxSizeBytes: Int): ByteArray? {
        return withContext(Dispatchers.Default) {
            try {
                val originalBitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                    ?: return@withContext null

                // Calculate maximum dimensions while maintaining aspect ratio
                val maxDimension = 1200f
                val scale: Float
                val width = originalBitmap.width
                val height = originalBitmap.height

                scale = if (width > height) {
                    maxDimension / width
                } else {
                    maxDimension / height
                }

                // Only scale down, not up
                val scaledWidth = if (scale < 1) (width * scale).toInt() else width
                val scaledHeight = if (scale < 1) (height * scale).toInt() else height

                val scaledBitmap =
                    Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)

                // Start with quality 85
                var quality = 85
                var outputStream = ByteArrayOutputStream()

                // Compress with reducing quality until size is acceptable
                do {
                    outputStream = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                    if (quality > 10) quality -= 10
                } while (outputStream.size() > maxSizeBytes && quality > 10)

                // Recycle bitmaps to free memory
                if (originalBitmap != scaledBitmap) {
                    originalBitmap.recycle()
                }
                scaledBitmap.recycle()

                outputStream.toByteArray()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    actual fun imageToBase64(imageData: ByteArray): String? {
        return try {
            Base64.encodeToString(imageData, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    actual fun imageBitmapToByteArray(imageBitmap: ImageBitmap): ByteArray? {
        return try {
            val bitmap = imageBitmap.asAndroidBitmap()
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}