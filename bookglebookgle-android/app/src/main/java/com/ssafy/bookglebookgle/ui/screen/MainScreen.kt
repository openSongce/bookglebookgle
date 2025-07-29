package com.ssafy.bookglebookgle.ui.screen

import com.ssafy.bookglebookgle.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.ssafy.bookglebookgle.ui.component.CustomTopAppBar
import com.ssafy.bookglebookgle.util.ScreenSize

@Composable
fun MainScreen(navController: NavHostController) {
    val tabs = listOf("독서", "학습", "첨삭")
    var selectedTab by remember { mutableStateOf("독서") }
    var selectedIndex by remember { mutableStateOf(0) }

    val horizontalPadding = ScreenSize.width * 0.04f
    val verticalPadding = ScreenSize.height * 0.01f
    val cardWidth = ScreenSize.width * 0.8f
    val cardHeight = ScreenSize.height * 0.22f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // 상단 앱바
        CustomTopAppBar(
            title = "main_home",
            navController = navController,
        )

        // 카테고리 추천
        Text(
            text = "카테고리",
            fontSize = ScreenSize.width.value.times(0.06f).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = horizontalPadding, top = verticalPadding)
        )

        LazyRow(
            contentPadding = PaddingValues(
                horizontal = horizontalPadding,
                vertical = verticalPadding
            )
        ) {
            items(meetingCard) { card ->
                RecommendCard(
                    title = card.first,
                    description = card.second,
                    imageRes = card.third,
                    width = cardWidth,
                    height = cardHeight,
                    rightMargin = horizontalPadding
                )
            }
        }

        Text(
            text = "카테고리별 모임",
            fontSize = ScreenSize.width.value.times(0.06f).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(
                horizontal = horizontalPadding,
                vertical = verticalPadding
            )
        )

        // 탭 선택
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = ScreenSize.width * 0.02f),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEach { tab ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = tab,
                        fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == tab) Color.Black else Color.Gray,
                        modifier = Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            selectedTab = tab
                        }
                    )
                    Spacer(modifier = Modifier.height(ScreenSize.height * 0.005f))
                    Box(
                        modifier = Modifier
                            .height(ScreenSize.height * 0.0015f)
                            .width(ScreenSize.width * 0.05f)
                            .background(if (selectedTab == tab) Color.Black else Color.Transparent)
                    )
                }
            }
        }

        HorizontalDivider(color = Color.LightGray, thickness = 1.dp)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding),
            contentPadding = PaddingValues(vertical = verticalPadding)
        ) {
            items(getMeetingsForTab(selectedTab)) { item ->
                MeetingCard(item)
            }
        }
    }
}


@Composable
fun RecommendCard(
    title: String,
    description: String,
    imageRes: Int,
    width: Dp,
    height: Dp,
    rightMargin: Dp
) {
    Card(
        modifier = Modifier
            .width(width)
            .padding(end = rightMargin),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f) // 적절한 비율
            ) {
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = width * 0.05f, vertical = height * 0.04f)
            ) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(description, fontSize = width.value.times(0.03f).sp, color = Color.Gray)
            }
        }
    }
}


@Composable
fun MeetingCard(title: String) {
    val config = LocalConfiguration.current
    val screenWidth = config.screenWidthDp.dp
    val screenHeight = config.screenHeightDp.dp

    val imageSize = screenHeight * 0.08f
    val innerPadding = screenWidth * 0.03f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(vertical = screenHeight * 0.01f),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = innerPadding, vertical = screenHeight * 0.01f)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("독서", fontSize = screenWidth.value.times(0.03f).sp, color = Color.Gray)
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    "이번 달의 독서는 인생을 변화시키는 독서입니다.",
                    fontSize = screenWidth.value.times(0.032f).sp,
                    color = Color.DarkGray
                )
                Spacer(modifier = Modifier.height(screenHeight * 0.005f))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFFF1F1F1)
                ) {
                    Text(
                        "10명",
                        modifier = Modifier
                            .padding(
                                horizontal = screenWidth * 0.03f,
                                vertical = screenHeight * 0.005f
                            ),
                        fontSize = screenWidth.value.times(0.03f).sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(screenWidth * 0.03f))
            Image(
                painter = painterResource(id = R.drawable.main_temp),
                contentDescription = null,
                modifier = Modifier
                    .size(imageSize)
                    .clip(RoundedCornerShape(screenWidth * 0.03f)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

val meetingCard = listOf(
    Triple("독서 모임", "이번 주제는 인생을 변화시키는 독서입니다.", R.drawable.main_reading),
    Triple("스터디 모임", "함께 공부하면 효과가 두 배입니다.", R.drawable.main_studying),
    Triple("첨삭 모임", "서류 피드백을 함께 나눠요.", R.drawable.main_editing)
)

fun getMeetingsForTab(tab: String): List<String> = when (tab) {
    "독서" -> List(20) { "인생 독서 모임 #$it" }
    "학습" -> List(20) { "코테 대비반 #$it" }
    else -> List(20) { "자소서 첨삭반 #$it" }
}



