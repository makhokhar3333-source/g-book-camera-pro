package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.ConnectionBannerState
import com.example.ui.theme.ExportGreenDark
import com.example.ui.theme.GoldReflection
import com.example.ui.theme.OceanDeepTop
import com.example.ui.theme.OceanMidnightBottom
import com.example.ui.theme.OfflineRed
import com.example.ui.theme.PlusJakartaFamily
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SapphireBright
import com.example.ui.theme.SapphireGlow
import com.example.ui.theme.SapphirePrimary
import com.example.ui.theme.TextSecondary

@Composable
fun WelcomeScreen(
    bannerState: ConnectionBannerState,
    onEditingClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "welcomeAura")
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraScale"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.65f),
        label = "editingBtnScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(OceanDeepTop, Color(0xFF09132B), OceanMidnightBottom)
                )
            )
    ) {
        // Subtle animated glowing camera lens aura behind hero emblem
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.37f)
            val baseRadius = size.minDimension * 0.44f * auraScale

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        SapphirePrimary.copy(alpha = 0.28f),
                        SapphireBright.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.45f
                ),
                radius = baseRadius * 1.45f,
                center = center
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        GoldReflection.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(center.x + baseRadius * 0.25f, center.y - baseRadius * 0.22f),
                    radius = baseRadius * 0.8f
                ),
                radius = baseRadius * 0.8f,
                center = Offset(center.x + baseRadius * 0.25f, center.y - baseRadius * 0.22f)
            )
        }

        // Main centered luxury welcome content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 500.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(144.dp)
                        .shadow(
                            elevation = 32.dp,
                            shape = RoundedCornerShape(36.dp),
                            ambientColor = SapphirePrimary,
                            spotColor = SapphireBright
                        )
                        .clip(RoundedCornerShape(36.dp))
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    GoldReflection.copy(alpha = 0.7f),
                                    SapphireBright.copy(alpha = 0.65f),
                                    SapphirePrimary.copy(alpha = 0.4f)
                                )
                            ),
                            shape = RoundedCornerShape(36.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.gbook_camera_icon_1795066862092),
                        contentDescription = "G-Book Camera Lens Emblem",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(34.dp))

                Text(
                    text = "G-BOOK CAMERA",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = SapphireBright,
                        letterSpacing = 3.sp,
                        fontSize = 12.sp
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(id = R.string.welcome_greeting),
                    color = PureWhite,
                    fontFamily = PlusJakartaFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp,
                    lineHeight = 36.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("welcome_greeting_text")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Smart HDR Studio • Pro Lens Clarity",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(44.dp))

                Button(
                    onClick = onEditingClick,
                    interactionSource = interactionSource,
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = PureWhite
                    ),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 320.dp)
                        .height(62.dp)
                        .scale(buttonScale)
                        .shadow(
                            elevation = 24.dp,
                            shape = RoundedCornerShape(22.dp),
                            ambientColor = SapphireGlow,
                            spotColor = SapphireBright
                        )
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(SapphirePrimary, SapphireGlow, SapphireBright)
                            ),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = PureWhite.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .testTag("editing_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = PureWhite,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(id = R.string.welcome_action_editing),
                        fontFamily = PlusJakartaFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = PureWhite
                    )
                }
            }
        }

        // Top Live Internet Check Banner (RED "Connect to Internet" / GREEN "Connected" for 2.5s)
        AnimatedVisibility(
            visible = bannerState != ConnectionBannerState.Hidden,
            enter = fadeIn(tween(220)) + expandVertically(tween(240)),
            exit = fadeOut(tween(220)) + shrinkVertically(tween(240)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            AnimatedContent(
                targetState = bannerState,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "bannerTransition"
            ) { state ->
                val isRed = state == ConnectionBannerState.OfflineRed
                val bannerColor = if (isRed) OfflineRed else ExportGreenDark
                val bannerText = if (isRed) {
                    stringResource(id = R.string.banner_offline)
                } else {
                    stringResource(id = R.string.banner_online)
                }
                val bannerIcon = if (isRed) Icons.Rounded.WifiOff else Icons.Rounded.CheckCircle

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bannerColor)
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .testTag(if (isRed) "banner_offline_red" else "banner_online_green"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = bannerIcon,
                            contentDescription = null,
                            tint = PureWhite,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = bannerText,
                            color = PureWhite,
                            fontFamily = PlusJakartaFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}
