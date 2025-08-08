package com.ssafy.bookglebookgle.ui.screen

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.entity.ChatMessage
import com.ssafy.bookglebookgle.entity.MessageType
import com.ssafy.bookglebookgle.ui.component.CustomTopAppBar
import com.ssafy.bookglebookgle.ui.theme.BaseColor
import com.ssafy.bookglebookgle.ui.theme.DeepMainColor
import com.ssafy.bookglebookgle.ui.theme.MainColor
import com.ssafy.bookglebookgle.viewmodel.ChatRoomViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "싸피_ChatRoomScreen"

@SuppressLint("NewApi")
@Composable
fun ChatRoomScreen(
    navController: NavHostController,
    groupId: Long,
    userId: Long,
    viewModel: ChatRoomViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 스크롤 제어를 위한 상태들
    var previousMessageCount by remember { mutableStateOf(0) }
    var scrollPositionBeforeLoad by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // 중복 호출 방지를 위한 강화된 상태 관리
    var lastLoadRequestTime by remember { mutableStateOf(0L) }
    var isScrollingUp by remember { mutableStateOf(false) }
    var lastFirstVisibleIndex by remember { mutableStateOf(-1) }

    // 새 메시지 알림 버튼 상태 추가
    var showNewMessageButton by remember { mutableStateOf(false) }
    var newMessageCount by remember { mutableStateOf(0) }

    // 키보드 상태 감지
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val imeVisible by remember {
        derivedStateOf {
            imeInsets.getBottom(density) > 0
        }
    }

    // 화면 나갈 때 채팅방 나가기
    DisposableEffect(Unit) {
        onDispose {
            viewModel.leaveChatRoom()
        }
    }

    // 채팅방 입장
    LaunchedEffect(groupId) {
        viewModel.enterChatRoom(groupId, userId)
        viewModel.markChatAsRead()
    }

    // 초기 로드 완료 시 맨 아래로 스크롤
    LaunchedEffect(uiState.shouldScrollToBottom) {
        if (uiState.shouldScrollToBottom && uiState.chatMessages.isNotEmpty()) {
            scope.launch {
                listState.scrollToItem(uiState.chatMessages.size - 1)
                previousMessageCount = uiState.chatMessages.size
                kotlinx.coroutines.delay(500)
                viewModel.resetScrollFlag()
                viewModel.markChatAsRead()
                // 초기 로드 시 버튼 숨기기
                showNewMessageButton = false
                newMessageCount = 0
            }
        }
    }

    // 개선된 스크롤 감지 로직 - 중복 호출 방지 강화
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val firstVisibleIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: -1
            val totalItemsCount = layoutInfo.totalItemsCount

            // 첫 번째 아이템이 완전히 보이는지 확인
            val firstVisibleItem = layoutInfo.visibleItemsInfo.firstOrNull()
            val isFirstItemFullyVisible = firstVisibleItem?.let { item ->
                item.index == 0 && item.offset >= -10 // 약간의 여유를 둠

            } ?: false

            // 마지막 아이템이 보이는지 확인 (읽음처리용)
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            val isLastItemVisible = lastVisibleItem?.let { item ->
                item.index == totalItemsCount - 1
            } ?: false

            // 맨 아래 근처에 있는지 확인 (새 메시지 버튼 표시용)
            val isNearBottom = lastVisibleItem?.let { item ->
                (totalItemsCount - item.index) <= 5
            } ?: false

            Triple(firstVisibleIndex, totalItemsCount, isFirstItemFullyVisible) to Pair(
                isLastItemVisible,
                isNearBottom
            )
        }
            .distinctUntilChanged()
            .collect { (triple, bottomInfo) ->
                val (firstVisibleIndex, totalItemsCount, isFirstItemFullyVisible) = triple
                val (isLastItemVisible, isNearBottom) = bottomInfo

                // 스크롤 방향 감지
                if (firstVisibleIndex != lastFirstVisibleIndex) {
                    isScrollingUp = firstVisibleIndex < lastFirstVisibleIndex
                    lastFirstVisibleIndex = firstVisibleIndex
                }

                val currentTime = System.currentTimeMillis()

                // 이전 메시지 로드 조건을 더 엄격하게 체크
                val shouldLoadMore = isFirstItemFullyVisible &&
                        firstVisibleIndex == 0 &&
                        totalItemsCount > 0 &&
                        !uiState.isLoadingMore &&
                        !uiState.isLoading &&
                        !uiState.shouldScrollToBottom &&
                        uiState.hasMoreData &&
                        isScrollingUp && // 위로 스크롤할 때만
                        (currentTime - lastLoadRequestTime) > 1500L // 최소 1.5초 간격으로 증가

                if (shouldLoadMore) {
                    lastLoadRequestTime = currentTime
                    viewModel.loadMoreMessages()
                }

                // 맨 아래 스크롤 시 읽음 처리 및 새 메시지 버튼 숨기기
                if (isLastItemVisible && !uiState.isLoadingMore && !uiState.isLoading) {
                    viewModel.markChatAsRead()
                    // 맨 아래에 도달하면 새 메시지 버튼 숨기기
                    if (showNewMessageButton) {
                        showNewMessageButton = false
                        newMessageCount = 0
                    }
                }

                // 맨 아래 근처에 있으면 새 메시지 버튼 숨기기
                if (isNearBottom && showNewMessageButton) {
                    showNewMessageButton = false
                    newMessageCount = 0
                }
            }
    }

    // 이전 메시지 로드 완료 시 스크롤 위치 조정
    LaunchedEffect(uiState.isLoadingMore) {
        if (uiState.isLoadingMore && scrollPositionBeforeLoad == null) {
//            previousMessageCount = uiState.chatMessages.size
            scrollPositionBeforeLoad = Pair(0, 0)
        }

        if (!uiState.isLoadingMore && scrollPositionBeforeLoad != null) {
            val currentMessageCount = uiState.chatMessages.size
            val addedMessageCount = currentMessageCount - previousMessageCount

            if (addedMessageCount > 0) {
                scope.launch {
                    try {
                        // 더 긴 지연시간으로 안정성 확보
                        kotlinx.coroutines.delay(100)

                        // 새로 불러온 메시지 수만큼 스크롤 위치 조정
                        val targetIndex = (addedMessageCount - 1).coerceAtLeast(0)
                        listState.scrollToItem(targetIndex, scrollOffset = 0)

                        // 스크롤 완료 후 추가 안정화 시간
                        kotlinx.coroutines.delay(200)

                    } catch (e: Exception) {
                        listState.scrollToItem(0)
                    }
                }

                // 이전 메시지 로드 완료 후에만 메시지 카운트 업데이트
                previousMessageCount = currentMessageCount
            }

            scrollPositionBeforeLoad = null
            // 로드 완료 후 잠시 대기하여 연속 호출 방지
            kotlinx.coroutines.delay(500)
        }
    }

    // 새 메시지 도착 시 스크롤 처리
    LaunchedEffect(uiState.chatMessages.size) {
        if (uiState.chatMessages.isNotEmpty() && !uiState.shouldScrollToBottom && !uiState.isLoadingMore && !uiState.isLoading) {
            val currentMessageCount = uiState.chatMessages.size

            if (currentMessageCount > previousMessageCount) {
                val newMessagesAdded = currentMessageCount - previousMessageCount

                // 새로 추가된 메시지 중 본인이 보낸 메시지가 있는지 확인
                val newMessages = uiState.chatMessages.takeLast(newMessagesAdded)
                val hasMyMessage = newMessages.any { viewModel.isMyMessage(it, userId) }

                // 새 메시지 도착 시 맨 아래 근처에 있으면 스크롤
                scope.launch {
                    val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                    val totalItems = listState.layoutInfo.totalItemsCount
                    val isNearBottom = lastVisibleItem?.index != null &&
                            (totalItems - lastVisibleItem.index) <= 3

                    if (isNearBottom || hasMyMessage) {
                        // 맨 아래 근처에 있으면 자동 스크롤
                        listState.animateScrollToItem(currentMessageCount - 1)
                        viewModel.markChatAsRead()
                        // 새 메시지 버튼 숨기기
                        showNewMessageButton = false
                        newMessageCount = 0
                    } else {
                        // 위에 있으면 새 메시지 버튼 표시
                        newMessageCount += newMessagesAdded
                        showNewMessageButton = true
                    }
                }
                previousMessageCount = currentMessageCount
            }
        }
    }

    // 키보드가 나타날 때 스크롤
    LaunchedEffect(imeVisible) {
        if (imeVisible && uiState.chatMessages.isNotEmpty()) {
            scope.launch {
                val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                val totalItems = listState.layoutInfo.totalItemsCount
                val isNearBottom = lastVisibleItem?.index != null &&
                        (totalItems - lastVisibleItem.index) <= 3

                if (isNearBottom) {
                    kotlinx.coroutines.delay(100)
                    listState.animateScrollToItem(uiState.chatMessages.size - 1)
                    viewModel.markChatAsRead()
                    // 키보드로 인한 스크롤 시에만 새 메시지 버튼 숨기기 (새 메시지가 있을 때만)
                    if (showNewMessageButton) {
                        showNewMessageButton = false
                        newMessageCount = 0
                    }
                }
            }
        }
    }

    // AI 타이핑 인디케이터가 표시될 때 자동 스크롤
    LaunchedEffect(uiState.isAiTyping) {
        if (uiState.isAiTyping && uiState.chatMessages.isNotEmpty()) {
            scope.launch {
                val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                val totalItems = listState.layoutInfo.totalItemsCount
                val isNearBottom = lastVisibleItem?.index != null &&
                        (totalItems - lastVisibleItem.index) <= 3

                if (isNearBottom) {
                    kotlinx.coroutines.delay(100)
                    // AI 타이핑 인디케이터를 포함한 총 아이템 수로 스크롤
                    listState.animateScrollToItem(totalItems)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize())
    {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .imePadding()
        ) {
            CustomTopAppBar(
                title = uiState.groupTitle,
                isChatScreen = true,
                navController = navController,
            )

            // 토론 컨트롤 패널 (READING 카테고리일 때만 표시)
            if (uiState.isReadingCategory) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 2.dp,
                    color = Color.White
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 토론 상태 표시 (모든 사용자가 볼 수 있음)
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (uiState.isDiscussionActive) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(MainColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AI 토론 진행 중",
                                    fontSize = 12.sp,
                                    color = DeepMainColor,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color.Gray, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "일반 채팅",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // 토론 시작/종료 버튼 (모임장만 볼 수 있음)
                        if (uiState.isHost) {
                            Button(
                                onClick = {
                                    Log.d(TAG, "토론 버튼 클릭됨! 현재 상태: ${uiState.isDiscussionActive}")
                                    if (uiState.isDiscussionActive) {
                                        Log.d(TAG, "토론 종료 호출")
                                        viewModel.endDiscussion()
                                    } else {
                                        Log.d(TAG, "토론 시작 호출")
                                        viewModel.startDiscussion()
                                    }
                                },
                                modifier = Modifier.height(32.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (uiState.isDiscussionActive) Color(0xFFE74C3C) else Color(0xFF2ECC71)
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                enabled = !uiState.isDiscussionConnecting
                            ) {
                                Icon(
                                    imageVector = if (uiState.isDiscussionActive) Icons.Default.Close else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (uiState.isDiscussionActive) "토론 종료" else "토론 시작",
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

//            // 토론 컨트롤 패널 (READING 카테고리일 때만 표시)
//            if (uiState.isReadingCategory) {
//                Surface(
//                    modifier = Modifier.fillMaxWidth(),
//                    shadowElevation = 2.dp,
//                    color = Color.White
//                ) {
//                    Row(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(horizontal = 16.dp, vertical = 8.dp),
//                        horizontalArrangement = Arrangement.SpaceBetween,
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        // 왼쪽: 토론 상태 표시
//                        Row(
//                            verticalAlignment = Alignment.CenterVertically
//                        ) {
//                            if (uiState.isDiscussionActive) {
//                                Box(
//                                    modifier = Modifier
//                                        .size(8.dp)
//                                        .background(MainColor, CircleShape)
//                                )
//                                Spacer(modifier = Modifier.width(6.dp))
//                                Text(
//                                    text = "AI 토론 진행 중",
//                                    fontSize = 12.sp,
//                                    color = DeepMainColor,
//                                    fontWeight = FontWeight.Medium
//                                )
//                            } else {
//                                Box(
//                                    modifier = Modifier
//                                        .size(8.dp)
//                                        .background(Color.Gray, CircleShape)
//                                )
//                                Spacer(modifier = Modifier.width(6.dp))
//                                Text(
//                                    text = "일반 채팅",
//                                    fontSize = 12.sp,
//                                    color = Color.Gray,
//                                    fontWeight = FontWeight.Medium
//                                )
//                            }
//                        }
//
//                        // 오른쪽: 토론 시작/종료 버튼
//                        Button(
//                            onClick = {
//                                Log.d(TAG, "토론 버튼 클릭됨! 현재 상태: ${uiState.isDiscussionActive}")
//                                if (uiState.isDiscussionActive) {
//                                    Log.d(TAG, "토론 종료 호출")
//                                    viewModel.endDiscussion()
//                                } else {
//                                    Log.d(TAG, "토론 시작 호출")
//                                    viewModel.startDiscussion()
//                                }
//                            },
//                            modifier = Modifier.height(32.dp),
//                            colors = ButtonDefaults.buttonColors(
//                                containerColor = if (uiState.isDiscussionActive) Color(0xFFE74C3C) else Color(
//                                    0xFF2ECC71
//                                )
//                            ),
//                            contentPadding = PaddingValues(horizontal = 12.dp),
//                            enabled = !uiState.isDiscussionConnecting // 연결 중일 때 버튼 비활성화
//                        ) {
//                            Icon(
//                                imageVector = if (uiState.isDiscussionActive) Icons.Default.Close else Icons.Default.PlayArrow,
//                                contentDescription = null,
//                                modifier = Modifier.size(16.dp),
//                                tint = Color.White
//                            )
//                            Spacer(modifier = Modifier.width(4.dp))
//                            Text(
//                                text = if (uiState.isDiscussionActive) "토론 종료" else "토론 시작",
//                                fontSize = 12.sp,
//                                color = Color.White
//                            )
//                        }
//                    }
//                }
//            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        top = 16.dp,
                        bottom = 26.dp
                    )
                ) {
                    // 위쪽에 더 불러오기 로딩 표시
                    if (uiState.isLoadingMore && uiState.hasMoreData) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = BaseColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "이전 메시지를 불러오는 중...",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }

                    // 더 이상 불러올 메시지가 없을 때 표시
                    if (!uiState.hasMoreData && uiState.chatMessages.isNotEmpty() && !uiState.isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "채팅의 시작입니다",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    // 초기 로딩
                    if (uiState.isLoading && uiState.chatMessages.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(600.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        color = BaseColor,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "채팅 불러오는 중...",
                                        fontSize = 14.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }

                    // 에러 메시지
                    if (uiState.error != null) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.Red.copy(alpha = 0.1f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = uiState.error!!,
                                        color = Color.Red,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(
                                        onClick = { viewModel.clearError() }
                                    ) {
                                        Text("확인", color = Color.Red)
                                    }
                                }
                            }
                        }
                    }

                    // 채팅 메시지들
                    items(
                        items = uiState.chatMessages,
                        key = { message -> message.messageId }
                    ) { message ->
                        ChatMessageItem(
                            message = message,
                            isMyMessage = viewModel.isMyMessage(message, userId)
                        )
                    }

                    // AI 타이핑 인디케이터
                    if (uiState.isAiTyping) {
                        item {
                            AiTypingIndicator()
                        }
                    }


                }
                // 새 메시지 알림 버튼 추가
                if (showNewMessageButton) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 30.dp), // 입력창 위로 여유 공간
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = Color(0xFF757575).copy(alpha = 0.85f),
                                    shape = CircleShape
                                )
                                .clickable(
                                    indication = null, // 눌림 효과 제거
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    scope.launch {
                                        // 맨 아래로 스크롤
                                        listState.animateScrollToItem(uiState.chatMessages.size - 1)
                                        // 버튼 숨기기
                                        showNewMessageButton = false
                                        newMessageCount = 0
                                        // 읽음 처리
                                        viewModel.markChatAsRead()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "새 메시지로 이동",
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                        }
                    }
                }

                // AI 추천 주제 오버레이
                if (uiState.isReadingCategory && uiState.showAiSuggestions && uiState.suggestedTopics.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                            .clickable {
                                Log.d(TAG, "AI 추천 주제 배경 클릭 - 닫기")
                                viewModel.dismissAiSuggestions()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .clickable { }, // 카드 클릭 시 배경 클릭 방지
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🤖 AI 추천 주제",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BaseColor
                                    )
                                    IconButton(
                                        onClick = {
                                            Log.d(TAG, "AI 추천 주제 X 버튼 클릭")
                                            viewModel.dismissAiSuggestions()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "닫기"
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                if (uiState.currentAiResponse != null) {
                                    Text(
                                        text = uiState.currentAiResponse!!,
                                        fontSize = 14.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                }

                                LazyColumn(
                                    modifier = Modifier.heightIn(max = 300.dp)
                                ) {
                                    items(uiState.suggestedTopics) { topic ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .clickable {
                                                    Log.d(TAG, "추천 주제 선택: $topic")
                                                    viewModel.selectSuggestedTopic(topic)
                                                },
                                            colors = CardDefaults.cardColors(
                                                containerColor = MainColor.copy(alpha = 0.1f)
                                            )
                                        ) {
                                            Text(
                                                text = topic,
                                                modifier = Modifier.padding(12.dp),
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 빈 상태 메시지 (토론 테스트 버튼 추가)
                if (!uiState.isLoading && uiState.chatMessages.isEmpty() && uiState.error == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "대화내역이 없습니다.",
                                fontSize = 16.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "채팅을 시작해보세요!",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // 메시지 입력 영역
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 4.dp
            ) {
                Column {
                    // 토론 중일 때 상태 표시바
                    if (uiState.isReadingCategory && uiState.isDiscussionActive && uiState.isDiscussionAutoDetected) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = BaseColor.copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painterResource(id = R.drawable.ic_ai),
                                    contentDescription = null,
                                    tint = DeepMainColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "AI 토론이 진행 중입니다. AI가 대화를 분석하고 피드백을 제공합니다.",
                                    fontSize = 12.sp,
                                    color = DeepMainColor
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        CompositionLocalProvider(
                            LocalTextSelectionColors provides TextSelectionColors(
                                handleColor = BaseColor,
                                backgroundColor = BaseColor.copy(alpha = 0.3f)
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 40.dp)
                                    .border(
                                        width = 1.dp,
                                        color = if (messageText.isNotEmpty()) BaseColor else Color.Gray.copy(
                                            alpha = 0.5f
                                        ),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicTextField(
                                    value = messageText,
                                    onValueChange = {
                                        messageText = it
                                        if (uiState.error != null) viewModel.clearError()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        color = Color.Black
                                    ),
                                    maxLines = 4,
                                    cursorBrush = SolidColor(BaseColor),
                                    decorationBox = { innerTextField ->
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (messageText.isEmpty()) {
                                                Text(
                                                    text = "메시지 입력",
                                                    fontSize = 14.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        FloatingActionButton(
                            onClick = {
                                if (messageText.isNotBlank() && uiState.grpcConnected) {
                                    Log.d(TAG, "메시지 전송: $messageText")
                                    viewModel.sendMessage(messageText.trim())
                                    messageText = ""
                                } else {
                                    Log.d(
                                        TAG,
                                        "메시지 전송 실패 - 텍스트: '$messageText', gRPC 연결: ${uiState.grpcConnected}"
                                    )
                                }
                            },
                            modifier = Modifier.size(40.dp),
                            containerColor = if (messageText.isNotBlank() && uiState.grpcConnected) MainColor else Color.Gray
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "메시지 전송",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // 토론 연결 중 로딩 오버레이
        if (uiState.isReadingCategory && uiState.isDiscussionConnecting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { }, // 클릭 차단
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .padding(32.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = BaseColor,
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "토론 준비 중입니다...",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "AI가 토론을 준비하고 있어요",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }

}

// AI 타이핑 인디케이터 컴포넌트
@Composable
fun AiTypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        // AI 프로필 이미지
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(BaseColor.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(id = R.drawable.ic_ai),
                contentDescription = "AI",
                tint = BaseColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = "AI",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Surface(
                shape = RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                color = Color.Gray.copy(alpha = 0.1f),
                modifier = Modifier.widthIn(min = 60.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 세 개의 점이 순차적으로 애니메이션
                    repeat(3) { index ->
                        val animatedAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(
                                    durationMillis = 600,
                                    delayMillis = index * 200
                                ),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dot_${index}_alpha"
                        )

                        val animatedScale by infiniteTransition.animateFloat(
                            initialValue = 0.8f,
                            targetValue = 1.2f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(
                                    durationMillis = 600,
                                    delayMillis = index * 200
                                ),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dot_${index}_scale"
                        )

                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = BaseColor.copy(alpha = animatedAlpha),
                                    shape = CircleShape
                                )
                                .graphicsLayer {
                                    scaleX = animatedScale
                                    scaleY = animatedScale
                                }
                        )
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    isMyMessage: Boolean
) {
    // 메시지 타입에 따른 렌더링 분기
    when (message.type) {
        MessageType.AI_RESPONSE -> {
            AiResponseMessageItem(message = message)
        }

        MessageType.DISCUSSION_START, MessageType.DISCUSSION_END -> {
            SystemMessageItem(message = message)
        }

        else -> {
            RegularMessageItem(message = message, isMyMessage = isMyMessage)
        }
    }
}

@Composable
fun AiResponseMessageItem(message: ChatMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = BaseColor.copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp)
            ) {
                Icon(
                    painterResource(id = R.drawable.ic_ai),
                    contentDescription = "AI",
                    tint = BaseColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "AI 분석 결과",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = BaseColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // AI 응답 내용
                    if (message.aiResponse != null) {
                        Text(
                            text = message.aiResponse!!,
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                    }

                    // 일반 메시지 내용도 표시 (필요한 경우)
                    if (message.message.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = message.message,
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message.timestamp,
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun SystemMessageItem(message: ChatMessage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = when (message.type) {
                    MessageType.DISCUSSION_START -> Color(0xFF2ECC71).copy(alpha = 0.06f)
                    MessageType.DISCUSSION_END -> Color(0xFFE74C3C).copy(alpha = 0.06f)
                    else -> Color.Gray.copy(alpha = 0.1f)
                }
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (message.type) {
                        MessageType.DISCUSSION_START -> Icons.Default.PlayArrow
                        MessageType.DISCUSSION_END -> Icons.Default.Close
                        else -> Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = when (message.type) {
                        MessageType.DISCUSSION_START -> Color(0xFF2ECC71)
                        MessageType.DISCUSSION_END -> Color.Red
                        else -> Color.Gray
                    },
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = message.message,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                    Text(
                        text = message.timestamp,
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun RegularMessageItem(
    message: ChatMessage,
    isMyMessage: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isMyMessage) Arrangement.End else Arrangement.Start
    ) {
        if (!isMyMessage) {
            if (message.profileImage != null) {
                AsyncImage(
                    model = message.profileImage,
                    contentDescription = "프로필 이미지",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Gray.copy(alpha = 0.3f)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MainColor.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = message.nickname.take(1),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = BaseColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isMyMessage) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            if (!isMyMessage) {
                Text(
                    text = message.nickname,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = if (isMyMessage) Arrangement.End else Arrangement.Start
            ) {
                if (isMyMessage) {
                    Text(
                        text = message.timestamp,
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(
                        topStart = if (isMyMessage) 16.dp else 4.dp,
                        topEnd = if (isMyMessage) 4.dp else 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    ),
                    color = if (isMyMessage) {
                        MainColor
                    } else {
                        Color.Gray.copy(alpha = 0.1f)
                    },
                    modifier = Modifier.widthIn(
                        max = if (isMyMessage) 220.dp else 200.dp
                    )
                ) {
                    Text(
                        text = message.message,
                        modifier = Modifier.padding(12.dp),
                        color = Color.Black,
                        fontSize = 14.sp
                    )
                }

                if (!isMyMessage) {
                    Text(
                        text = message.timestamp,
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}