package fr.berliat.hskwidget.ui.screens.OCR

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger

import com.kashif.cameraK.controller.CameraController
import com.kashif.cameraK.result.ImageCaptureResult
import kotlinx.coroutines.launch

import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.core.IntentSources
import fr.berliat.hskwidget.core.AppDispatchers
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.core.Logging
import fr.berliat.hskwidget.core.SnackbarType
import fr.berliat.hskwidget.ocr_capture_error_processed

import io.github.vinceglb.filekit.PlatformFile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CaptureImageViewModel(
    val onImageReady: (PlatformFile) -> Unit
): ViewModel() {
    fun takePhoto(cameraController: CameraController) {
        Logger.d(tag = TAG, messageString = "takePhoto triggered")
        viewModelScope.launch(AppDispatchers.IO) {
            when (val result = cameraController.takePictureToFile()) {
                is ImageCaptureResult.SuccessWithFile -> {
                    Logger.d(tag = TAG, messageString = "Image capture success: ${result.filePath}")
                    withContext(Dispatchers.Main) {
                        onImageReady(PlatformFile(result.filePath))
                    }
                }

                is ImageCaptureResult.Error -> {
                    logError(result.exception.message ?: "")
                }

                else -> {
                    logError("Unknown error")
                }
            }
        }

        Logging.logAnalyticsEvent(
            Logging.ANALYTICS_EVENTS.OCR_CAPTURE,
            mapOf("SOURCE" to IntentSources.IN_APP.toString())
        )
    }

    private fun logError(message: String) {
        Logger.e(
            tag = TAG,
            messageString = "Image Capture Error: $message"
        )
        HSKAppServices.snackbar.show(SnackbarType.ERROR, Res.string.ocr_capture_error_processed)

        Logging.logAnalyticsError(
            "OCR_CAPTURE",
            "ProcessingPictureFailed",
            message
        )
    }

    companion object {
        private const val TAG = "CaptureImageViewModel"
    }
}
