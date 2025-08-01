package com.ssafy.bookglebookgle.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import com.ssafy.bookglebookgle.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ssafy.bookglebookgle.entity.GroupListResponse
import com.ssafy.bookglebookgle.navigation.Screen
import com.ssafy.bookglebookgle.ui.component.CustomTopAppBar
import com.ssafy.bookglebookgle.util.ScreenSize
import com.ssafy.bookglebookgle.viewmodel.MainViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(navController: NavHostController, viewModel: MainViewModel = hiltViewModel()) {
    var selectedTab by remember { mutableStateOf("독서") }

    val readingGroups = viewModel.readingGroups.value
    val studyGroups = viewModel.studyGroups.value
    val reviewGroups = viewModel.reviewGroups.value
    val groups = when (selectedTab) {
        "독서" -> readingGroups
        "학습" -> studyGroups
        else -> reviewGroups
    }

    val tabs = listOf("독서", "학습", "첨삭")
    val horizontalPadding = ScreenSize.width * 0.04f
    val verticalPadding = ScreenSize.height * 0.01f

    LaunchedEffect(Unit) {
        viewModel.getchAllGroups()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        CustomTopAppBar(title = "main_home", navController = navController)

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // 카테고리 추천 섹션
            item {
                Text(
                    text = "추천 모임",
                    fontSize = ScreenSize.width.value.times(0.06f).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = horizontalPadding, top = verticalPadding)
                )
            }

            item {
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
                            width = ScreenSize.width * 0.8f,
                            height = ScreenSize.height * 0.2f,
                            rightMargin = horizontalPadding
                        )
                    }
                }
            }

            // 카테고리별 모임 헤더 - 스크롤 시 상단에 고정
            stickyHeader {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White) // 배경색 설정
                            .padding(vertical = verticalPadding)

                    ) {
                        Text(
                            text = "카테고리별 모임",
                            fontSize = ScreenSize.width.value.times(0.06f).sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = horizontalPadding)
                        )

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
                                        modifier = Modifier.clickable {
                                            selectedTab = tab
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .height(2.dp)
                                            .width(20.dp)
                                            .background(if (selectedTab == tab) Color(0xFFD2B48C) else Color.Transparent)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            color = Color.LightGray,
                            thickness = 1.dp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // 모임 리스트
                if (groups.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ScreenSize.height * 0.3f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "생성된 모임이 없습니다\n" +
                                        "모임을 생성해보세요!",
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                fontSize = ScreenSize.width.value.times(0.04f).sp
                            )
                        }
                    }
                } else {
                    itemsIndexed(groups) { index, group ->
                        MeetingCard(
                            group = group,
                        ) {
                            navController.currentBackStackEntry?.savedStateHandle?.set("groupId", group.groupId)
                            navController.currentBackStackEntry?.savedStateHandle?.set("isMyGroup", false)
                            navController.navigate(Screen.GroupDetailScreen.route)
                        }

                        // 마지막 아이템이 아닐 때만 구분선 추가
                        if (index < groups.size - 1) {
                            HorizontalDivider(
                                color = Color(0xFFE0E0E0),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(horizontal = horizontalPadding)
                            )
                        }
                    }
                }

            // 하단 여백
            item {
                Spacer(modifier = Modifier.height(verticalPadding))
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
                    .padding(horizontal = width * 0.04f, vertical = height * 0.02f)
            ) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(description, fontSize = width.value.times(0.03f).sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun MeetingCard(group: GroupListResponse, onClick: () -> Unit) {
    val config = LocalConfiguration.current
    val screenWidth = config.screenWidthDp.dp
    val screenHeight = config.screenHeightDp.dp

    val imageSize = screenHeight * 0.08f
    val innerPadding = screenWidth * 0.03f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(start = ScreenSize.width * 0.04f, end = ScreenSize.width * 0.04f)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = innerPadding, vertical = screenHeight * 0.01f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = when (group.category) {
                    "STUDY" -> "스터디"
                    "READING" -> "독서"
                    "REVIEW" -> "첨삭"
                    else -> group.category // 예외 처리: 혹시 다른 값이 있을 경우 그대로 출력
                }, fontSize = screenWidth.value.times(0.03f).sp, color = Color(0xFFD2B48C))
                Text(group.roomTitle, fontWeight = FontWeight.Bold)
                Text(
                    group.description,
                    fontSize = screenWidth.value.times(0.032f).sp,
                    color = Color.DarkGray
                )
                Spacer(modifier = Modifier.height(screenHeight * 0.005f))
                Surface(
                    shape = RoundedCornerShape(20),
                    color = Color(0xFFF1F1F1)
                ) {
                    Text(
                        "${group.currentNum}/${group.groupMaxNum}명",
                        modifier = Modifier.padding(
                            horizontal = screenWidth * 0.02f,
                            vertical = screenHeight * 0.003f
                        ),
                        fontSize = screenWidth.value.times(0.03f).sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(screenWidth * 0.03f))
            Image(
                painter = painterResource(id = when (group.category) {
                    "READING" -> R.drawable.book_group
                    "STUDY" -> R.drawable.study_group
                    "REVIEW" -> R.drawable.editing_group
                    else -> { R.drawable.profile_example}
                }),
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