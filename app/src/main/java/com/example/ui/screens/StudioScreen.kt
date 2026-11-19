package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.hdr.SmartHdrGlEffect
import com.example.ui.GBookUiState
import com.example.ui.StudioMediaType
import com.example.ui.theme.ExportGreen
import com.example.ui.theme.ExportGreenDark
import com.example.ui.theme.GoldReflection
import com.example.ui.theme.PlusJakartaFamily
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SapphireBright
import com.example.ui.theme.SapphireGlow
import com.example.ui.theme.SapphirePrimary
import com.example.ui.theme.SpaceGroteskFamily
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioDarkBg
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun StudioScreen(
    state: GBookUiState,
    onBack: () -> Unit,
    onMediaPicked: (Uri) -> Unit,
    onSliderChanged: (Float) -> Unit,
    onCompareChanged: (Boolean) -> Unit,
    onExportClick: () -> Unit
) {
    BackHandler(enabled = !state.isExporting) {
        onBack()
    }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Zero-permission full Android Gallery & Album/Folder Picker (Camera, WhatsApp, Downloads, Screenshots)
    val fullGalleryLauncher = rememberLauncherForActivityResult(
        contract = FullGalleryPickerContract()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onMediaPicked(uri)
        }
    }

    val fallbackDocPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onMediaPicked(uri)
        }
    }

    val launchGallery = {
        runCatching {
            fullGalleryLauncher.launch(Unit)
        }.onFailure {
            fallbackDocPickerLauncher.launch(arrayOf("image/*", "video/*"))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioDarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: "G-Book Camera" on left, bright green "Export" button on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(enabled = !state.isExporting) { onBack() }
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.gbook_camera_icon_1795066862092),
                        contentDescription = "G-Book Camera",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, SapphireBright.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(id = R.string.app_name),
                        color = PureWhite,
                        fontFamily = PlusJakartaFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                }

                val canExport = state.mediaType != StudioMediaType.None && !state.isExporting && !state.isLoadingMedia
                Button(
                    onClick = onExportClick,
                    enabled = canExport,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ExportGreen,
                        contentColor = PureBlack,
                        disabledContainerColor = ExportGreen.copy(alpha = 0.25f),
                        disabledContentColor = PureWhite.copy(alpha = 0.4f)
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("export_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.studio_export),
                        fontFamily = PlusJakartaFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
            }

            // Center Preview Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF040812))
                    .border(1.dp, StudioBorder.copy(alpha = 0.7f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    state.isLoadingMedia -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = SapphireBright,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Preparing 48MP Smart HDR...",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    state.mediaType == StudioMediaType.Image && state.originalBitmap != null -> {
                        val displayBitmap = if (state.isComparingOriginal || state.enhancedBitmap == null) {
                            state.originalBitmap
                        } else {
                            state.enhancedBitmap
                        }
                        Image(
                            bitmap = displayBitmap.asImageBitmap(),
                            contentDescription = "Photo preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("photo_preview_image")
                        )

                        // Floating Before/After status pill + Gallery change pill
                        StudioOverlayPills(
                            isComparing = state.isComparingOriginal,
                            intensityPercent = state.smartHdrIntensity,
                            onChangeMediaClick = { launchGallery() },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .padding(14.dp)
                        )
                    }

                    state.mediaType == StudioMediaType.Video && state.mediaUri != null -> {
                        SmartHdrVideoPreview(
                            uri = state.mediaUri,
                            intensityPercent = state.smartHdrIntensity,
                            isComparingOriginal = state.isComparingOriginal,
                            modifier = Modifier.fillMaxSize()
                        )

                        StudioOverlayPills(
                            isComparing = state.isComparingOriginal,
                            intensityPercent = state.smartHdrIntensity,
                            onChangeMediaClick = { launchGallery() },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .padding(14.dp)
                        )
                    }

                    else -> {
                        // Sleek empty viewfinder with "Upload from Gallery" CTA
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ViewfinderReticles()
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(StudioSurfaceElevated)
                                        .border(1.dp, SapphireBright.copy(alpha = 0.35f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = SapphireBright,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(18.dp))
                                Text(
                                    text = "iPhone 18 Pro Max Smart HDR",
                                    color = PureWhite,
                                    fontFamily = PlusJakartaFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Supports 50MP / 108MP photos & full HD/4K videos",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(22.dp))
                                Button(
                                    onClick = { launchGallery() },
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = PureWhite
                                    ),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .height(54.dp)
                                        .shadow(
                                            elevation = 16.dp,
                                            shape = RoundedCornerShape(18.dp),
                                            ambientColor = SapphireGlow,
                                            spotColor = SapphireBright
                                        )
                                        .background(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(SapphirePrimary, SapphireGlow)
                                            ),
                                            shape = RoundedCornerShape(18.dp)
                                        )
                                        .testTag("upload_gallery_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PhotoLibrary,
                                        contentDescription = null,
                                        tint = PureWhite,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = stringResource(id = R.string.studio_upload),
                                        fontFamily = PlusJakartaFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = PureWhite
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Studio Controls: Single Master Slider (0%..100%, default 70%) + Hold to Compare
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(StudioSurface)
                    .border(1.dp, StudioBorder.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Smart HDR Master",
                            color = PureWhite,
                            fontFamily = PlusJakartaFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "f/1.48 Shadows • f/4.0 Skies • 48MP Clarity",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(StudioSurfaceElevated)
                            .border(1.dp, SapphireBright.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${state.smartHdrIntensity.roundToInt()}%",
                            color = SapphireBright,
                            fontFamily = SpaceGroteskFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.testTag("slider_percentage_label")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Slider(
                    value = state.smartHdrIntensity,
                    onValueChange = { onSliderChanged(it) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = PureWhite,
                        activeTrackColor = SapphireBright,
                        inactiveTrackColor = StudioSurfaceElevated
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("smart_hdr_slider")
                )

                Spacer(modifier = Modifier.height(10.dp))

                val hasMedia = state.mediaType != StudioMediaType.None
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (state.isComparingOriginal) {
                                GoldReflection.copy(alpha = 0.22f)
                            } else {
                                StudioSurfaceElevated
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = if (state.isComparingOriginal) GoldReflection else StudioBorder,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .pointerInput(hasMedia) {
                            if (!hasMedia) return@pointerInput
                            detectTapGestures(
                                onPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onCompareChanged(true)
                                    tryAwaitRelease()
                                    onCompareChanged(false)
                                }
                            )
                        }
                        .testTag("hold_compare_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Compare,
                            contentDescription = null,
                            tint = if (state.isComparingOriginal) GoldReflection else PureWhite,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.studio_hold_compare),
                            color = if (state.isComparingOriginal) GoldReflection else PureWhite,
                            fontFamily = PlusJakartaFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Export Progress Overlay
        AnimatedVisibility(
            visible = state.isExporting,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PureBlack.copy(alpha = 0.78f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(StudioSurface)
                        .border(1.dp, StudioBorder, RoundedCornerShape(24.dp))
                        .padding(horizontal = 32.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = ExportGreen,
                        strokeWidth = 3.5.dp,
                        modifier = Modifier.size(46.dp)
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = if (state.exportProgress > 0) {
                            "Exporting Smart HDR... ${state.exportProgress}%"
                        } else {
                            "Preparing GPU Export..."
                        },
                        color = PureWhite,
                        fontFamily = PlusJakartaFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Preserving 100% quality & audio",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Saved / Status Confirmation Pill Toast
        AnimatedVisibility(
            visible = state.statusToast != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 74.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(ExportGreenDark)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = PureWhite,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = state.statusToast.orEmpty(),
                    color = PureWhite,
                    fontFamily = PlusJakartaFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun StudioOverlayPills(
    isComparing: Boolean,
    intensityPercent: Float,
    onChangeMediaClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(PureBlack.copy(alpha = 0.68f))
                .border(
                    width = 1.dp,
                    color = if (isComparing) GoldReflection else SapphireBright.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(99.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (isComparing) {
                    "BEFORE • ORIGINAL"
                } else {
                    "SMART HDR • ${intensityPercent.roundToInt()}%"
                },
                color = if (isComparing) GoldReflection else PureWhite,
                fontFamily = SpaceGroteskFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(PureBlack.copy(alpha = 0.68f))
                .border(1.dp, StudioBorder, RoundedCornerShape(99.dp))
                .clickable(onClick = onChangeMediaClick)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("change_media_button")
        ) {
            Text(
                text = "Change Media",
                color = PureWhite,
                fontFamily = PlusJakartaFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ViewfinderReticles() {
    Canvas(modifier = Modifier.fillMaxSize().padding(22.dp)) {
        val len = 26.dp.toPx()
        val stroke = 2.dp.toPx()
        val color = SapphireBright.copy(alpha = 0.35f)
        // Top-left
        drawLine(color, Offset(0f, 0f), Offset(len, 0f), stroke)
        drawLine(color, Offset(0f, 0f), Offset(0f, len), stroke)
        // Top-right
        drawLine(color, Offset(size.width, 0f), Offset(size.width - len, 0f), stroke)
        drawLine(color, Offset(size.width, 0f), Offset(size.width, len), stroke)
        // Bottom-left
        drawLine(color, Offset(0f, size.height), Offset(len, size.height), stroke)
        drawLine(color, Offset(0f, size.height), Offset(0f, size.height - len), stroke)
        // Bottom-right
        drawLine(color, Offset(size.width, size.height), Offset(size.width - len, size.height), stroke)
        drawLine(color, Offset(size.width, size.height), Offset(size.width, size.height - len), stroke)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun SmartHdrVideoPreview(
    uri: Uri,
    intensityPercent: Float,
    isComparingOriginal: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestIntensity by rememberUpdatedState(intensityPercent)
    val latestComparing by rememberUpdatedState(isComparingOriginal)
    var isPlaying by remember { mutableStateOf(true) }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
        }
    }

    val glEffect = remember {
        SmartHdrGlEffect(
            intensityProvider = { latestIntensity },
            compareOriginalProvider = { latestComparing }
        )
    }

    LaunchedEffect(uri) {
        exoPlayer.setVideoEffects(listOf(glEffect))
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        isPlaying = true
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier.clickable {
            if (exoPlayer.isPlaying) {
                exoPlayer.pause()
                isPlaying = false
            } else {
                exoPlayer.play()
                isPlaying = true
            }
        },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = exoPlayer
                }
            },
            update = { view ->
                if (view.player !== exoPlayer) {
                    view.player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = !isPlaying,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(PureBlack.copy(alpha = 0.65f))
                    .border(1.dp, PureWhite.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Play or Pause Video",
                    tint = PureWhite,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

private class FullGalleryPickerContract : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
        val getContentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(getContentIntent, "Upload from Gallery")
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (resultCode == Activity.RESULT_OK) intent?.data else null
    }
}
