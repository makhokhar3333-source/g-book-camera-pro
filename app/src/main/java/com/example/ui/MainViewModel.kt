package com.example.ui

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.ads.AdMobManager
import com.example.hdr.SmartHdrPhotoEngine
import com.example.hdr.VideoExportManager
import com.example.network.ConnectivityMonitor
import com.example.network.LiveOtaSync
import com.example.notifications.DailyNotificationScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen {
    NotificationGate,
    Welcome,
    Studio
}

enum class ConnectionBannerState {
    Hidden,
    OfflineRed,
    OnlineGreen
}

enum class StudioMediaType {
    None,
    Image,
    Video
}

data class GBookUiState(
    val currentScreen: AppScreen = AppScreen.NotificationGate,
    val isOnline: Boolean = true,
    val bannerState: ConnectionBannerState = ConnectionBannerState.Hidden,
    val mediaUri: Uri? = null,
    val mediaType: StudioMediaType = StudioMediaType.None,
    val originalBitmap: Bitmap? = null,
    val enhancedBitmap: Bitmap? = null,
    val isLoadingMedia: Boolean = false,
    val smartHdrIntensity: Float = 70f,
    val isComparingOriginal: Boolean = false,
    val isExporting: Boolean = false,
    val exportProgress: Int = 0,
    val statusToast: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val connectivityMonitor = ConnectivityMonitor(appContext)
    val adMobManager = AdMobManager(appContext)

    private val alreadyAllowedNotifications =
        DailyNotificationScheduler.isSchedulingEnabled(appContext)

    private val _uiState = MutableStateFlow(
        GBookUiState(
            currentScreen = if (alreadyAllowedNotifications) {
                AppScreen.Welcome
            } else {
                AppScreen.NotificationGate
            },
            isOnline = connectivityMonitor.isOnline.value
        )
    )
    val uiState: StateFlow<GBookUiState> = _uiState.asStateFlow()

    private var wasOfflineBannerShown = false
    private var bannerHideJob: Job? = null
    private var hdrRenderJob: Job? = null
    private var toastJob: Job? = null
    private var hasSyncedOtaThisSession = false

    init {
        if (alreadyAllowedNotifications) {
            DailyNotificationScheduler.scheduleAllDailyNotifications(appContext)
        }
        // Initialize AdMob immediately on app launch and pre-load all 3 tiers
        adMobManager.initializeAndPreload()

        // Continuously observe live internet changes for Red -> Green 2.5s banner transition
        viewModelScope.launch {
            connectivityMonitor.isOnline.collect { online ->
                val previouslyOnline = _uiState.value.isOnline
                _uiState.update { it.copy(isOnline = online) }

                if (online && !hasSyncedOtaThisSession) {
                    hasSyncedOtaThisSession = true
                    launch { LiveOtaSync.syncSilently(appContext) }
                }

                if (online && (!previouslyOnline || wasOfflineBannerShown || _uiState.value.bannerState == ConnectionBannerState.OfflineRed)) {
                    if (wasOfflineBannerShown || _uiState.value.bannerState == ConnectionBannerState.OfflineRed) {
                        wasOfflineBannerShown = false
                        showConnectedGreenBanner()
                    }
                } else if (!online && _uiState.value.bannerState == ConnectionBannerState.OnlineGreen) {
                    bannerHideJob?.cancel()
                    wasOfflineBannerShown = true
                    _uiState.update { it.copy(bannerState = ConnectionBannerState.OfflineRed) }
                }
            }
        }
    }

    fun onAllowNotificationsClicked() {
        DailyNotificationScheduler.markEnabledAndScheduleAll(appContext)
        _uiState.update { it.copy(currentScreen = AppScreen.Welcome) }
    }

    fun onEditingClicked() {
        viewModelScope.launch {
            val online = connectivityMonitor.verifyInternetNow()
            if (!online) {
                bannerHideJob?.cancel()
                wasOfflineBannerShown = true
                _uiState.update {
                    it.copy(
                        isOnline = false,
                        bannerState = ConnectionBannerState.OfflineRed
                    )
                }
            } else {
                wasOfflineBannerShown = false
                bannerHideJob?.cancel()
                _uiState.update {
                    it.copy(
                        isOnline = true,
                        bannerState = ConnectionBannerState.Hidden,
                        currentScreen = AppScreen.Studio
                    )
                }
            }
        }
    }

    fun navigateBackToWelcome() {
        _uiState.update { it.copy(currentScreen = AppScreen.Welcome) }
    }

    private fun showConnectedGreenBanner() {
        bannerHideJob?.cancel()
        _uiState.update { it.copy(bannerState = ConnectionBannerState.OnlineGreen) }
        bannerHideJob = viewModelScope.launch {
            delay(2_500L)
            _uiState.update { current ->
                if (current.bannerState == ConnectionBannerState.OnlineGreen) {
                    current.copy(bannerState = ConnectionBannerState.Hidden)
                } else {
                    current
                }
            }
        }
    }

    fun onMediaPicked(uri: Uri) {
        val mime = appContext.contentResolver.getType(uri).orEmpty().lowercase()
        val isVideo = mime.startsWith("video/") || uri.toString().contains("video", ignoreCase = true)

        if (isVideo) {
            hdrRenderJob?.cancel()
            _uiState.update {
                it.copy(
                    mediaUri = uri,
                    mediaType = StudioMediaType.Video,
                    originalBitmap = null,
                    enhancedBitmap = null,
                    isLoadingMedia = false,
                    isComparingOriginal = false
                )
            }
        } else {
            loadPhoto(uri)
        }
    }

    private fun loadPhoto(uri: Uri) {
        hdrRenderJob?.cancel()
        _uiState.update {
            it.copy(
                mediaUri = uri,
                mediaType = StudioMediaType.Image,
                isLoadingMedia = true,
                isComparingOriginal = false
            )
        }
        viewModelScope.launch {
            val decoded = SmartHdrPhotoEngine.decodeSafeBitmap(appContext, uri)
            if (decoded == null) {
                _uiState.update {
                    it.copy(
                        isLoadingMedia = false,
                        mediaType = StudioMediaType.None,
                        mediaUri = null
                    )
                }
                showToast("Could not open selected image.")
                return@launch
            }

            val enhanced = SmartHdrPhotoEngine.applySmartHdr(
                decoded,
                _uiState.value.smartHdrIntensity
            )
            _uiState.update {
                it.copy(
                    originalBitmap = decoded,
                    enhancedBitmap = enhanced,
                    isLoadingMedia = false
                )
            }
        }
    }

    fun onSliderChanged(value: Float) {
        val clamped = value.coerceIn(0f, 100f)
        _uiState.update { it.copy(smartHdrIntensity = clamped) }

        if (_uiState.value.mediaType == StudioMediaType.Image) {
            val source = _uiState.value.originalBitmap ?: return
            hdrRenderJob?.cancel()
            hdrRenderJob = viewModelScope.launch {
                val processed = SmartHdrPhotoEngine.applySmartHdr(source, clamped)
                _uiState.update { it.copy(enhancedBitmap = processed) }
            }
        }
    }

    fun onCompareOriginalChanged(comparing: Boolean) {
        _uiState.update { it.copy(isComparingOriginal = comparing) }
    }

    @OptIn(UnstableApi::class)
    fun onExportClicked(activity: Activity) {
        val state = _uiState.value
        val uri = state.mediaUri ?: return
        if (state.isExporting || state.mediaType == StudioMediaType.None) return

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportProgress = 0) }
            try {
                // 3-tier AdMob cascade (Priority 1 -> 2 -> 3, strict 3s max wait)
                adMobManager.showExportAdWithTimeout(activity)

                val savedUri = when (state.mediaType) {
                    StudioMediaType.Image -> {
                        _uiState.update { it.copy(exportProgress = 35) }
                        val result = SmartHdrPhotoEngine.exportPhotoToGallery(
                            context = appContext,
                            sourceUri = uri,
                            intensityPercent = state.smartHdrIntensity
                        )
                        _uiState.update { it.copy(exportProgress = 100) }
                        result
                    }
                    StudioMediaType.Video -> {
                        VideoExportManager.exportVideoToGallery(
                            context = appContext,
                            sourceUri = uri,
                            intensityPercent = state.smartHdrIntensity,
                            onProgress = { pct ->
                                _uiState.update { it.copy(exportProgress = pct) }
                            }
                        )
                    }
                    StudioMediaType.None -> null
                }

                if (savedUri != null) {
                    showToast("Saved to Gallery!")
                } else {
                    showToast("Export finished.")
                }
            } catch (_: Exception) {
                showToast("Could not export media. Please try another file.")
            } finally {
                _uiState.update { it.copy(isExporting = false, exportProgress = 0) }
            }
        }
    }

    private fun showToast(message: String) {
        toastJob?.cancel()
        _uiState.update { it.copy(statusToast = message) }
        toastJob = viewModelScope.launch {
            delay(2_800L)
            _uiState.update { it.copy(statusToast = null) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectivityMonitor.release()
    }
}
