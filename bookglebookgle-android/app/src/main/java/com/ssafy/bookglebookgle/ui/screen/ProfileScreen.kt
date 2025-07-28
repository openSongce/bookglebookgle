package com.ssafy.bookglebookgle.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.ui.theme.BookgleBookgleTheme
import com.ssafy.bookglebookgle.ui.component.CustomTopAppBar
import com.ssafy.bookglebookgle.util.ScreenSize

@Composable
fun ProfileScreen() {

    val profileImageSize = ScreenSize.width * 0.3f
    val sectionSpacing = ScreenSize.height * 0.03f

    CustomTopAppBar(
        title = "my_page",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ScreenSize.width * 0.08f)
            .padding(top = ScreenSize.height * 0.05f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {


        // 프로필 이미지
        Image(
            painter = painterResource(id = R.drawable.login_logo), // TODO: 프로필 이미지로 교체
            contentDescription = "프로필 이미지",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(profileImageSize)
                .background(Color.LightGray, CircleShape)
                .padding(4.dp)
        )

        Spacer(modifier = Modifier.height(ScreenSize.height * 0.02f))

        // 닉네임
        Text("북글이", fontSize = ScreenSize.width.value.times(0.06f).sp)

        Spacer(modifier = Modifier.height(sectionSpacing))

        // 메뉴 항목
        ProfileItem("내 정보 수정") { /* TODO */ }
        ProfileItem("참여한 모임") { /* TODO */ }
        ProfileItem("앱 설정") { /* TODO */ }
        ProfileItem("로그아웃") { /* TODO */ }
    }
}


@Composable
fun ProfileItem(label: String, onClick: () -> Unit) {
    val config = LocalConfiguration.current
    val screenWidth = config.screenWidthDp.dp
    val screenHeight = config.screenHeightDp.dp

    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = screenHeight * 0.01f)
    ) {
        Text(
            text = label,
            fontSize = screenWidth.value.times(0.045f).sp,
            color = Color.Black,
            modifier = Modifier.align(Alignment.CenterVertically)
        )
    }
}


@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ProfileScreenPreview() {
    BookgleBookgleTheme {
        ProfileScreen()
    }
}

