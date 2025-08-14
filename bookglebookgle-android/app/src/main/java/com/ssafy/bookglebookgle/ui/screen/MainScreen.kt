package com.ssafy.bookglebookgle.ui.screen

import android.annotation.SuppressLint
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.ssafy.bookglebookgle.ui.theme.*
import com.ssafy.bookglebookgle.viewmodel.MainViewModel

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(navController: NavHostController, viewModel: MainViewModel = hiltViewModel()) {
    var selectedTab by remember { mutableStateOf("독서") }

    // 반응형 디멘션 사용
    val dimensions = rememberResponsiveDimensions()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    val readingGroups = viewModel.readingGroups.value
    val studyGroups = viewModel.studyGroups.value
    val reviewGroups = viewModel.reviewGroups.value
    val allGroups = viewModel.allGroups.value
    val searchResults = viewModel.searchResults.value
    val isSearching = viewModel.isSearching.value
    val isInSearchMode = viewModel.isInSearchMode()

    // 추천 모임용 랜덤 그룹 3개 선택
    val recommendedGroups = remember(allGroups) {
        if (allGroups.isNotEmpty()) {
            allGroups.shuffled().take(3)
        } else {
            emptyList()
        }
    }

    val groups = if (isInSearchMode) {
        when (selectedTab) {
            "독서" -> viewModel.getSearchResultsByCategory("READING")
            "학습" -> viewModel.getSearchResultsByCategory("STUDY")
            else -> viewModel.getSearchResultsByCategory("REVIEW")
        }
    } else {
        when (selectedTab) {
            "독서" -> readingGroups
            "학습" -> studyGroups
            else -> reviewGroups
        }
    }

    val tabs = listOf("독서", "학습", "첨삭")

    LaunchedEffect(Unit) {
        viewModel.getchAllGroups()
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = if (dimensions.isTablet) Alignment.TopCenter else Alignment.TopStart
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (dimensions.isTablet) dimensions.contentMaxWidth * 1.5f else Dp.Infinity)
                .fillMaxSize()
        ) {
            CustomTopAppBar(
                title = "main_home",
                navController = navController,
                onSearchPerformed = { query ->
                    viewModel.searchGroups(query)
                },
                onSearchCancelled = {
                    viewModel.clearSearchResults()
                }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                // 검색 모드가 아닐 때만 추천 섹션 표시
                if (!isInSearchMode) {
                    // 카테고리 추천 섹션
                    item {
                        Text(
                            text = "추천 모임",
                            fontSize = dimensions.textSizeHeadline,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(
                                start = dimensions.defaultPadding,
                                top = dimensions.spacingSmall
                            )
                        )
                    }

                    item {
                        if (recommendedGroups.isNotEmpty()) {
                            if (recommendedGroups.size == 1) {
                                // 추천 모임이 1개일 때는 전체 너비로 표시
                                Column(
                                    modifier = Modifier.padding(
                                        horizontal = dimensions.defaultPadding,
                                        vertical = dimensions.spacingSmall
                                    )
                                ) {
                                    RecommendGroupCard(
                                        group = recommendedGroups[0],
                                        width = screenWidth - (dimensions.defaultPadding * 2),
                                        height = dimensions.recommendCardHeight, // 고정 높이 사용
                                        rightMargin = 0.dp,
                                        dimensions = dimensions,
                                        onClick = {
                                            navController.currentBackStackEntry?.savedStateHandle?.set(
                                                "groupId",
                                                recommendedGroups[0].groupId
                                            )
                                            navController.currentBackStackEntry?.savedStateHandle?.set(
                                                "isMyGroup",
                                                false
                                            )
                                            navController.navigate(Screen.GroupDetailScreen.route)
                                        }
                                    )
                                }
                            } else {
                                // 추천 모임이 2개 이상
                                LazyRow(
                                    contentPadding = PaddingValues(
                                        horizontal = dimensions.defaultPadding,
                                        vertical = dimensions.spacingSmall
                                    )
                                ) {
                                    items(recommendedGroups) { group ->
                                        RecommendGroupCard(
                                            group = group,
                                            width = screenWidth * dimensions.recommendCardWidth,
                                            height = dimensions.recommendCardHeight, // 고정 높이 사용
                                            rightMargin = dimensions.defaultPadding,
                                            dimensions = dimensions,
                                            onClick = {
                                                navController.currentBackStackEntry?.savedStateHandle?.set(
                                                    "groupId",
                                                    group.groupId
                                                )
                                                navController.currentBackStackEntry?.savedStateHandle?.set(
                                                    "isMyGroup",
                                                    false
                                                )
                                                navController.navigate(Screen.GroupDetailScreen.route)
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            // 추천할 모임이 없을 때
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(dimensions.recommendCardHeight), // 고정 높이 사용
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "추천 모임이 없습니다\n" +
                                            "새로운 모임을 생성해보세요!",
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center,
                                    fontSize = dimensions.textSizeBody
                                )
                            }
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
                                .background(Color.White)
                                .padding(vertical = dimensions.spacingSmall)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = dimensions.defaultPadding),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isInSearchMode) "검색 결과" else "카테고리별 모임",
                                    fontSize = dimensions.textSizeHeadline,
                                    fontWeight = FontWeight.Bold
                                )

                                if (!isInSearchMode) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_group_register),
                                        contentDescription = "모임생성",
                                        modifier = Modifier
                                            .size(dimensions.iconSizeLarge)
                                            .clickable {
                                                navController.navigate(Screen.GroupRegisterScreen.route)
                                            },
                                        tint = Color.Black
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = dimensions.spacingSmall),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                tabs.forEach { tab ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = tab,
                                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selectedTab == tab) Color.Black else Color.Gray,
                                            fontSize = dimensions.textSizeBody,
                                            modifier = Modifier.clickable {
                                                selectedTab = tab
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(dimensions.spacingTiny))
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
                                thickness = dimensions.dividerThickness,
                                modifier = Modifier.padding(top = dimensions.spacingSmall)
                            )
                        }
                    }
                }

                // 모임 리스트
                if (groups.isEmpty() && !isSearching) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(screenHeight * 0.3f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isInSearchMode) {
                                    "검색 결과가 없습니다\n" +
                                            "다른 검색어를 시도해보세요"
                                } else {
                                    "생성된 모임이 없습니다\n" +
                                            "모임을 생성해보세요!"
                                },
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                fontSize = dimensions.textSizeBody
                            )
                        }
                    }
                } else {
                    itemsIndexed(groups) { index, group ->
                        MeetingCard(
                            group = group,
                            dimensions = dimensions,
                            screenWidth = screenWidth,
                            screenHeight = screenHeight
                        ) {
                            navController.currentBackStackEntry?.savedStateHandle?.set(
                                "groupId",
                                group.groupId
                            )
                            navController.currentBackStackEntry?.savedStateHandle?.set(
                                "isMyGroup",
                                false
                            )
                            navController.navigate(Screen.GroupDetailScreen.route)
                        }

                        // 마지막 아이템이 아닐 때만 구분선 추가
                        if (index < groups.size - 1) {
                            HorizontalDivider(
                                color = Color(0xFFE0E0E0),
                                thickness = dimensions.dividerThickness,
                                modifier = Modifier.padding(horizontal = dimensions.defaultPadding)
                            )
                        }
                    }
                }

                // 검색 모드일 때 전체 검색 결과 수 표시
                if (isInSearchMode && searchResults.isNotEmpty()) {
                    item {
                        Text(
                            text = "총 ${searchResults.size}개의 모임이 검색되었습니다",
                            color = Color.Gray,
                            fontSize = dimensions.textSizeCaption,
                            modifier = Modifier.padding(
                                horizontal = dimensions.defaultPadding,
                                vertical = dimensions.spacingSmall
                            )
                        )
                    }
                }

                // 하단 여백
                item {
                    Spacer(modifier = Modifier.height(dimensions.spacingSmall))
                }
            }
        }
    }
}

@Composable
fun RecommendGroupCard(
    group: GroupListResponse,
    width: Dp,
    height: Dp,                 // ← 이미지만의 높이로 사용
    rightMargin: Dp,
    dimensions: ResponsiveDimensions,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(width)
            .padding(end = rightMargin)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(dimensions.defaultCornerRadius)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // 🔹 TOP: 이미지 (가로폭 꽉 채움 + 살짝 더 커 보이게 Crop)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxOf(height, dimensions.recommendCardImageHeight)) // 필요시 더 크게
                    .clip(
                        RoundedCornerShape(
                            topStart = dimensions.defaultCornerRadius,
                            topEnd = dimensions.defaultCornerRadius
                        )
                    )
            ) {
                Image(
                    painter = painterResource(
                        id = when (group.category) {
                            "READING" -> R.drawable.main_reading
                            "STUDY"   -> R.drawable.main_studying
                            "REVIEW"  -> R.drawable.main_editing
                            else      -> R.drawable.main_reading
                        }
                    ),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop // ← 너비 꽉, 더 크게 보이게
                )

                // 🔸 인원수 배지 (이미지 오른쪽 아래)
                Surface(
                    shape = RoundedCornerShape(dimensions.cornerRadiusSmall),
                    color = Color(0xFFf5ecdf),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        "${group.currentNum}/${group.groupMaxNum}명",
                        modifier = Modifier.padding(
                            horizontal = dimensions.spacingTiny,
                            vertical = dimensions.spacingTiny
                        ),
                        fontSize = dimensions.textSizeCaption,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 🔹 BOTTOM: 제목 + 설명 (이미지 아래)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensions.cardInnerPadding)
            ) {
                Text(
                    text = group.roomTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = dimensions.textSizeSubtitle,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(dimensions.spacingTiny))

                Text(
                    text = group.description,
                    fontSize = dimensions.textSizeCaption,
                    color = Color.Gray,
                    maxLines = 2
                )
            }
        }
    }
}



@Composable
fun MeetingCard(
    group: GroupListResponse,
    dimensions: ResponsiveDimensions,
    screenWidth: Dp,
    screenHeight: Dp,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(
                start = dimensions.defaultPadding,
                end = dimensions.defaultPadding
            )
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(dimensions.cornerRadiusSmall)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = dimensions.cardInnerPadding,
                    vertical = dimensions.spacingSmall
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (group.category) {
                        "STUDY" -> "스터디"
                        "READING" -> "독서"
                        "REVIEW" -> "첨삭"
                        else -> group.category
                    },
                    fontSize = dimensions.textSizeCaption,
                    color = Color(0xFFD2B48C)
                )
                Text(
                    group.roomTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = dimensions.textSizeBody
                )
                Text(
                    group.description,
                    fontSize = dimensions.textSizeCaption,
                    color = Color.DarkGray
                )
                Spacer(modifier = Modifier.height(dimensions.spacingTiny))
                Surface(
                    shape = RoundedCornerShape(20),
                    color = Color(0xFFF1F1F1)
                ) {
                    Text(
                        "${group.currentNum}/${group.groupMaxNum}명",
                        modifier = Modifier.padding(
                            horizontal = dimensions.spacingSmall,
                            vertical = dimensions.spacingTiny
                        ),
                        fontSize = dimensions.textSizeCaption
                    )
                }
            }
            Spacer(modifier = Modifier.width(dimensions.spacingMedium))
            Image(
                painter = painterResource(
                    id = when (group.category) {
                        "READING" -> R.drawable.book_group
                        "STUDY" -> R.drawable.study_group
                        "REVIEW" -> R.drawable.editing_group
                        else -> R.drawable.profile_example
                    }
                ),
                contentDescription = null,
                modifier = Modifier
                    .size(dimensions.itemImageSize)
                    .clip(RoundedCornerShape(dimensions.cornerRadiusSmall)),
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