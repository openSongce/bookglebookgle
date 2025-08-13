package com.ssafy.bookglebookgle.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.ssafy.bookglebookgle.viewmodel.ShelfViewModel
import kotlin.math.max

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MyBookShelfScreen(
    userId: Long,
    viewModel: ShelfViewModel = hiltViewModel()
) {
    val books by viewModel.books.collectAsState()
    val loading by viewModel.isLoading.collectAsState()

    LaunchedEffect(userId) { viewModel.load(userId) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .bookgleWoodBackground() // 나무 벽 + 책상
    ) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        // 스택/아이템 비율 (기기 무관)
        val deskHeightPx  = h * 0.30f
        val stackWidthPx  = w * 0.56f
        val thicknessPx   = h * 0.095f           // 한 권 두께(보이는 높이)
        val depthPx       = stackWidthPx * 0.58f // 탑면 깊이
        val gapPx         = thicknessPx * 0.30f  // 책 간 간격
        val bottomPadPx   = deskHeightPx * 0.40f // 책상 위 여유 패딩

        if (loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        // 책 더미 (아래에서 위로 쌓임)
        LazyColumn(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            reverseLayout = true,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(
                bottom = with(density) { bottomPadPx.toDp() }
            )
        ) {
            itemsIndexed(books, key = { _, it -> it.id }) { _, book ->
                val palette = if (book.id % 2L == 0L) BookColors.Green else BookColors.Orange
                val (tiltZ, wobbleX) = bookPose(book.id, stackWidthPx) // ✅ 책마다 다른 각도/오프셋

                BookTop3DItem(
                    title       = book.title,
                    widthPx     = stackWidthPx,
                    depthPx     = depthPx,
                    thicknessPx = thicknessPx,
                    colors      = palette,
                    tiltZ       = tiltZ,
                    wobbleX     = wobbleX,
                    modifier    = Modifier
                        .animateItemPlacement()
                        .zIndex(book.id.toFloat())
                )
                Spacer(Modifier.height(with(density) { gapPx.toDp() }))
            }
        }

        // 카테고리 탭
        var selected by remember { mutableStateOf(0) }
        SegmentedTabs(
            items = listOf("독서", "스터디", "첨삭"),
            selected = selected,
            onSelected = { selected = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = with(density) { (w * 0.08f).toDp() })
                .offset(y = with(density) { (-deskHeightPx * 0.28f).toDp() })
        )

        // CTA 버튼
        CtaButton(
            text = "지금 쌓으러 가기 !",
            onClick = { viewModel.addDummyOne("새로 읽은 책 #${books.size + 1}") },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = with(density) { (w * 0.18f).toDp() })
                .offset(y = with(density) { (-deskHeightPx * 0.02f).toDp() })
        )
    }
}

/* ───────────── 배경(나무 벽 + 책상) ───────────── */

private fun Modifier.bookgleWoodBackground(): Modifier = this.then(
    Modifier.drawBehind {
        // 벽: 세로 나무판
        val plankCount = 6
        val plankW = size.width / plankCount
        repeat(plankCount) { i ->
            val x = plankW * i
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF7A5A3F), Color(0xFF694A34))
                ),
                topLeft = Offset(x, 0f),
                size = Size(plankW, size.height)
            )
            // 위쪽 음영
            drawRect(
                color = Color.Black.copy(alpha = 0.06f),
                topLeft = Offset(x, 0f),
                size = Size(plankW, size.height * 0.06f)
            )
        }
        // 책상
        val deskH = size.height * 0.30f
        val top = size.height - deskH
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF6B4B32), Color(0xFF4A341F))
            ),
            topLeft = Offset(0f, top),
            size = Size(size.width, deskH)
        )
        // 책상 가장자리 경계
        drawLine(
            color = Color.Black.copy(alpha = 0.18f),
            start = Offset(0f, top),
            end = Offset(size.width, top),
            strokeWidth = deskH * 0.03f
        )
    }
)

/* ───────────── 색 팔레트 ───────────── */

private data class BookColors(
    val coverStart: Color,
    val coverEnd: Color,
    val band: Color,
    val page: Color,
    val pageLine: Color
) {
    companion object {
        val Green = BookColors(
            coverStart = Color(0xFF5E8D55),
            coverEnd   = Color(0xFF2F6B3C),
            band       = Color(0xFF2F6B3C),
            page       = Color(0xFFF7E3B3),
            pageLine   = Color(0xFFE2C98F)
        )
        val Orange = BookColors(
            coverStart = Color(0xFFD37936),
            coverEnd   = Color(0xFFB05B1F),
            band       = Color(0xFF8E4214),
            page       = Color(0xFFF7E3B3),
            pageLine   = Color(0xFFE2C98F)
        )
    }
}

/* ───────────── 탑면 보이는 3D 북 아이템 ───────────── */

@Composable
private fun BookTop3DItem(
    title: String,
    widthPx: Float,       // 표지 가로
    depthPx: Float,       // 탑면 깊이
    thicknessPx: Float,   // 책 두께(보이는 높이)
    colors: BookColors,
    tiltZ: Float = -8f,
    wobbleX: Float = 0f,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // 원근 파라미터
    val shrink = 0.14f                // 뒤쪽 폭 축소 비율
    val backRise = depthPx * 0.58f    // 뒤쪽 Y 상승량
    val backShiftX = depthPx * 0.18f  // 뒤쪽 X 이동량

    val containerW = widthPx + backShiftX
    val containerH = thicknessPx + backRise
    val wDp = with(density) { containerW.toDp() }
    val hDp = with(density) { containerH.toDp() }

    val rise by animateFloatAsState(
        targetValue = 0f,
        animationSpec = spring(stiffness = 700f, dampingRatio = 0.78f),
        label = "riseTop3D"
    )

    Box(
        modifier
            .width(wDp)
            .height(hDp)
            .graphicsLayer {
                rotationZ = tiltZ
                translationX = wobbleX
                cameraDistance = widthPx * 12f
                translationY = rise
            }
    ) {
        Canvas(Modifier.matchParentSize()) {
            // 기준 좌표
            val frontLeftX  = (size.width - widthPx) / 2f
            val frontRightX = frontLeftX + widthPx
            val topFrontY   = size.height - thicknessPx

            val backLeftX   = frontLeftX  + (widthPx * shrink * 0.5f) + backShiftX
            val backRightX  = frontRightX - (widthPx * shrink * 0.5f) + backShiftX
            val topBackY    = topFrontY - backRise

            val strokeW = max(size.minDimension * 0.008f, 1.2f)

            // 바닥 그림자
            val shW = size.width * 0.80f
            val shH = size.height * 0.28f
            drawOval(
                color = Color.Black.copy(alpha = 0.16f),
                topLeft = Offset((size.width - shW) / 2f, size.height - shH * 0.6f),
                size = Size(shW, shH)
            )

            // 1) 탑면(사다리꼴)
            val topPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(frontLeftX,  topFrontY)
                lineTo(frontRightX, topFrontY)
                lineTo(backRightX,  topBackY)
                lineTo(backLeftX,   topBackY)
                close()
            }
            drawPath(
                path = topPath,
                brush = Brush.linearGradient(listOf(colors.coverStart, colors.coverEnd))
            )

            // 상/하단 밴드
            val bandH = (topFrontY - topBackY) * 0.18f
            val bandInset = (frontRightX - frontLeftX) * 0.06f
            val bandTop = androidx.compose.ui.graphics.Path().apply {
                moveTo(frontLeftX + bandInset,  topFrontY - bandH)
                lineTo(frontRightX - bandInset, topFrontY - bandH)
                lineTo(backRightX  - bandInset, topBackY   - bandH)
                lineTo(backLeftX   + bandInset, topBackY   - bandH)
                close()
            }
            drawPath(path = bandTop, color = colors.band.copy(alpha = 0.85f))

            val bandBottom = androidx.compose.ui.graphics.Path().apply {
                moveTo(frontLeftX + bandInset,  topFrontY - bandH * 0.2f)
                lineTo(frontRightX - bandInset, topFrontY - bandH * 0.2f)
                lineTo(backRightX  - bandInset, topBackY   - bandH * 0.2f)
                lineTo(backLeftX   + bandInset, topBackY   - bandH * 0.2f)
                close()
            }
            drawPath(path = bandBottom, color = colors.band.copy(alpha = 0.85f))

            // 2) 앞면(두께)
            val frontFace = androidx.compose.ui.graphics.Path().apply {
                moveTo(frontLeftX,  topFrontY)
                lineTo(frontRightX, topFrontY)
                lineTo(frontRightX, topFrontY + thicknessPx)
                lineTo(frontLeftX,  topFrontY + thicknessPx)
                close()
            }
            drawPath(path = frontFace, color = colors.coverEnd.copy(alpha = 0.85f))

            // 3) 오른쪽 옆면(페이지)
            val rightFace = androidx.compose.ui.graphics.Path().apply {
                moveTo(frontRightX, topFrontY)
                lineTo(frontRightX, topFrontY + thicknessPx)
                lineTo(backRightX,  topBackY   + thicknessPx)
                lineTo(backRightX,  topBackY)
                close()
            }
            drawPath(path = rightFace, color = colors.page)

            // 페이지 결 (얇은 라인)
            repeat(6) { i ->
                val y = topBackY + thicknessPx * 0.14f + (thicknessPx * 0.72f) * (i / 5f)
                drawLine(
                    color = colors.pageLine,
                    start = Offset(frontRightX - (widthPx * 0.06f), y),
                    end   = Offset(backRightX  - (widthPx * 0.06f), y),
                    strokeWidth = thicknessPx * 0.02f
                )
            }

            // 윤곽선(카툰 라인)
            drawPath(path = topPath,   color = Color.Black.copy(alpha = 0.12f), style = Stroke(strokeW))
            drawPath(path = frontFace, color = Color.Black.copy(alpha = 0.12f), style = Stroke(strokeW))
            drawPath(path = rightFace, color = Color.Black.copy(alpha = 0.12f), style = Stroke(strokeW))
        }

        // 제목(탑면 중앙 근처)
        val centerX = with(density) { ((containerW) / 2f).toDp() }
        val centerY = with(density) { ((containerH - thicknessPx) - backRise * 0.5f).toDp() }
        Box(Modifier.fillMaxSize()) {
            Text(
                text = title,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = centerX, y = centerY)
            )
        }
    }
}

/* ───────────── 세그먼트 탭 ───────────── */

@Composable
private fun SegmentedTabs(
    items: List<String>,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val heightPx = h * 0.10f + 1f
        val shape = RoundedCornerShape(percent = 20)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { heightPx.toDp() })
                .clip(shape)
                .background(Color(0xFF704F33).copy(alpha = 0.22f))
                .padding(all = with(density) { (w * 0.02f).toDp() }),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { idx, label ->
                val selectedBg: Brush = when (idx) {
                    0 -> Brush.horizontalGradient(listOf(Color(0xFF2F6B3C), Color(0xFF5E8D55)))
                    1 -> Brush.horizontalGradient(listOf(Color(0xFF3F7F50), Color(0xFF2F6B3C)))
                    else -> Brush.horizontalGradient(listOf(Color(0xFFB05B1F), Color(0xFFD37936)))
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(shape)
                        // Brush/Color 모호성 회피 위해 항상 brush 사용
                        .background(
                            brush = if (idx == selected)
                                selectedBg
                            else
                                SolidColor(Color.Transparent),
                            shape = shape
                        )
                        .clickable { onSelected(idx) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (idx == selected) Color.White else Color(0xFFE7D6C0)
                    )
                }
            }
        }
    }
}

/* ───────────── CTA 버튼 ───────────── */

@Composable
private fun CtaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val h = constraints.maxHeight.toFloat()
        val btnH = h * 0.10f
        val shape = RoundedCornerShape(percent = 20)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { btnH.toDp() })
                .clip(shape)
                .drawBehind {
                    // 부드러운 그림자
                    drawIntoCanvas {
                        drawRoundRect(
                            color = Color.Black.copy(alpha = 0.20f),
                            topLeft = Offset(0f, size.height * 0.15f),
                            size = size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height * 0.5f)
                        )
                    }
                }
                .background(Color(0xFF634326), shape = shape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(text = text, color = Color.White)
        }
    }
}

/* ───────────── 책마다 다른 각도/오프셋 헬퍼 ───────────── */

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

// 결정적 0..1 노이즈 (id + salt 기반)
private fun noise01(key: Long, salt: Int = 0): Float {
    var x = key xor (salt.toLong() shl 32)
    // 간단 LCG + xorshift 혼합 (결정성 보장)
    x = x * 6364136223846793005L + 1442695040888963407L
    x = x xor (x ushr 33)
    val v = (x and Long.MAX_VALUE).toDouble() / Long.MAX_VALUE.toDouble()
    return v.toFloat().coerceIn(0f, 1f)
}

// 각 책 포즈(기울기/좌우 오프셋) 계산
private fun bookPose(id: Long, widthPx: Float): Pair<Float, Float> {
    // 각도: -13° ~ -5°
    val tiltZ = lerp(-13f, -5f, noise01(id, salt = 0))
    // 좌우 오프셋: [-w*0.015, w*0.015]
    val wobbleRange = widthPx * 0.015f
    val wobbleX = lerp(-wobbleRange, wobbleRange, noise01(id, salt = 1))
    return tiltZ to wobbleX
}
