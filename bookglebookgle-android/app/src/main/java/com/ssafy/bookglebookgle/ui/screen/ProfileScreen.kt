package com.ssafy.bookglebookgle.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.navigation.Screen
import com.ssafy.bookglebookgle.ui.component.CustomTopAppBar
import com.ssafy.bookglebookgle.util.ScreenSize
import com.ssafy.bookglebookgle.viewmodel.ProfileViewModel

@Composable
fun RatingStatisticItem(label: String, rating: Float, modifier: Modifier = Modifier) {
    val screenW = ScreenSize.width
    val screenH = ScreenSize.height

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ){
            Text(
                text = label,
                fontSize = screenW.value.times(0.032f).sp,
                color = Color(0xFF8D7E6E),
                fontWeight = FontWeight.Normal
            )

            Icon(
                painter = painterResource(id = R.drawable.ic_star),
                contentDescription = "Rating Icon",
                tint = Color.Unspecified, // 금색
                modifier = Modifier.size(screenW * 0.05f)
            )
        }


        Spacer(modifier = Modifier.height(screenH * 0.005f))

        Text(
            text = rating.toString(),
            fontSize = screenW.value.times(0.065f).sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}



@Composable
fun ProfileScreen(navController: NavHostController, viewModel: ProfileViewModel = hiltViewModel()) {
    val profileImageSize = ScreenSize.width * 0.3f
    val iconSize = profileImageSize * 0.25f

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        val logoutDone by viewModel.logoutCompleted.collectAsState()
        val profileImageUrl by viewModel.profileImageUrl.collectAsState()
        val nickname by viewModel.nickname.collectAsState()
        val email by viewModel.email.collectAsState()
        val averageScore by viewModel.averageScore.collectAsState()

        if (logoutDone) {
            LaunchedEffect(Unit) {
                navController.navigate(Screen.LoginScreen.route) {
                    popUpTo(Screen.MainScreen.route) { inclusive = true }
                }
            }
        }

        CustomTopAppBar(title = "my_page", navController)

        Spacer(modifier = Modifier.height(ScreenSize.height * 0.03f))

        // 프로필 이미지 + 연필 아이콘
        Box(
            modifier = Modifier.size(profileImageSize),
            contentAlignment = Alignment.BottomEnd
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(profileImageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "프로필 이미지",
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.profile_example), // 로딩 중 기본 이미지
                error = painterResource(R.drawable.profile_image),       // 로딩 실패 시
                fallback = painterResource(R.drawable.profile_image),    // null일 경우
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.LightGray)
            )


            Box(
                modifier = Modifier
                    .size(iconSize)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clip(CircleShape)
                    .background(Color(0xFFE0E0E0))
                    .padding(1.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(iconSize * 0.2f),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_edit),
                    contentDescription = "수정",
                    tint = Color.Black,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(ScreenSize.height * 0.015f))

        Text(
            nickname ?: "닉네임 없음",
            fontSize = ScreenSize.width.value.times(0.05f).sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            email ?: "이메일 없음",
            fontSize = ScreenSize.width.value.times(0.035f).sp,
            color = Color(0xFF8D7E6E)
        )
        Spacer(modifier = Modifier.height(ScreenSize.height * 0.03f))
        // 구분선
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .padding(horizontal = ScreenSize.width * 0.08f)
                .background(Color(0xFFE5E5E5))
        )

        Spacer(modifier = Modifier.height(ScreenSize.height * 0.03f))

        // 버튼들을 가로로 배치
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenSize.width * 0.08f),
            horizontalArrangement = Arrangement.spacedBy(ScreenSize.width * 0.04f)
        ) {
            ProfileItemHorizontal("내 책장", Modifier.weight(1f)) { }
            ProfileItemHorizontal("로그아웃", Modifier.weight(1f)) {
                viewModel.logout()
            }
        }

        Spacer(modifier = Modifier.height(ScreenSize.height * 0.04f))

        // 내 통계 섹션
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenSize.width * 0.08f)
        ) {
            Text(
                "내 통계",
                fontSize = ScreenSize.width.value.times(0.060f).sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(ScreenSize.height * 0.02f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ScreenSize.width * 0.08f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 원형 그래프
                CircularProgressChart(
                    totalMeetings = 15,
                    completedMeetings = 12,
                    modifier = Modifier.weight(1.5f)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ScreenSize.height * 0.025f)
                ) {
                    RatingStatisticItem(
                        label = "내 평점",
                        rating = averageScore
                    )
                    SimpleStatisticItem(
                        label = "총 모임 시간",
                        value = "13시간"
                    )
                }
            }
        }
    }
}

@Composable
fun CircularProgressChart(
    totalMeetings: Int,
    completedMeetings: Int,
    modifier: Modifier = Modifier
) {
    val completionRate = if (totalMeetings > 0) completedMeetings.toFloat() / totalMeetings else 0f
    val screenW = ScreenSize.width

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(screenW * 0.35f)
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val strokeWidth = 12.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)

                // 배경 원
                drawCircle(
                    color = Color(0xFFE5E5E5),
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // 진행률 원호
                if (completionRate > 0f) {
                    drawArc(
                        color = Color(0xFF5B7FFF),
                        startAngle = -90f,
                        sweepAngle = 360f * completionRate,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        topLeft = androidx.compose.ui.geometry.Offset(
                            center.x - radius,
                            center.y - radius
                        ),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                    )
                }
            }

            // 중앙 텍스트
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${(completionRate * 100).toInt()}%",
                    fontSize = screenW.value.times(0.045f).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = "완료율",
                    fontSize = screenW.value.times(0.025f).sp,
                    color = Color(0xFF8D7E6E)
                )
            }
        }

        Spacer(modifier = Modifier.height(ScreenSize.height * 0.01f))

        // 범례
        Row(
            horizontalArrangement = Arrangement.spacedBy(ScreenSize.width * 0.03f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(
                color = Color(0xFF5B7FFF),
                label = "완료",
                value = completedMeetings.toString()
            )
            LegendItem(
                color = Color(0xFFE5E5E5),
                label = "미완료",
                value = (totalMeetings - completedMeetings).toString()
            )
        }
    }
}

@Composable
fun LegendItem(
    color: Color,
    label: String,
    value: String
) {
    val screenW = ScreenSize.width

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            text = "$label $value",
            fontSize = screenW.value.times(0.025f).sp,
            color = Color(0xFF8D7E6E)
        )
    }
}

@Composable
fun ProfileItemHorizontal(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val screenW = ScreenSize.width
    val screenH = ScreenSize.height

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(screenW * 0.05f))
            .background(Color(0xFFF5F2F1))
            .clickable { onClick() }
            .padding(vertical = screenH * 0.018f),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = screenW.value.times(0.04f).sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black
        )
    }
}

@Composable
fun SimpleStatisticItem(label: String, value: String, modifier: Modifier = Modifier) {
    val screenW = ScreenSize.width
    val screenH = ScreenSize.height

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            fontSize = screenW.value.times(0.032f).sp,
            color = Color(0xFF8D7E6E),
            fontWeight = FontWeight.Normal
        )

        Spacer(modifier = Modifier.height(screenH * 0.005f))

        Text(
            text = value,
            fontSize = screenW.value.times(0.065f).sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}