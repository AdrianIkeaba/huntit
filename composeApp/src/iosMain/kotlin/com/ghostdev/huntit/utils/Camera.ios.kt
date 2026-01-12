@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.ghostdev.huntit.utils

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.AVFoundation.*
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.Foundation.getBytes
import platform.Photos.*
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIView
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CameraView(controller: CameraController) {
    val device = AVCaptureDevice.devicesWithMediaType(AVMediaTypeVideo).firstOrNull { device ->
        (device as AVCaptureDevice).position == AVCaptureDevicePositionBack
    }!! as AVCaptureDevice

    val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null) as AVCaptureDeviceInput

    val output = AVCaptureStillImageOutput()
    output.outputSettings = mapOf(AVVideoCodecKey to AVVideoCodecJPEG)

    val session = remember {
        AVCaptureSession().apply {
            sessionPreset = AVCaptureSessionPresetPhoto
            addInput(input)
            addOutput(output)
        }
    }

    controller.setSession(session)
    controller.setOutput(output)

    val cameraPreviewLayer = remember { AVCaptureVideoPreviewLayer(session = session) }

    DisposableEffect(Unit) {
        onDispose {
            dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
                session.stopRunning()
            }
        }
    }

    UIKitView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            val container = UIView()
            cameraPreviewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
            container.layer.addSublayer(cameraPreviewLayer)

            // Start session on background thread
            dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
                session.startRunning()
            }

            container
        },
        update = { view ->
            // Update the preview layer frame whenever the view bounds change
            dispatch_async(dispatch_get_main_queue()) {
                cameraPreviewLayer.frame = view.layer.bounds
            }
        }
    )
}

@OptIn(ExperimentalForeignApi::class)
actual class CameraController actual constructor() {
    private var flashOn = false
    private val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
    private var session: AVCaptureSession? = null
    private var output: AVCaptureStillImageOutput? = null

    fun setSession(captureSession: AVCaptureSession) {
        session = captureSession
    }

    fun setOutput(stillImageOutput: AVCaptureStillImageOutput) {
        output = stillImageOutput
    }

    actual fun toggleFlash() {
        device?.let {
            if (it.hasTorch()) {
                it.lockForConfiguration(null)
                flashOn = !flashOn
                it.setTorchMode(if (flashOn) AVCaptureTorchModeOn else AVCaptureTorchModeOff)
                it.unlockForConfiguration()
            }
        }
    }

    actual fun isFlashOn(): Boolean = flashOn

    actual fun takePhoto(onPhotoTaken: (ImageBitmap) -> Unit) {
        val captureOutput = output ?: return

        val videoConnection = captureOutput.connectionWithMediaType(AVMediaTypeVideo)
            ?: return

        captureOutput.captureStillImageAsynchronouslyFromConnection(videoConnection) { imageDataSampleBuffer, error ->
            if (error != null) {
                println("Error capturing image: $error")
                return@captureStillImageAsynchronouslyFromConnection
            }

            imageDataSampleBuffer?.let { sampleBuffer ->
                val imageData = AVCaptureStillImageOutput.jpegStillImageNSDataRepresentation(sampleBuffer)
                imageData?.let { data ->
                    val bytes = ByteArray(data.length.toInt())

                    bytes.usePinned { pinned ->
                        data.getBytes(pinned.addressOf(0), data.length)
                    }

                    try {
                        val skiaImage = Image.makeFromEncoded(bytes)
                        val imageBitmap = skiaImage.toComposeImageBitmap()

                        dispatch_async(dispatch_get_main_queue()) {
                            onPhotoTaken(imageBitmap)
                        }
                    } catch (e: Exception) {
                        println("Error converting image: ${e.message}")
                    }
                }
            }
        }
    }
}

@Composable
actual fun createPermissionsManager(callback: PermissionCallback): PermissionsManager {
    return PermissionsManager(callback)
}

actual class PermissionsManager actual constructor(private val callback: PermissionCallback) :
    PermissionHandler {
    @Composable
    actual override fun askPermission(permission: PermissionType) {
        when (permission) {
            PermissionType.CAMERA -> {
                val status: AVAuthorizationStatus =
                    remember { AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) }
                askCameraPermission(status, permission, callback)
            }

            PermissionType.GALLERY -> {
                val status: PHAuthorizationStatus =
                    remember { PHPhotoLibrary.authorizationStatus() }
                askGalleryPermission(status, permission, callback)
            }
        }
    }

    private fun askCameraPermission(
        status: AVAuthorizationStatus, permission: PermissionType, callback: PermissionCallback
    ) {
        when (status) {
            AVAuthorizationStatusAuthorized -> {
                callback.onPermissionStatus(permission, PermissionStatus.GRANTED)
            }

            AVAuthorizationStatusNotDetermined -> {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { isGranted ->
                    dispatch_async(dispatch_get_main_queue()) {
                        callback.onPermissionStatus(
                            permission,
                            if (isGranted) PermissionStatus.GRANTED else PermissionStatus.DENIED
                        )
                    }
                }
            }

            AVAuthorizationStatusDenied -> {
                callback.onPermissionStatus(permission, PermissionStatus.DENIED)
            }

            else -> error("unknown camera status $status")
        }
    }

    private fun askGalleryPermission(
        status: PHAuthorizationStatus, permission: PermissionType, callback: PermissionCallback
    ) {
        when (status) {
            PHAuthorizationStatusAuthorized -> {
                callback.onPermissionStatus(permission, PermissionStatus.GRANTED)
            }

            PHAuthorizationStatusNotDetermined -> {
                PHPhotoLibrary.requestAuthorization { newStatus ->
                    askGalleryPermission(newStatus, permission, callback)
                }
            }

            PHAuthorizationStatusDenied -> {
                callback.onPermissionStatus(
                    permission, PermissionStatus.DENIED
                )
            }

            else -> error("unknown gallery status $status")
        }
    }

    @Composable
    actual override fun isPermissionGranted(permission: PermissionType): Boolean {
        return when (permission) {
            PermissionType.CAMERA -> {
                val status: AVAuthorizationStatus =
                    remember { AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) }
                status == AVAuthorizationStatusAuthorized
            }

            PermissionType.GALLERY -> {
                val status: PHAuthorizationStatus =
                    remember { PHPhotoLibrary.authorizationStatus() }
                status == PHAuthorizationStatusAuthorized
            }
        }
    }

    @Composable
    actual override fun launchSettings() {
        NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let {
            UIApplication.sharedApplication.openURL(it)
        }
    }
}