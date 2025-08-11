package com.ssafy.bookglebookgle.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.navigation.Screen
import com.ssafy.bookglebookgle.ui.theme.BaseColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTopAppBar(
    title: String,
    navController: NavHostController,
    ismygroup: Boolean = false,
    isHost: Boolean = false,
    isDetailScreen: Boolean = false,
    isChatScreen: Boolean = false,
    isPdfView: Boolean = false,
    onBackPressed: (() -> Unit)? = null, // 뒤로가기 버튼 클릭 콜백
    onEditClick: (() -> Unit)? = null,
    onSearchPerformed: ((String) -> Unit)? = null, // 검색 실행 콜백
    onSearchCancelled: (() -> Unit)? = null, // 검색 취소 콜백 추가
    onChatSettingsClick: (() -> Unit)? = null, // 채팅 설정 콜백 추가
    onMyGroupFilterClick: (() -> Unit)? = null, // 내 모임 필터 콜백 추가
    onParticipantsClick: (() -> Unit)? = null,
    onChatClick: (() -> Unit)? = null
) {
    var isSearchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    // 디바운스를 위한 LaunchedEffect
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            // 0.3초 지연 후 검색 실행
            kotlinx.coroutines.delay(300)
            onSearchPerformed?.invoke(searchQuery.trim())
        } else if (searchQuery.isEmpty()) {
            // 검색어가 비어있으면 즉시 검색 결과 초기화
            onSearchCancelled?.invoke()
        }
    }

    TopAppBar(
        title = {
            if (isSearchMode && title == "main_home") {
                CompositionLocalProvider(
                    LocalTextSelectionColors provides TextSelectionColors(
                        handleColor = BaseColor,
                        backgroundColor = BaseColor.copy(alpha = 0.3f)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .padding(end = 8.dp)
                            .border(1.dp, BaseColor, RoundedCornerShape(6.dp))
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { newValue ->
                                searchQuery = newValue
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 12.sp,
                                color = Color.Black
                            ),
                            cursorBrush = SolidColor(BaseColor),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (searchQuery.isNotBlank()) {
                                        onSearchPerformed?.invoke(searchQuery.trim())
                                        keyboardController?.hide()
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp)
                                .wrapContentHeight(Alignment.CenterVertically),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            "모임을 검색해보세요",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }
            } else if (!isSearchMode && title == "main_home") {
                Text(
                    text = "북글북글",
                    fontWeight        = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp)
                )
            } else if (title == "create_group") {
                Text(
                    text = "모임 개설",
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp)
                )
            } else if (title == "chat") {
                Text(
                    text = "채팅",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp)
                )
            } else if (title == "my_page") {
                Text(
                    text = "내 프로필",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp)
                )
            } else if (title == "my_group") {
                Text(
                    text = "내 모임",
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp)
                )
            } else if (isPdfView) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            } else if (ismygroup && isHost) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            } else if(ismygroup && !isHost) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 40.dp)
                )
            } else if (isDetailScreen) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 40.dp)
                )
            }
            else if(isChatScreen){
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 40 .dp)
                )
            }
        },
        navigationIcon = {
            if (isPdfView || ismygroup || isDetailScreen || isChatScreen) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_back_arrow_left),
                    contentDescription = "뒤로가기",
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .size(24.dp)
                        .clickable { navController.popBackStack() }
                )
            }
        },
        actions = {
            if (title == "main_home" && isSearchMode) {
                // 검색 모드일 때: 취소, 검색 버튼
                Row(
                    modifier = Modifier.padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 취소 버튼
                    Icon(
                        painter = painterResource(id = R.drawable.ic_cancel),
                        contentDescription = "취소",
                        modifier = Modifier
                            .size(20.dp)
                            .clickable {
                                isSearchMode = false
                                searchQuery = ""
                                keyboardController?.hide()
                                // 검색 취소 시 결과 초기화
                                onSearchCancelled?.invoke()
                            }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    // 검색 실행 버튼
                    Icon(
                        painter = painterResource(id = R.drawable.main_search),
                        contentDescription = "검색 실행",
                        modifier = Modifier
                            .size(20.dp)
                            .clickable {
                                if (searchQuery.isNotBlank()) {
                                    onSearchPerformed?.invoke(searchQuery.trim())
                                    keyboardController?.hide()
                                }
                            }
                    )
                }
            } else if (title == "main_home" && !isSearchMode) {
                Icon(
                    painter = painterResource(id = R.drawable.main_search),
                    contentDescription = "검색",
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(20.dp)
                        .clickable {
                            isSearchMode = true
                        }
                )
            } else if (title == "my_page") {
                Icon(
                    painter = painterResource(id = R.drawable.my_profile_setting),
                    contentDescription = "설정",
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(20.dp)
                        .clickable {

                        }
                )
            } else if (title == "chat" || title == "my_group") {
                Icon(
                    painter = painterResource(id = R.drawable.ic_filter),
                    contentDescription = "필터",
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(20.dp)
                        .clickable {
                            if (title == "chat") {
                                onChatSettingsClick?.invoke()
                            } else if(title == "my_group"){
                                onMyGroupFilterClick?.invoke()
                            }

                        }
                )
            }else if (title == "create_group") {
                Icon(
                    painter = painterResource(id = R.drawable.ic_cancel),
                    contentDescription = "취소",
                    modifier = Modifier
                        .size(36.dp)
                        .padding(end = 16.dp)
                        .clickable { onBackPressed?.invoke() }
                )
            } else if (isPdfView) {

                Icon(
                    painter = painterResource(id = R.drawable.main_selected_chat),
                    contentDescription = "채팅 열기",
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(24.dp)
                        .clickable { onChatClick?.invoke() }
                )

                Icon(
                    painter = painterResource(id = R.drawable.profile_image),
                    contentDescription = "접속인원",
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onParticipantsClick?.invoke() }
                )
            } else if (ismygroup && isHost) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_edit),
                    contentDescription = "모임수정",
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(24.dp)
                        .clickable { onEditClick?.invoke() ?: navController.popBackStack() }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.White,
        )
    )
}