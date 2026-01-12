@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.ghostdev.huntit.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

@Composable
expect fun CameraView(controller: CameraController)

enum class FlashMode {
    ON, OFF
}

expect class CameraController() {
    fun toggleFlash()
    fun isFlashOn(): Boolean
    fun takePhoto(onPhotoTaken: (ImageBitmap) -> Unit)
}

expect class PermissionsManager(callback: PermissionCallback) : PermissionHandler {
    @Composable
    override fun askPermission(permission: PermissionType)

    @Composable
    override fun isPermissionGranted(permission: PermissionType): Boolean

    @Composable
    override fun launchSettings()
}

interface PermissionCallback {
    fun onPermissionStatus(permissionType: PermissionType, status: PermissionStatus)
}

@Composable
expect fun createPermissionsManager(callback: PermissionCallback): PermissionsManager

interface PermissionHandler {
    @Composable
    fun askPermission(permission: PermissionType)

    @Composable
    fun isPermissionGranted(permission: PermissionType): Boolean

    @Composable
    fun launchSettings()

}

enum class PermissionType {
    GALLERY,
    CAMERA
}
enum class PermissionStatus {
    GRANTED,
    DENIED,
    SHOW_RATIONALE
}