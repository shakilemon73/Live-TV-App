package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    var startAnimation by remember { mutableStateOf(false) }

    // Cinematic Staggered Animations
    val alphaAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1500, easing = EaseInOutCubic),
        label = "alpha"
    )

    val scaleAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1.0f else 0.82f,
        animationSpec = tween(durationMillis = 1600, easing = EaseOutBack),
        label = "scale"
    )

    val translationYAnim by animateFloatAsState(
        targetValue = if (startAnimation) 0f else 50f,
        animationSpec = tween(durationMillis = 1400, easing = EaseOutCubic),
        label = "translationY"
    )

    // Cinematic Letter Spacing Expansion Anim (starts tight, expands gracefully)
    val letterSpacingAnim by animateFloatAsState(
        targetValue = if (startAnimation) 5f else 1f,
        animationSpec = tween(durationMillis = 1800, easing = EaseOutQuart),
        label = "letterSpacing"
    )

    // Pulse animation for the ambient backlight
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_pulse")
    val backlightGlowScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "backlight_scale"
    )
    val backlightGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.26f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "backlight_alpha"
    )

    // Shimmer highlight effect rotation for the logo container
    val shimmerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_rotation"
    )

    // Live streaming connection status ticker
    val statusTicks = remember {
        listOf(
            "INITIATING SECURE VIDEO PIPELINE...",
            "TUNING LOW-LATENCY CACHE STRATEGY...",
            "OPTIMIZING STREAM INTEGRITY...",
            "COMPILING DIGITAL BROADCAST INTERFACE..."
        )
    }
    var currentTickIndex by remember { mutableStateOf(0) }
    var progressVal by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        startAnimation = true
        
        // Progress bar simulation synced perfectly with our premium ticks
        for (i in 0..100) {
            progressVal = i / 100f
            if (i == 25) currentTickIndex = 1
            if (i == 55) currentTickIndex = 2
            if (i == 80) currentTickIndex = 3
            delay(20) // total ~2000ms
        }
        
        delay(300) // visual completion pause
        onNavigateToHome()
    }

    // Modern cinematic metallic background brush
    val darkMetallicGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF09080B),
            Color(0xFF0F0E13),
            Color(0xFF14131A)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(darkMetallicGradient),
        contentAlignment = Alignment.Center
    ) {
        // Living Ambient Backlight behind the logo
        Box(
            modifier = Modifier
                .size(340.dp)
                .graphicsLayer {
                    alpha = backlightGlowAlpha * alphaAnim
                    scaleX = backlightGlowScale * scaleAnim
                    scaleY = backlightGlowScale * scaleAnim
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF9E86FF),
                            Color(0xFF6C3DFE).copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .widthIn(max = 480.dp)
                .padding(horizontal = 32.dp, vertical = 24.dp)
                .graphicsLayer {
                    alpha = alphaAnim
                    scaleX = scaleAnim
                    scaleY = scaleAnim
                    translationY = translationYAnim
                }
        ) {
            // Elegant glowing card for the app icon
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .border(
                        border = BorderStroke(
                            width = 1.5.dp,
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color(0xFF9E86FF),
                                    Color(0xFF381E72),
                                    Color(0xFFBB86FC),
                                    Color(0xFF03DAC6),
                                    Color(0xFF9E86FF)
                                )
                            )
                        ),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                val hasCustomLogo = remember {
                    try {
                        R.drawable.live_stream_logo_1782503081231 != 0
                    } catch (e: Throwable) {
                        false
                    }
                }

                if (hasCustomLogo) {
                    Image(
                        painter = painterResource(id = R.drawable.live_stream_logo_1782503081231),
                        contentDescription = "Live Stream Hub Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher),
                        contentDescription = "App Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF0F0E13))
                    )
                }

                // Glassmorphic light beam / shimmer overlay sweep
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = shimmerRotation
                        }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0f),
                                        Color.White.copy(alpha = 0.08f),
                                        Color.White.copy(alpha = 0f)
                                    )
                                ),
                                blendMode = BlendMode.SrcAtop
                            )
                        }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Cinematic brand title
            Text(
                text = "LIVE STREAM HUB",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = letterSpacingAnim.sp,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.graphicsLayer {
                    // Subtle dynamic perspective scale based on animated spacing
                    scaleX = 1f + (letterSpacingAnim / 100f)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // High-end subtitle
            Text(
                text = "ULTRALOW LATENCY TV PORTAL",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFD0BCFF).copy(alpha = 0.85f),
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(72.dp))

            // Premium Sleek Gradient Progress Indicator
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressVal)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF6C3DFE),
                                    Color(0xFF9E86FF),
                                    Color(0xFF03DAC6)
                                )
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Modern High-Tech Status Check Ticker (Smooth Crossfade Animation)
            AnimatedContent(
                targetState = statusTicks[currentTickIndex],
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) with fadeOut(animationSpec = tween(300))
                },
                label = "status_ticker"
            ) { tick ->
                Text(
                    text = tick,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.45f),
                    letterSpacing = 1.2.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Version credit and premium status lines at bottom
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .graphicsLayer { alpha = alphaAnim }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color(0xFF03DAC6), CircleShape)
                )
                Text(
                    text = "ALL BROADCASTS OPERATIONAL",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF03DAC6).copy(alpha = 0.75f),
                    letterSpacing = 1.5.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "VERSION 1.0.4 • HIGH SPEC ENGINE",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.25f),
                letterSpacing = 2.sp
            )
        }
    }
}
