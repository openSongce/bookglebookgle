package com.ssafy.bookglebookgle.ui.screen

import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.navigation.Screen
import com.ssafy.bookglebookgle.ui.theme.BookgleBookgleTheme
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
            Image(
                painter = painterResource(id = R.drawable.login_logo), // 사용자 프로필 이미지
                contentDescription = "프로필 이미지",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.LightGray)
            )

            Box(
                modifier = Modifier
                    .size(iconSize)
                    .clip(CircleShape)
                    .background(Color(0xFFEFEAE1)) // 연한 회색
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

        Text("송진우", fontSize = ScreenSize.width.value.times(0.05f).sp, fontWeight = FontWeight.Bold)
        Text("gews30025@naver.com", fontSize = ScreenSize.width.value.times(0.035f).sp, color = Color(0xFF8D7E6E))

        Spacer(modifier = Modifier.height(ScreenSize.height * 0.03f))

        ProfileItem("내 보상") { }
        ProfileItem("로그 아웃") {
            viewModel.logout()
        }

        Spacer(modifier = Modifier.height(ScreenSize.height * 0.04f))

        Text(
            "내 통계",
            fontSize = ScreenSize.width.value.times(0.045f).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenSize.width * 0.08f)
        )
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

