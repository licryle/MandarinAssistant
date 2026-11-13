package fr.berliat.hskwidget.ui.screens.OCR

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp

import co.touchlab.kermit.Logger

import com.kashif.cameraK.compose.CameraKScreen
import com.kashif.cameraK.compose.rememberCameraKState
import com.kashif.cameraK.permissions.providePermissions
import com.kashif.cameraK.state.CameraConfiguration
import com.kashif.cameraK.enums.*

import fr.berliat.hskwidget.ui.components.Error
import fr.berliat.hskwidget.ui.components.ErrorView
import fr.berliat.hskwidget.ui.components.LoadingProgressView
import fr.berliat.hskwidget.ui.components.AppSnackbar
import fr.berliat.hskwidget.ui.components.IconButton

import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.core.SnackbarType
import fr.berliat.hskwidget.ic_launcher
import fr.berliat.hskwidget.pinch_zoom_out_24px
import fr.berliat.hskwidget.folder_open_24px
import fr.berliat.hskwidget.ocr_capture_btn
import fr.berliat.hskwidget.ocr_select_image_btn
import fr.berliat.hskwidget.ocr_capture_permission_denied
import fr.berliat.hskwidget.ocr_capture_pinch_hint

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val TAG = "CaptureImageScreen"

@Composable
fun CaptureImageScreen(
    modifier: Modifier = Modifier,
    onImageReady: (PlatformFile) -> Unit,
    viewModel: CaptureImageViewModel = remember { CaptureImageViewModel(onImageReady) }
) {
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        focusManager.clearFocus()
    }

    val launcher = rememberFilePickerLauncher(
        type = FileKitType.Image,
        onResult = { platformFile ->
            if (platformFile != null) {
                onImageReady(platformFile)
            }
        }
    )

    val permissions = providePermissions()
    val cameraPermissionState = remember { mutableStateOf(permissions.hasCameraPermission()) }
    val storagePermissionState = remember { mutableStateOf(permissions.hasStoragePermission()) }
    val retryPermissions = remember { mutableStateOf(true) }

    if (retryPermissions.value) {
        if (!cameraPermissionState.value) {
            permissions.RequestCameraPermission(
                onGranted = { cameraPermissionState.value = true },
                onDenied = {
                    Logger.e(tag = TAG, messageString = "Camera Permission Denied")
                }
            )
        }

        if (!storagePermissionState.value) {
            permissions.RequestStoragePermission(
                onGranted = { storagePermissionState.value = true },
                onDenied = {
                    Logger.e(tag = TAG, messageString = "Folder Permission Denied")
                }
            )
        }
        retryPermissions.value = false
    }

    Column(modifier = modifier.fillMaxSize().padding(0.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                text = stringResource(Res.string.ocr_select_image_btn),
                drawable = Res.drawable.folder_open_24px,
                onClick = { launcher.launch() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (!cameraPermissionState.value || !storagePermissionState.value) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ErrorView(error = Error(
                    errorText = Res.string.ocr_capture_permission_denied,
                    onRetryClick = { retryPermissions.value = true }
                ))
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val cameraKState = rememberCameraKState(
                    config = CameraConfiguration(
                        cameraLens = CameraLens.BACK,
                        flashMode = FlashMode.OFF,

                        // Image output
                        imageFormat = ImageFormat.PNG,
                        directory = Directory.DOCUMENTS,
                        qualityPrioritization = QualityPrioritization.BALANCED,

                        // iOS only: Advanced camera device types
                        cameraDeviceType = CameraDeviceType.DEFAULT,
                        aspectRatio = AspectRatio.RATIO_9_16
                    )
                )

                val zoomLevel = remember { mutableStateOf(1f) }
                val showPinchHint = remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(3.seconds)
                    showPinchHint.value = false
                }

                CameraKScreen(
                    cameraState = cameraKState.value,
                    showPreview = true,
                    modifier = Modifier.fillMaxSize().padding(0.dp),
                    loadingContent = {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 50.dp)
                                    .size(110.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.background)
                                    .border(3.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                LoadingProgressView()
                            }
                        }
                    },
                    errorContent = {
                        ErrorView(error = Error(
                            errorText = Res.string.ocr_capture_permission_denied,
                            onRetryClick = { retryPermissions.value = true }
                        ))
                    }
                ) { readyState ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoomChange, _ ->
                                    if (zoomChange != 1f) {
                                        zoomLevel.value *= zoomChange
                                        zoomLevel.value = zoomLevel.value.coerceIn(1f, 10f)
                                        readyState.controller.setZoom(zoomLevel.value)
                                    }
                                }
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 50.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AnimatedVisibility(
                                visible = showPinchHint.value,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                AppSnackbar(
                                    message = stringResource(Res.string.ocr_capture_pinch_hint),
                                    type = SnackbarType.INFO,
                                    customIcon = Res.drawable.pinch_zoom_out_24px,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                            }
                            CaptureButton(
                                onClick = { viewModel.takePhoto(readyState.controller) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(110.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.background)
            .border(3.dp, Color.White, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_launcher),
            contentDescription = stringResource(Res.string.ocr_capture_btn),
            modifier = Modifier.requiredSize(130.dp),
            tint = Color.Unspecified
        )
    }
}
