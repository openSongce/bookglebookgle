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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.entity.ChatMessage
import com.ssafy.bookglebookgle.entity.MessageType
import com.ssafy.bookglebookgle.entity.PerUserAnswer
import com.ssafy.bookglebookgle.entity.QuizQuestion
import com.ssafy.bookglebookgle.entity.QuizReveal
import com.ssafy.bookglebookgle.entity.QuizSummary
import com.ssafy.bookglebookgle.entity.UserScore
import com.ssafy.bookglebookgle.ui.component.CustomTopAppBar
import com.ssafy.bookglebookgle.ui.theme.BaseColor
import com.ssafy.bookglebookgle.ui.theme.DeepMainColor
import com.ssafy.bookglebookgle.ui.theme.MainColor
import com.ssafy.bookglebookgle.ui.theme.ResponsiveDimensions
import com.ssafy.bookglebookgle.ui.theme.defaultCornerRadius
import com.ssafy.bookglebookgle.ui.theme.defaultPadding
import com.ssafy.bookglebookgle.ui.theme.rememberResponsiveDimensions
import com.ssafy.bookglebookgle.viewmodel.ChatRoomViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val TAG = "싸피_ChatRoomScreen"

private val AVATAR_RES_MAP = mapOf(
    "whitebear" to R.drawable.whitebear_no_bg,
    "penguin"   to R.drawable.penguin_no_bg,
    "squirrel"  to R.drawable.squirrel_no_bg,
    "rabbit"    to R.drawable.rabbit_no_bg,
    "dog"       to R.drawable.dog_no_bg,
    "cat"       to R.drawable.cat_no_bg
)
private fun keyToResId(key: String?): Int? = key?.let { AVATAR_RES_MAP[it] }

@SuppressLint("NewApi", "UnusedBoxWithConstraintsScope")
@Composable
fun ChatRoomScreen(
    navController: NavHostController,
    groupId: Long,
    userId: Long,
    embedded: Boolean = false,
    viewModel: ChatRoomViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 반응형 디멘션 사용
    val dimensions = rememberResponsiveDimensions()
    val configuration = LocalConfiguration.current

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

    // 키보드 컨트롤러와 포커스 매니저 추가
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // 키보드를 숨기는 함수
    val hideKeyboard = {
        keyboardController?.hide()
        focusManager.clearFocus()
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

        // 메시지가 로드될 때까지 잠시 대기 후 스크롤
        kotlinx.coroutines.delay(300)
        if (uiState.chatMessages.isNotEmpty()) {
            scope.launch {
                listState.scrollToItem(uiState.chatMessages.size - 1)
                previousMessageCount = uiState.chatMessages.size
                showNewMessageButton = false
                newMessageCount = 0
            }
        }
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

                // 퀴즈/토론/AI 관련 중요 메시지인지 확인
                val hasImportantMessage = newMessages.any { message ->
                    message.type in listOf(
                        MessageType.QUIZ_START,
                        MessageType.QUIZ_QUESTION,
                        MessageType.QUIZ_REVEAL,
                        MessageType.QUIZ_SUMMARY,
                        MessageType.QUIZ_END,
                        MessageType.AI_RESPONSE,
                        MessageType.DISCUSSION_START,
                        MessageType.DISCUSSION_END
                    )
                }

                // 새 메시지 도착 시 맨 아래 근처에 있으면 스크롤
                scope.launch {
                    val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                    val totalItems = listState.layoutInfo.totalItemsCount
                    val isNearBottom = lastVisibleItem?.index != null &&
                            (totalItems - lastVisibleItem.index) <= 3

                    if (isNearBottom || hasMyMessage || hasImportantMessage) {
                        // 맨 아래 근처에 있으면 자동 스크롤
                        listState.animateScrollToItem(currentMessageCount - 1)
                        viewModel.markChatAsRead()
                        // 새 메시지 버튼 숨기기
                        showNewMessageButton = false
                        newMessageCount = 0
                    } else {
                        // 위에 있으면 새 메시지 버튼 표시
                        if (!hasImportantMessage) {
                            newMessageCount += newMessagesAdded
                            showNewMessageButton = true
                        } else {
                            // 중요 메시지인 경우에도 강제 스크롤 (사용자가 위에 있어도)
                            listState.animateScrollToItem(currentMessageCount - 1)
                            viewModel.markChatAsRead()
                            showNewMessageButton = false
                            newMessageCount = 0
                        }
                    }
                }
                previousMessageCount = currentMessageCount
            }
        }
    }

    LaunchedEffect(uiState.isQuizActive, uiState.currentQuestion) {
        if (uiState.isQuizActive && uiState.currentQuestion != null && uiState.chatMessages.isNotEmpty()) {
            scope.launch {
                // 퀴즈 문제가 나올 때 자동으로 맨 아래로 스크롤
                listState.animateScrollToItem(uiState.chatMessages.size - 1)
                viewModel.markChatAsRead()
                showNewMessageButton = false
                newMessageCount = 0
            }
        }
    }

    LaunchedEffect(uiState.isDiscussionActive) {
        if (uiState.chatMessages.isNotEmpty()) {
            scope.launch {
                // 토론 시작/종료 시 자동으로 맨 아래로 스크롤
                listState.animateScrollToItem(uiState.chatMessages.size - 1)
                viewModel.markChatAsRead()
                showNewMessageButton = false
                newMessageCount = 0
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

    // 퀴즈 요약이 나타날 때 자동 스크롤
    LaunchedEffect(uiState.quizSummary) {
        if (uiState.quizSummary != null && uiState.chatMessages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(uiState.chatMessages.size - 1)
                viewModel.markChatAsRead()
                showNewMessageButton = false
                newMessageCount = 0
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

    // 태블릿 레이아웃 적용
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = if (dimensions.isTablet) Alignment.TopCenter else Alignment.TopStart
    ) {
        Box(modifier = Modifier
            .widthIn(max = if (dimensions.isTablet) dimensions.contentMaxWidth * 1.5f else Dp.Infinity)
            .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .imePadding()
            ) {
                if (!embedded) {
                    CustomTopAppBar(
                        title = uiState.groupTitle,
                        isChatScreen = true,
                        navController = navController,
                    )
                }
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
                                .padding(horizontal = dimensions.defaultPadding, vertical = dimensions.spacingSmall),
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
                                            .size(dimensions.iconSizeSmall)
                                            .background(MainColor, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(dimensions.spacingTiny))
                                    Text(
                                        text = "AI 토론 진행 중",
                                        fontSize = dimensions.textSizeCaption,
                                        color = DeepMainColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(dimensions.iconSizeSmall)
                                            .background(Color.Gray, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(dimensions.spacingTiny))
                                    Text(
                                        text = "일반 채팅",
                                        fontSize = dimensions.textSizeCaption,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // 토론 시작/종료 버튼 (모임장만 볼 수 있음)
                            if (uiState.isHost) {
                                Button(
                                    onClick = {
                                        // 키보드 숨기기
                                        hideKeyboard()
                                        Log.d(TAG, "토론 버튼 클릭됨! 현재 상태: ${uiState.isDiscussionActive}")
                                        if (uiState.isDiscussionActive) {
                                            Log.d(TAG, "토론 종료 호출")
                                            viewModel.endDiscussion()
                                        } else {
                                            Log.d(TAG, "토론 시작 호출")
                                            viewModel.startDiscussion()
                                        }
                                    },
                                    modifier = Modifier.height(dimensions.buttonHeightSmall),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (uiState.isDiscussionActive) Color(
                                            0xFFE74C3C
                                        ) else Color(0xFF2ECC71)
                                    ),
                                    contentPadding = PaddingValues(horizontal = dimensions.spacingSmall),
                                    enabled = !uiState.isDiscussionConnecting
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isDiscussionActive) Icons.Default.Close else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(dimensions.iconSizeSmall),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(dimensions.spacingTiny))
                                    Text(
                                        text = if (uiState.isDiscussionActive) "토론 종료" else "토론 시작",
                                        fontSize = dimensions.textSizeCaption,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // 퀴즈 컨트롤 패널 (STUDY 카테고리일 때만 표시) - 새로 추가
                QuizControlPanel(
                    isStudyCategory = uiState.isStudyCategory,
                    isHost = uiState.isHost,
                    isQuizActive = uiState.isQuizActive,
                    isQuizConnecting = uiState.isQuizConnecting,
                    averageProgress = uiState.averageProgress,
                    isLoadingProgress = uiState.isLoadingProgress,
                    dimensions = dimensions,
                    onStartMidtermQuiz = {hideKeyboard()
                        viewModel.startMidtermQuiz() },
                    onStartFinalQuiz = { hideKeyboard()
                        viewModel.startFinalQuiz() },
                    onEndQuiz = { hideKeyboard()
                        viewModel.endQuiz() }
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
                            .padding(horizontal = dimensions.defaultPadding),
                        verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                        contentPadding = PaddingValues(
                            top = dimensions.defaultPadding,
                            bottom = dimensions.spacingLarge
                        )
                    ) {
                        // 위쪽에 더 불러오기 로딩 표시
                        if (uiState.isLoadingMore && uiState.hasMoreData) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(dimensions.defaultPadding),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = BaseColor,
                                            modifier = Modifier.size(dimensions.iconSizeMedium)
                                        )
                                        Spacer(modifier = Modifier.width(dimensions.spacingSmall))
                                        Text(
                                            text = "이전 메시지를 불러오는 중...",
                                            fontSize = dimensions.textSizeCaption,
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
                                        .padding(start = dimensions.defaultPadding, end = dimensions.defaultPadding),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "채팅의 시작입니다",
                                        fontSize = dimensions.textSizeCaption,
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
                                        .height(configuration.screenHeightDp.dp * 0.6f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator(
                                            color = BaseColor,
                                            modifier = Modifier.size(dimensions.iconSizeLarge)
                                        )
                                        Spacer(modifier = Modifier.height(dimensions.defaultPadding))
                                        Text(
                                            text = "채팅 불러오는 중...",
                                            fontSize = dimensions.textSizeBody,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }

                        // 채팅 메시지들
                        items(
                            items = uiState.chatMessages,
                            key = { message -> message.messageId }
                        ) { message ->
                            when (message.type) {
                                MessageType.QUIZ_START, MessageType.QUIZ_END -> {
                                    QuizSystemMessageItem(message = message, dimensions = dimensions)
                                }

                                MessageType.AI_RESPONSE -> {
                                    AiResponseMessageItem(message = message, dimensions = dimensions)
                                }

                                MessageType.DISCUSSION_START, MessageType.DISCUSSION_END -> {
                                    SystemMessageItem(message = message, dimensions = dimensions)
                                }

                                else -> {
                                    ChatMessageItem(
                                        message = message,
                                        isMyMessage = viewModel.isMyMessage(message, userId),
                                        dimensions = dimensions
                                    )
                                }
                            }
                        }

                        // AI 타이핑 인디케이터
                        if (uiState.isAiTyping) {
                            item {
                                AiTypingIndicator(dimensions = dimensions)
                            }
                        }
                    }
                    // 새 메시지 알림 버튼 추가
                    if (showNewMessageButton) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = dimensions.spacingLarge), // 입력창 위로 여유 공간
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(dimensions.iconSizeLarge)
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
                                    modifier = Modifier.size(dimensions.iconSizeMedium),
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
                                elevation = CardDefaults.cardElevation(defaultElevation = dimensions.spacingSmall)
                            ) {
                                Column(
                                    modifier = Modifier.padding(dimensions.defaultPadding)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🤖 AI 추천 주제",
                                            fontSize = dimensions.textSizeHeadline,
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

                                    Spacer(modifier = Modifier.height(dimensions.spacingSmall))

                                    if (uiState.currentAiResponse != null) {
                                        Text(
                                            text = uiState.currentAiResponse!!,
                                            fontSize = dimensions.textSizeBody,
                                            color = Color.Black,
                                            modifier = Modifier.padding(bottom = dimensions.spacingSmall)
                                        )
                                    }

                                    LazyColumn(
                                        modifier = Modifier.heightIn(max = 300.dp)
                                    ) {
                                        items(uiState.suggestedTopics) { topic ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = dimensions.spacingTiny)
                                                    .clickable {
                                                        Log.d(TAG, "추천 주제 선택: $topic")
                                                        viewModel.selectSuggestedTopic(topic)
                                                    },
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MainColor.copy(alpha = 0.06f)
                                                )
                                            ) {
                                                Text(
                                                    text = topic,
                                                    modifier = Modifier.padding(dimensions.spacingSmall),
                                                    fontSize = dimensions.textSizeBody
                                                )
                                            }
                                        }
                                    }
                                }
                            }
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
                                    fontSize = dimensions.textSizeSubtitle,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "채팅을 시작해보세요!",
                                    fontSize = dimensions.textSizeBody,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }

                // 메시지 입력 영역
                Surface(
                    modifier = Modifier.fillMaxWidth(),

                ) {
                    Column {
                        // 토론 중일 때 상태 표시바
                        if (uiState.isReadingCategory && uiState.isDiscussionActive && uiState.isDiscussionAutoDetected) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = BaseColor.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(dimensions.spacingSmall),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painterResource(id = R.drawable.ic_ai),
                                        contentDescription = null,
                                        tint = DeepMainColor,
                                        modifier = Modifier.size(dimensions.iconSizeSmall)
                                    )
                                    Spacer(modifier = Modifier.width(dimensions.spacingSmall))
                                    Text(
                                        text = "AI 토론이 진행 중입니다. AI가 대화를 분석하고 피드백을 제공합니다.",
                                        fontSize = dimensions.textSizeCaption,
                                        color = DeepMainColor
                                    )
                                }
                            }
                        }

                        // 퀴즈 진행 중일 때 상태 표시바
                        if (uiState.isStudyCategory && uiState.isQuizActive) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = BaseColor.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(dimensions.spacingSmall),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painterResource(id = R.drawable.ic_quiz),
                                        contentDescription = null,
                                        tint = DeepMainColor,
                                        modifier = Modifier.size(dimensions.iconSizeSmall)
                                    )
                                    Spacer(modifier = Modifier.width(dimensions.spacingSmall))
                                    Text(
                                        text = "퀴즈가 진행 중입니다. 문제가 나오면 답을 선택해주세요.",
                                        fontSize = dimensions.textSizeCaption,
                                        color = DeepMainColor
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(dimensions.inputFieldHeight)
                                .padding(dimensions.spacingSmall),
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
                                        .heightIn(min = dimensions.inputFieldMinHeight)
                                        .border(
                                            width = 1.dp,
                                            color = if (messageText.isNotEmpty()) BaseColor else Color.Gray.copy(
                                                alpha = 0.5f
                                            ),
                                            shape = RoundedCornerShape(dimensions.defaultCornerRadius)
                                        )
                                        .padding(horizontal = dimensions.spacingSmall, vertical = dimensions.spacingSmall),
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
                                            fontSize = dimensions.textSizeBody,
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
                                                        fontSize = dimensions.textSizeBody,
                                                        color = Color.Gray
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(dimensions.spacingSmall))

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
                                modifier = Modifier.size(dimensions.fabSize),
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

            // 퀴즈 문제 오버레이
            if (uiState.currentQuestion != null && uiState.isQuizActive) {
                QuizQuestionOverlay(
                    question = uiState.currentQuestion!!,
                    selectedAnswerIndex = uiState.selectedAnswerIndex,
                    isAnswerSubmitted = uiState.isAnswerSubmitted,
                    timeRemaining = uiState.quizTimeRemaining,
                    dimensions = dimensions,
                    onAnswerSelected = { index -> viewModel.selectQuizAnswer(index) },
                    onSubmitAnswer = { viewModel.submitQuizAnswer() }
                )
            }

            // 퀴즈 요약 오버레이 - 새로 추가
            if (uiState.quizSummary != null) {
                QuizSummaryOverlay(
                    quizSummary = uiState.quizSummary!!,
                    currentUserId = userId,
                    dimensions = dimensions,
                    onDismiss = { viewModel.dismissQuizSummary() }
                )
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
                            .padding(dimensions.spacingLarge),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = dimensions.spacingSmall)
                    ) {
                        Column(
                            modifier = Modifier.padding(dimensions.spacingLarge),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = BaseColor,
                                modifier = Modifier.size(dimensions.iconSizeXLarge),
                                strokeWidth = 4.dp
                            )
                            Spacer(modifier = Modifier.height(dimensions.spacingLarge))
                            Text(
                                text = "토론 준비 중입니다...",
                                fontSize = dimensions.textSizeHeadline,
                                fontWeight = FontWeight.Medium,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(dimensions.spacingSmall))
                            Text(
                                text = "AI가 토론을 준비하고 있어요",
                                fontSize = dimensions.textSizeBody,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // 퀴즈 연결 중 로딩 오버레이 - 새로 추가
            if (uiState.isStudyCategory && uiState.isQuizConnecting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.padding(dimensions.spacingLarge),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = dimensions.spacingSmall)
                    ) {
                        Column(
                            modifier = Modifier.padding(dimensions.spacingLarge),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = BaseColor,
                                modifier = Modifier.size(dimensions.iconSizeXLarge),
                                strokeWidth = 4.dp
                            )
                            Spacer(modifier = Modifier.height(dimensions.spacingLarge))
                            Text(
                                text = "퀴즈 준비 중입니다...",
                                fontSize = dimensions.textSizeHeadline,
                                fontWeight = FontWeight.Medium,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(dimensions.spacingSmall))
                            Text(
                                text = "퀴즈 문제를 준비하고 있어요",
                                fontSize = dimensions.textSizeBody,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

// AI 타이핑 인디케이터 컴포넌트
@Composable
fun AiTypingIndicator(dimensions: ResponsiveDimensions) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensions.spacingTiny),
        horizontalArrangement = Arrangement.Start
    ) {
        // AI 프로필 이미지
        Box(
            modifier = Modifier
                .size(dimensions.avatarSize)
                .clip(CircleShape)
                .background(BaseColor.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(id = R.drawable.ic_ai),
                contentDescription = "AI",
                tint = BaseColor,
                modifier = Modifier.size(dimensions.iconSizeMedium)
            )
        }

        Spacer(modifier = Modifier.width(dimensions.spacingSmall))

        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = "AI",
                fontSize = dimensions.textSizeCaption,
                fontWeight = FontWeight.Medium,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = dimensions.spacingTiny)
            )

            Surface(
                shape = RoundedCornerShape(
                    topStart = dimensions.cornerRadiusSmall,
                    topEnd = dimensions.defaultCornerRadius,
                    bottomStart = dimensions.defaultCornerRadius,
                    bottomEnd = dimensions.defaultCornerRadius
                ),
                color = Color.Gray.copy(alpha = 0.1f),
                modifier = Modifier.widthIn(min = 60.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = dimensions.defaultPadding, vertical = dimensions.spacingSmall),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.spacingTiny),
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
                                .size(dimensions.spacingTiny)
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
    isMyMessage: Boolean,
    dimensions: ResponsiveDimensions
) {
    // 메시지 타입에 따른 렌더링 분기
    when (message.type) {
        MessageType.QUIZ_START, MessageType.QUIZ_END,
        MessageType.QUIZ_QUESTION, MessageType.QUIZ_ANSWER,
        MessageType.QUIZ_REVEAL, MessageType.QUIZ_SUMMARY -> {
            QuizSystemMessageItem(message = message, dimensions = dimensions)
        }
        MessageType.AI_RESPONSE -> {
            AiResponseMessageItem(message = message, dimensions = dimensions)
        }

        MessageType.DISCUSSION_START, MessageType.DISCUSSION_END -> {
            SystemMessageItem(message = message, dimensions = dimensions)
        }

        else -> {
            RegularMessageItem(message = message, isMyMessage = isMyMessage, dimensions = dimensions)
        }
    }
}

@Composable
fun AiResponseMessageItem(message: ChatMessage, dimensions: ResponsiveDimensions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensions.spacingSmall)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = BaseColor.copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier.padding(dimensions.spacingSmall)
            ) {
                Icon(
                    painterResource(id = R.drawable.ic_ai),
                    contentDescription = "AI",
                    tint = BaseColor,
                    modifier = Modifier.size(dimensions.iconSizeLarge)
                )
                Spacer(modifier = Modifier.width(dimensions.spacingSmall))
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "AI 분석 결과",
                        fontSize = dimensions.textSizeCaption,
                        fontWeight = FontWeight.Bold,
                        color = BaseColor
                    )
                    Spacer(modifier = Modifier.height(dimensions.spacingTiny))

                    // AI 응답 내용
                    if (message.aiResponse != null) {
                        Text(
                            text = message.aiResponse!!,
                            fontSize = dimensions.textSizeBody,
                            color = Color.Black
                        )
                    }

                    // 일반 메시지 내용도 표시 (필요한 경우)
                    if (message.message.isNotBlank()) {
                        Spacer(modifier = Modifier.height(dimensions.spacingSmall))
                        Text(
                            text = message.message,
                            fontSize = dimensions.textSizeBody,
                            color = Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.height(dimensions.spacingTiny))
                    Text(
                        text = message.timestamp,
                        fontSize = dimensions.textSizeCaption,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun SystemMessageItem(message: ChatMessage, dimensions: ResponsiveDimensions) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensions.spacingSmall),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = when (message.type) {
                    MessageType.DISCUSSION_START -> Color(0xFF2ECC71).copy(alpha = 0.03f)
                    MessageType.DISCUSSION_END -> Color(0xFFE74C3C).copy(alpha = 0.03f)
                    else -> Color.Gray.copy(alpha = 0.1f)
                }
            )
        ) {
            Row(
                modifier = Modifier.padding(dimensions.spacingSmall),
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
                    modifier = Modifier.size(dimensions.iconSizeSmall)
                )

                Spacer(modifier = Modifier.width(dimensions.spacingSmall))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = message.message,
                            fontSize = dimensions.textSizeBody,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                    }
                    Text(
                        text = message.timestamp,
                        fontSize = dimensions.textSizeCaption,
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
    isMyMessage: Boolean,
    dimensions: ResponsiveDimensions
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensions.spacingTiny),
        horizontalArrangement = if (isMyMessage) Arrangement.End else Arrangement.Start
    ) {
        if (!isMyMessage) {
            // 퀴즈 관련 메시지인지 확인
            val isQuizMessage = message.type in listOf(
                MessageType.QUIZ_ANSWER,
                MessageType.QUIZ_QUESTION,
                MessageType.QUIZ_START,
                MessageType.QUIZ_END
            )

            val avatarBg: Color =
                parseColorOrNull(message.avatarBgColor) ?: MainColor.copy(alpha = 0.3f)
            val localRes = keyToResId(message.profileImage)

            if (message.profileImage != null && message.profileImage.isNotBlank() && !isQuizMessage) {
                AsyncImage(
                    model = localRes,
                    contentDescription = "프로필 이미지",
                    modifier = Modifier
                        .size(dimensions.avatarSize)
                        .clip(CircleShape)
                        .background(avatarBg), // 배경색 살짝 깔아줘도 OK (투명 PNG 대비)
                    contentScale = ContentScale.Crop
                )

            } else {
                Box(
                    modifier = Modifier
                        .size(dimensions.avatarSize)
                        .clip(CircleShape)
                        .background(avatarBg),
                    contentAlignment = Alignment.Center
                ) {
                    if (isQuizMessage || message.nickname.isEmpty()) {
                        // 퀴즈 아이콘 표시
                        Icon(
                            painterResource(R.drawable.ic_quiz),
                            contentDescription = "퀴즈",
                            tint = BaseColor,
                            modifier = Modifier.size(dimensions.iconSizeMedium)
                        )
                    } else {
                        Text(
                            text = message.nickname.take(1),
                            fontSize = dimensions.textSizeSubtitle,
                            fontWeight = FontWeight.Bold,
                            color = BaseColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(dimensions.spacingSmall))
        }

        Column(
            horizontalAlignment = if (isMyMessage) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            if (!isMyMessage) {
                Text(
                    text = message.nickname,
                    fontSize = dimensions.textSizeCaption,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = dimensions.spacingTiny)
                )
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = if (isMyMessage) Arrangement.End else Arrangement.Start
            ) {
                if (isMyMessage) {
                    Text(
                        text = message.timestamp,
                        fontSize = dimensions.textSizeCaption,
                        color = Color.Gray,
                        modifier = Modifier.padding(end = dimensions.spacingTiny)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(
                        topStart = if (isMyMessage) dimensions.defaultCornerRadius else dimensions.cornerRadiusSmall,
                        topEnd = if (isMyMessage) dimensions.cornerRadiusSmall else dimensions.defaultCornerRadius,
                        bottomStart = dimensions.defaultCornerRadius,
                        bottomEnd = dimensions.defaultCornerRadius
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
                        modifier = Modifier.padding(dimensions.spacingSmall),
                        color = Color.Black,
                        fontSize = dimensions.textSizeBody
                    )
                }

                if (!isMyMessage) {
                    Text(
                        text = message.timestamp,
                        fontSize = dimensions.textSizeCaption,
                        color = Color.Gray,
                        modifier = Modifier.padding(start = dimensions.spacingTiny)
                    )
                }
            }
        }
    }
}

@Composable
fun QuizControlPanel(
    isStudyCategory: Boolean,
    isHost: Boolean,
    isQuizActive: Boolean,
    isQuizConnecting: Boolean,
    averageProgress: Int,
    isLoadingProgress: Boolean,
    dimensions: ResponsiveDimensions,
    onStartMidtermQuiz: () ->Unit,
    onStartFinalQuiz: () ->Unit,
    onEndQuiz: () -> Unit
) {
    if (isStudyCategory) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 0.5.dp,
            color = Color.White
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimensions.defaultPadding, vertical = dimensions.spacingSmall),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 퀴즈 상태 표시
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isQuizActive) {
                        Box(
                            modifier = Modifier
                                .size(dimensions.iconSizeSmall)
                                .background(BaseColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(dimensions.spacingTiny))
                        Text(
                            text = "퀴즈 진행 중",
                            fontSize = dimensions.textSizeCaption,
                            color = BaseColor,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(dimensions.iconSizeSmall)
                                .background(Color.Gray, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(dimensions.spacingTiny))
                        Text(
                            text = "일반 채팅",
                            fontSize = dimensions.textSizeCaption,
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 퀴즈 시작/종료 버튼 (모임장만 볼 수 있음)
                if (isHost && !isQuizActive) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onStartMidtermQuiz,
                            modifier = Modifier.height(dimensions.buttonHeightSmall),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isQuizActive) Color(0xFFE74C3C) else Color(
                                    0xFF2196F3
                                )
                            ),
                            contentPadding = PaddingValues(horizontal = dimensions.spacingSmall),
                            enabled = !isQuizConnecting && averageProgress >= 50 && !isLoadingProgress
                        ) {

                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(dimensions.iconSizeSmall),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(dimensions.spacingTiny))
                            Text(
                                text = "중간 퀴즈",
                                fontSize = dimensions.textSizeCaption,
                                color = Color.White
                            )
                        }
                        Button(
                            onClick = onStartFinalQuiz,
                            modifier = Modifier.height(dimensions.buttonHeightSmall),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isQuizActive) Color(0xFFE74C3C) else Color(
                                    0xFF2196F3
                                )
                            ),
                            contentPadding = PaddingValues(horizontal = dimensions.spacingSmall),
                            enabled = !isQuizConnecting && averageProgress >= 100 && !isLoadingProgress
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(dimensions.iconSizeSmall),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(dimensions.spacingTiny))
                            Text(
                                text = "최종 퀴즈",
                                fontSize = dimensions.textSizeCaption,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuizQuestionOverlay(
    question: QuizQuestion,
    selectedAnswerIndex: Int?,
    isAnswerSubmitted: Boolean,
    timeRemaining: Int,
    dimensions: ResponsiveDimensions,
    onAnswerSelected: (Int) -> Unit,
    onSubmitAnswer: () -> Unit
) {
    // 키보드 컨트롤러와 포커스 매니저 추가
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // 키보드를 숨기는 함수
    val hideKeyboard = {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    // 퀴즈 오버레이가 나타날 때 키보드 숨기기
    LaunchedEffect(Unit) {
        hideKeyboard()
    }

    // 시간이 0이 되면 자동 제출
    LaunchedEffect(timeRemaining) {
        if (timeRemaining == 0 && selectedAnswerIndex != null && !isAnswerSubmitted) {
            onSubmitAnswer()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(dimensions.defaultPadding),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = dimensions.spacingSmall)
        ) {
            Column(
                modifier = Modifier.padding(dimensions.spacingLarge)
            ) {
                // 문제 헤더
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "문제 ${question.questionIndex + 1}",
                        fontSize = dimensions.textSizeHeadline,
                        fontWeight = FontWeight.Bold,
                        color = BaseColor
                    )

                    // 타이머
                    Box(
                        modifier = Modifier
                            .background(
                                color = when {
                                    timeRemaining <= 3 -> Color.Red
                                    timeRemaining <= 5 -> Color(0xFFFF9800) // 주황색
                                    else -> BaseColor
                                },
                                shape = RoundedCornerShape(dimensions.spacingSmall)
                            )
                            .padding(horizontal = dimensions.spacingSmall, vertical = dimensions.spacingTiny)
                    ) {
                        Text(
                            text = "${timeRemaining}초",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = dimensions.textSizeBody
                        )
                    }
                }

                Spacer(modifier = Modifier.height(dimensions.defaultPadding))

                // 문제 텍스트
                Text(
                    text = question.questionText,
                    fontSize = dimensions.textSizeSubtitle,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(dimensions.spacingLarge))

                // 선택지들
                question.options.forEachIndexed { index, option ->
                    QuizOptionButton(
                        index = index,
                        text = option,
                        isSelected = selectedAnswerIndex == index,
                        isSubmitted = isAnswerSubmitted,
                        dimensions = dimensions,
                        onClick = {
                            if (!isAnswerSubmitted) {
                                onAnswerSelected(index)
                            }
                        }
                    )

                    if (index < question.options.size - 1) {
                        Spacer(modifier = Modifier.height(dimensions.spacingSmall))
                    }
                }

                Spacer(modifier = Modifier.height(dimensions.spacingLarge))

                // 제출 버튼
                Button(
                    onClick = onSubmitAnswer,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedAnswerIndex != null && !isAnswerSubmitted,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BaseColor,
                        disabledContainerColor = Color.Gray
                    )
                ) {
                    Text(
                        text = when {
                            isAnswerSubmitted -> "제출 완료"
                            timeRemaining == 0 -> "시간 종료"
                            else -> "답안 제출"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (isAnswerSubmitted) {
                    Spacer(modifier = Modifier.height(dimensions.spacingSmall))
                    Text(
                        text = "답안이 제출되었습니다. 잠시 후 결과가 공개됩니다.",
                        fontSize = dimensions.textSizeCaption,
                        color = Color.Gray,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                else if (timeRemaining <= 5 && selectedAnswerIndex == null) {
                    Spacer(modifier = Modifier.height(dimensions.spacingSmall))
                    Text(
                        text = "시간이 얼마 남지 않았습니다! 답을 선택해주세요.",
                        fontSize = dimensions.textSizeCaption,
                        color = Color.Red,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// 퀴즈 정답 버튼 컴포넌트
@Composable
fun QuizOptionButton(
    index: Int,
    text: String,
    isSelected: Boolean,
    isSubmitted: Boolean,
    dimensions: ResponsiveDimensions,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected && !isSubmitted -> BaseColor.copy(alpha = 0.1f)
        isSelected && isSubmitted -> BaseColor.copy(alpha = 0.2f)
        else -> Color.Gray.copy(alpha = 0.05f)
    }

    val borderColor = when {
        isSelected -> BaseColor
        else -> Color.Gray.copy(alpha = 0.3f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isSubmitted) { onClick() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensions.defaultPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 번호 표시
            Box(
                modifier = Modifier
                    .size(dimensions.iconSizeLarge)
                    .background(
                        color = if (isSelected) BaseColor else Color.Gray.copy(alpha = 0.3f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    color = if (isSelected) Color.White else Color.Gray,
                    fontSize = dimensions.textSizeCaption,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(dimensions.spacingSmall))

            // 선택지 텍스트
            Text(
                text = text,
                fontSize = dimensions.textSizeBody,
                color = Color.Black,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun QuizSummaryOverlay(
    quizSummary: QuizSummary,
    currentUserId: Long,
    dimensions: ResponsiveDimensions,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensions.defaultPadding),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = dimensions.spacingSmall)
        ) {
            Column(
                modifier = Modifier.padding(dimensions.spacingLarge),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "퀴즈 결과",
                    fontSize = dimensions.textSizeHeadline,
                    fontWeight = FontWeight.Bold,
                    color = BaseColor
                )

                Spacer(modifier = Modifier.height(dimensions.defaultPadding))

                Text(
                    text = "총 ${quizSummary.totalQuestions}문제",
                    fontSize = dimensions.textSizeSubtitle,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(dimensions.spacingLarge))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 300.dp), // 최소 높이 설정으로 일관성 확보
                    contentAlignment = Alignment.Center // Box 내부 요소들을 중앙 정렬
                ) {
                    // 결과가 있는지 확인
                    if (quizSummary.scores.isNotEmpty()) {
                        // 랭킹이 제대로 설정되어 있는지 확인
                        val hasValidRanking = quizSummary.scores.any { it.rank > 0 }

                        if (hasValidRanking) {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 300.dp)
                            ) {
                                items(quizSummary.scores.sortedBy { it.rank }) { score ->
                                    QuizScoreItem(
                                        userScore = score,
                                        totalQuestions = quizSummary.totalQuestions,
                                        isCurrentUser = score.userId == currentUserId,
                                        showRanking = true,
                                        dimensions = dimensions
                                    )
                                }
                            }
                        } else {
                            // 랭킹이 없는 경우 - 참여자별 결과만 표시
                            Text(
                                text = "참여자별 결과",
                                fontSize = dimensions.textSizeSubtitle,
                                fontWeight = FontWeight.Medium,
                                color = Color.Black,
                                modifier = Modifier.padding(bottom = dimensions.spacingSmall)
                            )

                            LazyColumn(
                                modifier = Modifier.heightIn(max = 300.dp)
                            ) {
                                items(quizSummary.scores.sortedBy { it.nickname }) { score ->
                                    QuizScoreItem(
                                        userScore = score,
                                        totalQuestions = quizSummary.totalQuestions,
                                        isCurrentUser = score.userId == currentUserId,
                                        showRanking = false,
                                        dimensions = dimensions
                                    )
                                }
                            }
                        }
                    } else {
                        // 결과가 아예 없는 경우
                        Text(
                            text = "참여한 사용자가 없습니다.",
                            fontSize = dimensions.textSizeBody,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = dimensions.spacingLarge)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(dimensions.spacingLarge))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BaseColor
                    )
                ) {
                    Text(
                        text = "확인",
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun QuizScoreItem(
    userScore: UserScore,
    totalQuestions: Int,
    isCurrentUser: Boolean,
    showRanking: Boolean = true,
    dimensions: ResponsiveDimensions
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensions.spacingTiny),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCurrentUser -> BaseColor.copy(alpha = 0.1f)
                showRanking && userScore.rank == 1 -> Color(0xFFFFD700).copy(alpha = 0.2f) // 금색
                showRanking && userScore.rank == 2 -> Color(0xFFC0C0C0).copy(alpha = 0.2f) // 은색
                showRanking && userScore.rank == 3 -> Color(0xFFCD7F32).copy(alpha = 0.2f) // 동색
                else -> Color.Gray.copy(alpha = 0.05f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensions.spacingSmall),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showRanking && userScore.rank > 0) {
                    // 순위 아이콘 (랭킹이 있는 경우에만)
                    val rankIcon = when (userScore.rank) {
                        1 -> "🥇"
                        2 -> "🥈"
                        3 -> "🥉"
                        else -> "${userScore.rank}위"
                    }

                    Text(
                        text = rankIcon,
                        fontSize = dimensions.textSizeSubtitle,
                        modifier = Modifier.padding(end = dimensions.spacingSmall)
                    )
                } else {
                    // 랭킹이 없는 경우 단순 불릿 포인트
                    Box(
                        modifier = Modifier
                            .size(dimensions.spacingTiny)
                            .background(BaseColor, CircleShape)
                            .padding(end = dimensions.spacingSmall)
                    )
                    Spacer(modifier = Modifier.width(dimensions.spacingSmall))
                }

                Spacer(modifier = Modifier.width(dimensions.spacingSmall))

                Text(
                    text = "${userScore.nickname}${if (isCurrentUser) " (나)" else ""}",
                    fontSize = dimensions.textSizeBody,
                    fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Normal
                )
            }

            Text(
                text = "${userScore.correctCount}/${totalQuestions}",
                fontSize = dimensions.textSizeBody,
                fontWeight = FontWeight.Medium,
                color = BaseColor
            )
        }
    }
}

// 시스템 메시지 아이템 (퀴즈 관련 메시지용)
@Composable
fun QuizSystemMessageItem(message: ChatMessage, dimensions: ResponsiveDimensions) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensions.spacingSmall),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = when (message.type) {
                    MessageType.QUIZ_START -> DeepMainColor.copy(alpha = 0.1f)
                    MessageType.QUIZ_QUESTION -> DeepMainColor.copy(alpha = 0.1f)
                    MessageType.QUIZ_ANSWER -> MainColor
                    MessageType.QUIZ_REVEAL -> DeepMainColor.copy(alpha = 0.1f)
                    MessageType.QUIZ_SUMMARY -> DeepMainColor.copy(alpha = 0.1f)
                    MessageType.QUIZ_END -> Color(0xFFE74C3C).copy(alpha = 0.3f)
                    else -> Color.Gray.copy(alpha = 0.1f)
                }
            )
        ) {
            Row(
                modifier = Modifier.padding(dimensions.spacingSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (iconVector, iconColor, displayText) = when (message.type) {
                    MessageType.QUIZ_START -> Triple(
                        R.drawable.ic_play,
                        DeepMainColor,
                        "퀴즈가 시작되었습니다!"
                    )
                    MessageType.QUIZ_QUESTION -> Triple(
                        R.drawable.ic_quiz,
                        DeepMainColor,
                        "새로운 문제가 출제되었습니다"
                    )
                    MessageType.QUIZ_ANSWER -> Triple(
                        R.drawable.ic_answer,
                        DeepMainColor,
                        message.message // 사용자별 답안 제출 메시지
                    )
                    MessageType.QUIZ_REVEAL -> Triple(
                        R.drawable.ic_quiz,
                        DeepMainColor,
                        "정답이 공개되었습니다"
                    )
                    MessageType.QUIZ_SUMMARY -> Triple(
                        R.drawable.ic_quiz,
                        DeepMainColor,
                        "퀴즈 결과가 나왔습니다"
                    )
                    MessageType.QUIZ_END -> Triple(
                        R.drawable.ic_check,
                        Color(0xFFE74C3C),
                        "퀴즈가 종료되었습니다"
                    )
                    else -> Triple(
                        R.drawable.ic_play,
                        Color.Gray,
                        message.message
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = iconVector),
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(dimensions.iconSizeSmall)
                        )
                        Spacer(modifier = Modifier.width(dimensions.spacingSmall))
                        Text(
                            text = message.message,
                            fontSize = dimensions.textSizeBody,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                    }
                    Text(
                        text = message.timestamp,
                        fontSize = dimensions.textSizeCaption,
                        color = Color.Gray,
                        modifier = Modifier.padding(start = dimensions.iconSizeLarge)
                    )
                }
            }
        }
    }
}

private fun parseColorOrNull(hex: String?): Color? = try {
    hex?.let { Color(android.graphics.Color.parseColor(it)) }
} catch (_: IllegalArgumentException) { null }