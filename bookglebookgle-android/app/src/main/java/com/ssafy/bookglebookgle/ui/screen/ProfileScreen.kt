package com.ssafy.bookglebookgle.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
fun ProfileScreen(navController: NavHostController, viewModel: ProfileViewModel = hiltViewModel()) {
    val profileImageSize = ScreenSize.width * 0.3f
    val iconSize = profileImageSize * 0.25f

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        val logoutDone by viewModel.logoutCompleted.collectAsState()

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
                    .data(viewModel.profileImageUrl)
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
                    painter = painterResource(id = R.drawable.ic_edit), // 연필 아이콘
                    contentDescription = "수정",
                    tint = Color.Black,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(ScreenSize.height * 0.015f))

        Text(
            viewModel.nickname ?: "닉네임 없음",
            fontSize = ScreenSize.width.value.times(0.05f).sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            viewModel.email ?: "이메일 없음",
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
            ProfileItemHorizontal("내 보상", Modifier.weight(1f)) { }
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

            // 통계 항목들을 2x2 그리드로 배치
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ScreenSize.width * 0.06f)
            ) {
                StatisticItem(
                    label = "총 모임수",
                    value = "15",
                    modifier = Modifier.weight(1f)
                )
                StatisticItem(
                    label = "총 모임 시간",
                    value = "30",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(ScreenSize.height * 0.03f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ScreenSize.width * 0.06f)
            ) {
                StatisticItem(
                    label = "참여율",
                    value = "85%",
                    modifier = Modifier.weight(1f)
                )
                StatisticItem(
                    label = "독서량",
                    value = "12",
                    modifier = Modifier.weight(1f)
                )
            }
        }
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
fun StatisticItem(label: String, value: String, modifier: Modifier = Modifier) {
    val screenW = ScreenSize.width
    val screenH = ScreenSize.height

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            fontSize = screenW.value.times(0.035f).sp,
            color = Color(0xFF8D7E6E),
            fontWeight = FontWeight.Normal
        )

        Spacer(modifier = Modifier.height(screenH * 0.005f))

        Text(
            text = value,
            fontSize = screenW.value.times(0.08f).sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(screenH * 0.008f))

        // 진행률 바 (파란색)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color(0xFFE5E5E5), RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f) // 진행률에 따라 조정
                    .fillMaxHeight()
                    .background(Color(0xFF5B7FFF), RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
fun ProfileItem(label: String, onClick: () -> Unit) {
    val screenW = ScreenSize.width
    val screenH = ScreenSize.height

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = screenH * 0.01f, horizontal = screenW * 0.08f)
            .clip(RoundedCornerShape(screenW * 0.05f))
            .background(Color(0xFFF5F2F1))
            .clickable { onClick() }
            .padding(vertical = screenH * 0.018f), // 버튼 높이
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