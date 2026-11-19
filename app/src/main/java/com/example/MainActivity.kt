package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.screens.NotificationGateScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.GBookCameraTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            GBookCameraTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                // Android 13+ POST_NOTIFICATIONS launcher (handles grant or dismiss silently)
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { _ ->
                    viewModel.onAllowNotificationsClicked()
                }

                AnimatedContent(
                    targetState = uiState.currentScreen,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(260)) togetherWith fadeOut(animationSpec = tween(220))
                    },
                    label = "screenFlow"
                ) { screen ->
                    when (screen) {
                        AppScreen.NotificationGate -> {
                            NotificationGateScreen(
                                onAllowClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        val granted = ContextCompat.checkSelfPermission(
                                            this@MainActivity,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (!granted) {
                                            notificationPermissionLauncher.launch(
                                                Manifest.permission.POST_NOTIFICATIONS
                                            )
                                        } else {
                                            viewModel.onAllowNotificationsClicked()
                                        }
                                    } else {
                                        viewModel.onAllowNotificationsClicked()
                                    }
                                }
                            )
                        }

                        AppScreen.Welcome -> {
                            WelcomeScreen(
                                bannerState = uiState.bannerState,
                                onEditingClick = { viewModel.onEditingClicked() }
                            )
                        }

                        AppScreen.Studio -> {
                            StudioScreen(
                                state = uiState,
                                onBack = { viewModel.navigateBackToWelcome() },
                                onMediaPicked = { uri -> viewModel.onMediaPicked(uri) },
                                onSliderChanged = { value -> viewModel.onSliderChanged(value) },
                                onCompareChanged = { comparing ->
                                    viewModel.onCompareOriginalChanged(comparing)
                                },
                                onExportClick = {
                                    viewModel.onExportClicked(this@MainActivity)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
