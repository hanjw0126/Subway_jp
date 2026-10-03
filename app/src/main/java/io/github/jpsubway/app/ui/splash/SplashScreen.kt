package io.github.jpsubway.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jpsubway.app.R

val SplashNavy = Color(0xFF0B1F4B)
private val SplashLines = listOf(Color(0xFFFF922B), Color(0xFF40C057), Color(0xFFF06595), Color(0xFF74C0FC))

/** 앱 시작 로딩 화면: 로고 확대·페이드 인 + 역 5개를 지나가는 노선 진행 표시 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing)) }
    val loop = rememberInfiniteTransition(label = "splash")
    val progress by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1100, easing = LinearEasing), RepeatMode.Restart),
        label = "train",
    )
    Box(modifier.fillMaxSize().background(SplashNavy), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(200.dp).graphicsLayer {
                    val s = 0.8f + 0.2f * enter.value
                    scaleX = s
                    scaleY = s
                    alpha = enter.value
                },
            )
            Text(
                stringResource(R.string.app_name),
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer {
                    alpha = enter.value
                    translationY = (1f - enter.value) * 40f
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "WORLD WIDE METRO",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                letterSpacing = 3.sp,
                modifier = Modifier.graphicsLayer { alpha = enter.value },
            )
            Spacer(Modifier.height(36.dp))
            Canvas(Modifier.width(168.dp).height(18.dp)) {
                val y = size.height / 2
                val lw = 3.dp.toPx()
                val x = size.width * progress
                drawLine(Color.White.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), lw, StrokeCap.Round)
                drawLine(SplashLines[0], Offset(0f, y), Offset(x, y), lw, StrokeCap.Round)
                for (i in 0..4) {
                    val c = Offset(size.width * i / 4f, y)
                    val passed = c.x <= x
                    drawCircle(if (passed) Color.White else SplashNavy, 5.dp.toPx(), c)
                    drawCircle(
                        if (passed) SplashLines[i % SplashLines.size] else Color.White.copy(alpha = 0.35f),
                        5.dp.toPx(),
                        c,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }
        Text(
            "Data: ODPT · 비공식 앱",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp),
        )
    }
}
