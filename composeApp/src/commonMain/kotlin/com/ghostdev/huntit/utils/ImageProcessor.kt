package com.ghostdev.huntit.utils

import androidx.compose.ui.graphics.ImageBitmap

expect class ImageProcessor() {
    /**
     * Compresses an image to reduce file size (target 1MB, max 2MB)
     * @param imageData Raw image data as ByteArray
     * @param maxSizeBytes Maximum allowed file size in bytes (default 2MB)
     * @return Compressed image data as ByteArray or null if compression failed
     */
    suspend fun compressImage(imageData: ByteArray, maxSizeBytes: Int = 2 * 1024 * 1024): ByteArray?

    /**
     * Converts image data to base64 string for API calls
     * @param imageData Image data as ByteArray
     * @return Base64 encoded string or null if encoding failed
     */
    fun imageToBase64(imageData: ByteArray): String?

    /**
     * Converts an ImageBitmap to ByteArray
     * @param imageBitmap The ImageBitmap to convert
     * @return ByteArray representation or null if conversion failed
     */
    fun imageBitmapToByteArray(imageBitmap: ImageBitmap): ByteArray?
}