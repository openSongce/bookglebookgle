package com.ssafy.bookglebookgle.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.entity.MyGroupResponse
import com.ssafy.bookglebookgle.navigation.Screen
import com.ssafy.bookglebookgle.ui.component.CustomTopAppBar
import com.ssafy.bookglebookgle.ui.theme.MainColor
import com.ssafy.bookglebookgle.viewmodel.GroupFilter
import com.ssafy.bookglebookgle.viewmodel.MyGroupViewModel

// GroupCategory enum을 String으로 변환하는 확장 함수
fun String.toGroupCategory(): GroupCategory {
    return when (this.uppercase()) {
        "READING", "독서" -> GroupCategory.READING
        "STUDY", "학습" -> GroupCategory.STUDY
        "REVIEW", "첨삭" -> GroupCategory.REVIEW
        else -> GroupCategory.READING
    }
}

enum class GroupCategory(val displayName: String, val backgroundColor: Color) :
    java.io.Serializable {
    READING("독서", Color(0xFFB8C5B8)),
    STUDY("학습", Color(0xFFB8C5B8)),
    REVIEW("첨삭", Color(0xFFE8D5C4))
}

@Composable
fun MyGroupScreen(
    navController: NavHostController,
    viewModel: MyGroupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.resetFilter()  // 필터를 전체로 리셋
        viewModel.loadMyGroups()
    }

    // 화면이 다시 보일 때 (뒤로가기 등)
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.route == "main_mygroup") {
                viewModel.resetFilter()
            }
        }
        navController.addOnDestinationChangedListener(listener)

        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            CustomTopAppBar(
                title = "my_group",
                navController = navController,
                onMyGroupFilterClick = {
                    viewModel.toggleFilterDropdown()
                }
            )

            when {
                uiState.isLoading -> {
                    // 로딩 상태
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFF4E5CE)
                        )
                    }
                }

                uiState.filteredGroups.isEmpty() && !uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "참여중인 모임이 없습니다",
                                fontSize = 16.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "새로운 모임에 참여해보세요!",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                else -> {
                    // 현재 필터 표시
                    if (uiState.currentFilter != GroupFilter.ALL) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFEFE5D8),
                                modifier = Modifier.wrapContentSize()
                            ) {
                                Text(
                                    text = "${uiState.currentFilter.displayName} (${uiState.filteredGroups.size})",
                                    color = Color.DarkGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // 모임 목록 표시
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 16.dp, end = 16.dp)
                    ) {
                        itemsIndexed(uiState.filteredGroups) { index, group ->
                            MyGroupCard(
                                group = group,
                                onClick = {
                                    navController.currentBackStackEntry?.savedStateHandle?.set(
                                        "groupId",
                                        group.groupId
                                    )
                                    navController.currentBackStackEntry?.savedStateHandle?.set(
                                        "isMyGroup",
                                        true
                                    )
                                    navController.navigate(Screen.GroupDetailScreen.route)
                                }
                            )

                            // 마지막 아이템이 아닌 경우 구분선 추가
                            if (index < uiState.filteredGroups.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    thickness = 1.dp,
                                    color = Color(0xFFE0E0E0)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 필터 드롭다운
        if (uiState.isFilterDropdownVisible) {
            FilterDropdown(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 52.dp, end = 16.dp)
                    .zIndex(10f),
                currentFilter = uiState.currentFilter,
                onFilterSelected = { filter ->
                    viewModel.setFilter(filter)
                },
                onDismiss = {
                    viewModel.hideFilterDropdown()
                }
            )
        }

        // 드롭다운이 열려있을 때 배경 클릭으로 닫기
        if (uiState.isFilterDropdownVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.1f))
                    .clickable {
                        viewModel.hideFilterDropdown()
                    }
                    .zIndex(5f)
            )
        }
    }
}

@Composable
fun FilterDropdown(
    modifier: Modifier = Modifier,
    currentFilter: GroupFilter,
    onFilterSelected: (GroupFilter) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .width(160.dp)
                .padding(vertical = 8.dp)
        ) {
            GroupFilter.values().forEach { filter ->
                FilterDropdownItem(
                    filter = filter,
                    isSelected = filter == currentFilter,
                    onClick = {
                        onFilterSelected(filter)
                    }
                )
            }
        }
    }
}

@Composable
fun FilterDropdownItem(
    filter: GroupFilter,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(
                if (isSelected) Color(0xFFEFE5D8) else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = filter.displayName,
            fontSize = 14.sp,
            color = if (isSelected) Color(0xFF8B4513) else Color.Black,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
fun MyGroupCard(
    group: MyGroupResponse,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // 오른쪽 콘텐츠
            Column(
                modifier = Modifier.weight(1f)
            ) {
                CategoryTag(category = group.category.toGroupCategory())
                Spacer(modifier = Modifier.height(4.dp))

                // 제목
                Text(
                    text = group.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 설명
                Text(
                    text = group.description,
                    fontSize = 13.sp,
                    color = Color(0xFF666666),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 하단에 참여 인원 표시 - 별도 행으로 분리
                Text(
                    text = "${group.currentMembers}/${group.maxMembers}명",
                    fontSize = 12.sp,
                    color = Color(0xFF888888)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 이미지
            if (group.imageUrl != null) {
                AsyncImage(
                    model = group.imageUrl,
                    contentDescription = "모임 이미지",
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            color = Color(0xFFF0F0F0),
                            shape = RoundedCornerShape(12.dp)
                        )
                )
            } else {
                Image(
                    painter = painterResource(
                        id = if (group.category == GroupCategory.READING.toString())
                            R.drawable.book_group
                        else if (group.category == GroupCategory.REVIEW.toString())
                            R.drawable.editing_group
                        else
                            R.drawable.study_group
                    ),
                    contentDescription = "모임 이미지",
                    modifier = Modifier
                        .size(70.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
fun CategoryTag(category: GroupCategory) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFEFE5D8),
        modifier = Modifier.wrapContentSize()
    ) {
        Text(
            text = category.displayName,
            color = Color.DarkGray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}