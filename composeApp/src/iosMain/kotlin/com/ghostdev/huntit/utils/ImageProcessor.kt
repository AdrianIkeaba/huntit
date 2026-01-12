package com.ghostdev.huntit.utils

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.getBytes
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImage

actual class ImageProcessor {

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun compressImage(imageData: ByteArray, maxSizeBytes: Int): ByteArray? {
        return withContext(Dispatchers.Default) {
            try {
                // Basic iOS implementation - this could be enhanced for better scaling
                imageData.usePinned { pinnedData ->
                    val nsData =
                        NSData.dataWithBytes(pinnedData.addressOf(0), imageData.size.toULong())
                    val image = UIImage.imageWithData(nsData) ?: return@withContext null

                    // Start with quality 0.85 and compress
                    var quality = 0.85
                    var jpegData = UIImageJPEGRepresentation(image, quality)

                    // Reduce quality if needed
                    while (jpegData != null && jpegData.length.toInt() > maxSizeBytes && quality > 0.1) {
                        quality -= 0.1
                        jpegData = UIImageJPEGRepresentation(image, quality)
                    }

                    if (jpegData == null) return@withContext null

                    // Convert back to ByteArray using NSData methods
                    val bytes = ByteArray(jpegData.length.toInt())
                    bytes.usePinned { pinnedBytes ->
                        jpegData.getBytes(pinnedBytes.addressOf(0), jpegData.length)
                    }
                    bytes
                }
            } catch (e: Exception) {
                println("Error compressing image: ${e.message}")
                null
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun imageToBase64(imageData: ByteArray): String? {
        return try {
            imageData.usePinned { pinnedData ->
                val nsData = NSData.dataWithBytes(pinnedData.addressOf(0), imageData.size.toULong())
                nsData.base64EncodedStringWithOptions(0u).toString()
            }
        } catch (e: Exception) {
            println("Error converting image to Base64: ${e.message}")
            null
        }
    }

    actual fun imageBitmapToByteArray(imageBitmap: ImageBitmap): ByteArray? {
        // Note: This is a simplified placeholder implementation
        // The actual implementation would depend on how ImageBitmap is implemented on iOS
        println("Warning: imageBitmapToByteArray not fully implemented for iOS")
        return ByteArray(0) // Placeholder
    }
}