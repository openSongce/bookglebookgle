package com.ssafy.bookglebookgle.ui.screen

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ssafy.bookglebookgle.entity.ChatMessage
import com.ssafy.bookglebookgle.ui.component.CustomTopAppBar
import com.ssafy.bookglebookgle.ui.theme.BaseColor
import com.ssafy.bookglebookgle.ui.theme.MainColor
import com.ssafy.bookglebookgle.viewmodel.ChatRoomViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

// ChatRoomScreen.kt의 핵심 수정 부분

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
                kotlinx.coroutines.delay(500) // 스크롤 감지 로직 안정화 시간 확보
                viewModel.resetScrollFlag()
                viewModel.markChatAsRead()
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

            Triple(firstVisibleIndex, totalItemsCount, isFirstItemFullyVisible) to isLastItemVisible
        }
            .distinctUntilChanged()
            .collect {  (triple, isLastItemVisible) ->

                val (firstVisibleIndex, totalItemsCount, isFirstItemFullyVisible) = triple

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

                // 맨 아래 스크롤 시 읽음 처리
                if (isLastItemVisible && !uiState.isLoadingMore && !uiState.isLoading) {
                    viewModel.markChatAsRead()
                }
            }
    }

    // 이전 메시지 로드 완료 시 스크롤 위치 조정 - 더 안정적으로 개선
    LaunchedEffect(uiState.isLoadingMore) {
        if (uiState.isLoadingMore && scrollPositionBeforeLoad == null) {
            previousMessageCount = uiState.chatMessages.size
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
            }

            scrollPositionBeforeLoad = null
            // 로드 완료 후 잠시 대기하여 연속 호출 방지
            kotlinx.coroutines.delay(500)
        }
    }

    // 새 메시지 도착 시 스크롤 처리
    LaunchedEffect(uiState.chatMessages.size) {
        if (uiState.chatMessages.isNotEmpty() && !uiState.shouldScrollToBottom && !uiState.isLoadingMore) {
            val currentMessageCount = uiState.chatMessages.size

            if (currentMessageCount > previousMessageCount) {
                // 새 메시지 도착 시 맨 아래 근처에 있으면 스크롤
                scope.launch {
                    val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                    val totalItems = listState.layoutInfo.totalItemsCount
                    val isNearBottom = lastVisibleItem?.index != null &&
                            (totalItems - lastVisibleItem.index) <= 5

                    if (isNearBottom) {
                        listState.animateScrollToItem(currentMessageCount - 1)
                        viewModel.markChatAsRead()
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
                }
            }
        }
    }

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

                // 채팅 메시지들 - key로 안정성 보장
                items(
                    items = uiState.chatMessages,
                    key = { message -> message.messageId }
                ) { message ->
                    ChatMessageItem(
                        message = message,
                        isMyMessage = viewModel.isMyMessage(message, userId)
                    )
                }
            }

            // 빈 상태 메시지
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

                        // gRPC 연결 상태 표시
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (uiState.grpcConnected) Color.Green else Color.Red,
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (uiState.grpcConnected) "연결됨" else "연결 중...",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }

        // 메시지 입력 영역
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 4.dp
        ) {
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
                            viewModel.sendMessage(messageText.trim())
                            messageText = ""
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

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ChatMessageItem(
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
            // 상대방 프로필 이미지 또는 기본 아바타
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
                // 프로필 이미지가 없을 때 기본 아바타
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MainColor.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = message.nickname.take(1), // 닉네임 첫 글자
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
                // 상대방 닉네임
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
                    // 내 메시지 - 시간이 왼쪽
                    Text(
                        text = message.timestamp,
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }

                // 메시지 말풍선
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
                    // 상대방 메시지 - 시간이 오른쪽
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