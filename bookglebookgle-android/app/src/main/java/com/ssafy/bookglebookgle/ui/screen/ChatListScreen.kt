package com.ssafy.bookglebookgle.ui.screen

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.entity.ChatListResponse
import com.ssafy.bookglebookgle.navigation.Screen
import com.ssafy.bookglebookgle.ui.component.CustomTopAppBar
import com.ssafy.bookglebookgle.ui.theme.MainColor
import com.ssafy.bookglebookgle.viewmodel.ChatListViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class ChatFilter(val displayName: String, val category: String?) {
    ALL("전체", null),
    READING("독서", "READING"),
    REVIEW("첨삭", "REVIEW"),
    STUDY("학습", "STUDY")
}

@Composable
fun ChatListScreen(
    navController: NavHostController,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCategoryFilter by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    // 카테고리 필터 적용된 채팅 리스트
    val filteredChatList = remember(uiState.chatList, selectedCategory) {
        if (selectedCategory == null) {
            uiState.chatList
        } else {
            uiState.chatList.filter { it.category == selectedCategory }
        }
    }

    // 현재 필터 상태
    val currentFilter = remember(selectedCategory) {
        ChatFilter.values().find { it.category == selectedCategory } ?: ChatFilter.ALL
    }

    LaunchedEffect(Unit) {
        viewModel.loadChatList()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            CustomTopAppBar(
                title = "chat",
                navController = navController,
                onChatSettingsClick = { showCategoryFilter = !showCategoryFilter }
            )

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFF4E5CE)
                        )
                    }
                }

                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "오류가 발생했습니다",
                                fontSize = 16.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.error ?: "알 수 없는 오류",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.refreshChatList() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF4E5CE),
                                    contentColor = Color.Black
                                )
                            ) {
                                Text("다시 시도", color = Color.White)
                            }
                        }
                    }
                }

                filteredChatList.isEmpty() && !uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (selectedCategory != null) {
                                    "해당 카테고리의 채팅방이 없습니다."
                                } else {
                                    "참여 중인 채팅방이 없습니다."
                                },
                                fontSize = 16.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                else -> {
                    // 현재 필터 표시 (전체가 아닌 경우)
                    if (selectedCategory != null) {
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
                                    text = "${currentFilter.displayName} (${filteredChatList.size})",
                                    color = Color.DarkGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // 채팅방 목록
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 16.dp, end = 16.dp)
                    ) {
                        itemsIndexed(filteredChatList) { index, chatList ->
                            ChatCard(
                                chat = chatList,
                                onClick = {
                                    navController.currentBackStackEntry?.savedStateHandle?.set(
                                        "groupId",
                                        chatList.groupId
                                    )
                                    // 채팅방 화면으로 이동
                                     navController.navigate(Screen.ChatRoomScreen.route)
                                }
                            )

                            if (index < filteredChatList.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                                    thickness = 1.dp,
                                    color = Color(0xFFE0E0E0)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 필터 드롭다운 (MyGroupScreen 스타일 적용)
        if (showCategoryFilter) {
            FilterDropdown(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 52.dp, end = 16.dp)
                    .zIndex(10f),
                currentFilter = currentFilter,
                onFilterSelected = { filter ->
                    selectedCategory = filter.category
                    showCategoryFilter = false
                },
                onDismiss = {
                    showCategoryFilter = false
                }
            )
        }

        // 드롭다운이 열려있을 때 배경 클릭으로 닫기
        if (showCategoryFilter) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.1f))
                    .clickable {
                        showCategoryFilter = false
                    }
                    .zIndex(5f)
            )
        }
    }
}

@Composable
fun FilterDropdown(
    modifier: Modifier = Modifier,
    currentFilter: ChatFilter,
    onFilterSelected: (ChatFilter) -> Unit,
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
            ChatFilter.values().forEach { filter ->
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
    filter: ChatFilter,
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
fun ChatCard(
    chat: ChatListResponse,
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
                .padding(top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // 이미지
            if (chat.imageUrl != null) {
                AsyncImage(
                    model = chat.imageUrl,
                    contentDescription = "모임 이미지",
                    modifier = Modifier
                        .size(50.dp)
                        .background(
                            color = Color(0xFFF0F0F0),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(
                        id = when (chat.category) {
                            "READING" -> R.drawable.book_group
                            "REVIEW" -> R.drawable.editing_group
                            else -> R.drawable.study_group
                        }
                    ),
                    contentDescription = "모임 이미지",
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 텍스트 영역
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 첫 번째 행: 제목 + 멤버 수 + 시간
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = chat.groupTitle,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = "${chat.memberCount}",
                            fontSize = 14.sp,
                            color = Color(0xFF888888)
                        )
                    }

                    // 시간
                    Text(
                        text = formatTime(chat.lastMessageTime),
                        fontSize = 11.sp,
                        color = Color(0xFF999999)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 두 번째 행: 마지막 메시지 + 안읽은 메시지 뱃지
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chat.lastMessage ?: "메시지가 없습니다.",
                        fontSize = 13.sp,
                        color = Color(0xFF666666),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (chat.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color.Red,
                                    shape = CircleShape
                                )
                                .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                lineHeight = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getCategoryDisplayName(category: String): String {
    return when (category) {
        "READING" -> "독서"
        "REVIEW" -> "첨삭"
        "STUDY" -> "학습"
        else -> category
    }
}

// 시간 포맷팅 함수
@SuppressLint("NewApi")
private fun formatTime(timeString: String?): String {
    if (timeString == null) return ""

    return try {
        // timeString이 "2024-01-15 14:30:00" 형태라고 가정
        val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val messageTime = LocalDateTime.parse(timeString, inputFormatter)
        val currentTime = LocalDateTime.now()

        val diffInMinutes = ChronoUnit.MINUTES.between(messageTime, currentTime)
        val diffInHours = ChronoUnit.HOURS.between(messageTime, currentTime)
        val diffInDays = ChronoUnit.DAYS.between(messageTime, currentTime)

        when {
            // 1분 미만
            diffInMinutes < 1 -> "방금"

            // 1시간 미만
            diffInMinutes < 60 -> "${diffInMinutes}분 전"

            // 오늘 (24시간 미만이면서 같은 날)
            diffInHours < 24 && messageTime.dayOfYear == currentTime.dayOfYear -> {
                val hour = messageTime.hour
                val minute = messageTime.minute

                if (hour < 12) {
                    "오전 ${hour}:${String.format("%02d", minute)}"
                } else {
                    val displayHour = if (hour == 12) 12 else hour - 12
                    "오후 ${displayHour}:${String.format("%02d", minute)}"
                }
            }

            // 어제
            diffInDays == 1L -> "어제"

            // 이번 주 (7일 미만)
            diffInDays < 7 -> {
                val dayOfWeek = when (messageTime.dayOfWeek.value) {
                    1 -> "월요일"
                    2 -> "화요일"
                    3 -> "수요일"
                    4 -> "목요일"
                    5 -> "금요일"
                    6 -> "토요일"
                    7 -> "일요일"
                    else -> "지난주"
                }
                dayOfWeek
            }

            // 이번 년도
            messageTime.year == currentTime.year -> {
                "${messageTime.monthValue}월 ${messageTime.dayOfMonth}일"
            }

            // 작년 이전
            else -> {
                "${messageTime.year}.${messageTime.monthValue}.${messageTime.dayOfMonth}"
            }
        }

    } catch (e: Exception) {
        // 파싱 실패시 원본 문자열에서 시간만 추출 (기존 로직)
        try {
            val parts = timeString.split(" ")
            if (parts.size >= 2) {
                val timePart = parts[1] // "14:30"
                val hour = timePart.split(":")[0].toInt()
                val minute = timePart.split(":")[1]

                if (hour < 12) {
                    "오전 ${hour}:${minute}"
                } else {
                    val displayHour = if (hour == 12) 12 else hour - 12
                    "오후 ${displayHour}:${minute}"
                }
            } else {
                timeString
            }
        } catch (innerE: Exception) {
            timeString
        }
    }
}