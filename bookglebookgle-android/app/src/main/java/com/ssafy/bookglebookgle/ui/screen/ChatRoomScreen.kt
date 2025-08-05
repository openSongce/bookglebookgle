package com.ssafy.bookglebookgle.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ssafy.bookglebookgle.entity.ChatMessage
import com.ssafy.bookglebookgle.ui.component.CustomTopAppBar
import com.ssafy.bookglebookgle.viewmodel.ChatRoomViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    navController: NavHostController,
    groupId: Long,
    viewModel: ChatRoomViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 채팅방 입장
    LaunchedEffect(groupId) {
        viewModel.enterChatRoom(groupId)
    }

    // 새 메시지가 추가될 때마다 맨 아래로 스크롤
    LaunchedEffect(uiState.chatMessages.size) {
        if (uiState.chatMessages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(uiState.chatMessages.size - 1)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        CustomTopAppBar(
            title = uiState.currentChatRoom?.groupId?.toString() ?: "채팅방",
            isChatScreen = true,
            navController = navController,
        )

        // 채팅 메시지 목록
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            if (uiState.error != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f))
                    ) {
                        Text(
                            text = uiState.error!!,
                            modifier = Modifier.padding(16.dp),
                            color = Color.Red
                        )
                    }
                }
            }

            items(uiState.chatMessages) { message ->
//                ChatMessageItem(message = message)
            }
        }

        // 메시지 입력 영역
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("메시지를 입력하세요...") },
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                FloatingActionButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            // TODO: gRPC로 메시지 전송
                            // viewModel.sendMessage(messageText.trim())

                            // 임시로 더미 메시지 추가 (나중에 gRPC로 대체)
                            val dummyMessage = ChatMessage(
                                messageId = System.currentTimeMillis(),
                                userId = 1L,
                                nickname = "나",
                                profileImage = null,
                                message = messageText.trim(),
                                timestamp = "방금 전",
                            )
                            viewModel.addNewMessage(dummyMessage)
                            messageText = ""
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.primary
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

//@Composable
//fun ChatMessageItem(message: ChatMessage) {
//    Row(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(vertical = 4.dp),
//        horizontalArrangement = if (message.isMyMessage) Arrangement.End else Arrangement.Start
//    ) {
//        if (!message.isMyMessage) {
//            // 상대방 프로필 이미지
//            AsyncImage(
//                model = message.profileImage,
//                contentDescription = "프로필 이미지",
//                modifier = Modifier
//                    .size(40.dp)
//                    .clip(CircleShape)
//                    .background(Color.Gray.copy(alpha = 0.3f)),
//                contentScale = ContentScale.Crop
//            )
//
//            Spacer(modifier = Modifier.width(8.dp))
//        }
//
//        Column(
//            horizontalAlignment = if (message.isMyMessage) Alignment.End else Alignment.Start,
//            modifier = Modifier.widthIn(max = 280.dp)
//        ) {
//            if (!message.isMyMessage) {
//                // 상대방 닉네임
//                Text(
//                    text = message.nickname,
//                    fontSize = 12.sp,
//                    fontWeight = FontWeight.Medium,
//                    color = Color.Gray,
//                    modifier = Modifier.padding(bottom = 4.dp)
//                )
//            }
//
//            Row(
//                verticalAlignment = Alignment.Bottom,
//                horizontalArrangement = if (message.isMyMessage) Arrangement.End else Arrangement.Start
//            ) {
//                if (message.isMyMessage) {
//                    // 내 메시지 - 시간이 왼쪽
//                    Text(
//                        text = message.timestamp,
//                        fontSize = 10.sp,
//                        color = Color.Gray,
//                        modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)
//                    )
//                }
//
//                // 메시지 말풍선
//                Surface(
//                    shape = RoundedCornerShape(
//                        topStart = 16.dp,
//                        topEnd = 16.dp,
//                        bottomStart = if (message.isMyMessage) 16.dp else 4.dp,
//                        bottomEnd = if (message.isMyMessage) 4.dp else 16.dp
//                    ),
//                    color = if (message.isMyMessage) {
//                        MaterialTheme.colorScheme.primary
//                    } else {
//                        Color.Gray.copy(alpha = 0.1f)
//                    }
//                ) {
//                    Text(
//                        text = message.message,
//                        modifier = Modifier.padding(12.dp),
//                        color = if (message.isMyMessage) Color.White else Color.Black,
//                        fontSize = 14.sp
//                    )
//                }
//
//                if (!message.isMyMessage) {
//                    // 상대방 메시지 - 시간이 오른쪽
//                    Text(
//                        text = message.timestamp,
//                        fontSize = 10.sp,
//                        color = Color.Gray,
//                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
//                    )
//                }
//            }
//        }
//
//        if (message.isMyMessage) {
//            Spacer(modifier = Modifier.width(48.dp)) // 오른쪽 여백
//        }
//    }
//}