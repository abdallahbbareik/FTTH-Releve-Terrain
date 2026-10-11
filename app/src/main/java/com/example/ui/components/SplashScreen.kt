package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay

/**
 * Splash : fond marine (identique au splash système), logo AB animé,
 * puis « Relevé terrain » et « FTTH » en fondu.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    var showText by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300L)
        showText = true
    }
    val textAlpha by animateFloatAsState(
        targetValue = if (showText) 1f else 0f,
        animationSpec = tween(500),
        label = "splash_text_alpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF062A3D))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedLogo(modifier = Modifier.size(160.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.splash_title),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.alpha(textAlpha)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.splash_subtitle),
            color = Color(0xFF4FC3F7),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 3.sp,
            modifier = Modifier.alpha(textAlpha)
        )
    }
}
