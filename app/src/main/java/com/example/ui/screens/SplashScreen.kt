package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    var startAnimation by remember { mutableStateOf(false) }

    val alphaAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "alpha"
    )

    val scaleAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1.05f else 0.85f,
        animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
        label = "scale"
    )

    val translationYAnim by animateFloatAsState(
        targetValue = if (startAnimation) 0f else 40f,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "translationY"
    )

    LaunchedEffect(key1 = Unit) {
        startAnimation = true
        delay(2200) // Beautiful 2.2s delay for visual immersion
        onNavigateToHome()
    }

    // Cinematic gradients
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F0E11),
            Color(0xFF131215),
            Color(0xFF1C1A22)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        // Subtle ambient glow in background
        Box(
            modifier = Modifier
                .size(320.dp)
                .graphicsLayer(alpha = alphaAnim * 0.15f)
                .scale(scaleAnim * 1.2f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFD0BCFF), Color.Transparent)
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(24.dp)
                .graphicsLayer(
                    alpha = alphaAnim,
                    scaleX = scaleAnim,
                    scaleY = scaleAnim,
                    translationY = translationYAnim
                )
        ) {
            // Elegant framed App Logo
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.04f))
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                // Try reading the custom drawable, or fallback to default ic_launcher
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
                            .clip(CircleShape)
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher),
                        contentDescription = "App Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFF131215))
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Premium cinematic typography
            Text(
                text = "LIVE STREAM HUB",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your Premium Live Broadcast Portal",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFD0BCFF).copy(alpha = 0.75f),
                letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(64.dp))

            // Premium subtle progress loading line
            LinearProgressIndicator(
                color = Color(0xFFD0BCFF),
                trackColor = Color.White.copy(alpha = 0.08f),
                modifier = Modifier
                    .width(160.dp)
                    .height(3.dp)
                    .clip(CircleShape)
            )
        }

        // Elegant version credit at the bottom
        Text(
            text = "VERSION 1.0.0 • RELEASE READY",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.35f),
            letterSpacing = 2.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
                .graphicsLayer(alpha = alphaAnim)
        )
    }
}
