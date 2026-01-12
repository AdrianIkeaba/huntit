@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.ghostdev.huntit.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.Settings
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import java.io.ByteArrayOutputStream

@Composable
actual fun CameraView(controller: CameraController) {
    val context = LocalContext.current
    val lifeCycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
        update = {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
            preview.surfaceProvider = previewView.surfaceProvider

            val imageCapture = ImageCapture.Builder()
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifeCycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                controller.setCamera(camera)
                controller.setImageCapture(imageCapture)
                controller.setContext(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    )
}

actual class CameraController actual constructor() {
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var context: Context? = null
    private var flashOn = false

    fun setCamera(cam: Camera) {
        camera = cam
    }

    fun setImageCapture(capture: ImageCapture) {
        imageCapture = capture
    }

    fun setContext(ctx: Context) {
        context = ctx
    }

    actual fun toggleFlash() {
        camera?.let {
            flashOn = !flashOn
            it.cameraControl.enableTorch(flashOn)
        }
    }

    actual fun isFlashOn(): Boolean = flashOn

    actual fun takePhoto(onPhotoTaken: (ImageBitmap) -> Unit) {
        val capture = imageCapture ?: return
        val ctx = context ?: return

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            ctx.cacheDir.resolve("temp_photo_${System.currentTimeMillis()}.jpg")
        ).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    output.savedUri?.let { uri ->
                        try {
                            ctx.contentResolver.openInputStream(uri)?.use { inputStream ->
                                val bitmap = BitmapFactory.decodeStream(inputStream)

                                // Rotate bitmap if needed (camera might capture in wrong orientation)
                                val rotatedBitmap = rotateBitmap(bitmap, 90f)

                                val imageBitmap = rotatedBitmap.asImageBitmap()
                                onPhotoTaken(imageBitmap)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()
                }
            }
        )
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

@Composable
actual fun createPermissionsManager(callback: PermissionCallback): PermissionsManager {
    return remember { PermissionsManager(callback) }
}

actual class PermissionsManager actual constructor(private val callback: PermissionCallback) :
    PermissionHandler {
    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    actual override fun askPermission(permission: PermissionType) {
        when (permission) {
            PermissionType.CAMERA -> {
                val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

                LaunchedEffect(Unit) {
                    cameraPermissionState.launchPermissionRequest()
                }

                when {
                    cameraPermissionState.status.isGranted -> {
                        callback.onPermissionStatus(permission, PermissionStatus.GRANTED)
                    }
                    cameraPermissionState.status.shouldShowRationale -> {
                        callback.onPermissionStatus(permission, PermissionStatus.SHOW_RATIONALE)
                    }
                    else -> {
                        callback.onPermissionStatus(permission, PermissionStatus.DENIED)
                    }
                }
            }

            PermissionType.GALLERY -> {
                callback.onPermissionStatus(permission, PermissionStatus.GRANTED)
            }
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    actual override fun isPermissionGranted(permission: PermissionType): Boolean {
        return when (permission) {
            PermissionType.CAMERA -> {
                val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
                cameraPermissionState.status.isGranted
            }

            PermissionType.GALLERY -> {
                true
            }
        }
    }

    @Composable
    actual override fun launchSettings() {
        val context = LocalContext.current
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).also {
            context.startActivity(it)
        }
    }
}