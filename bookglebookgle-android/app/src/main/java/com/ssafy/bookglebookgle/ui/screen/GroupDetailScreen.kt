package com.ssafy.bookglebookgle.ui.screen

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.entity.GroupDetailResponse
import com.ssafy.bookglebookgle.navigation.Screen
import com.ssafy.bookglebookgle.ui.component.CustomTopAppBar
import com.ssafy.bookglebookgle.ui.component.GroupEditDialog
import com.ssafy.bookglebookgle.ui.theme.MainColor
import com.ssafy.bookglebookgle.util.ScreenSize
import com.ssafy.bookglebookgle.viewmodel.EditGroupUiState
import com.ssafy.bookglebookgle.viewmodel.GroupDetailUiState
import com.ssafy.bookglebookgle.viewmodel.GroupDetailViewModel
import com.ssafy.bookglebookgle.viewmodel.JoinGroupUiState

@Composable
fun GroupDetailScreen(
    navController: NavHostController,
    groupId: Long,
    isMyGroup: Boolean,
    viewModel: GroupDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val joinGroupState by viewModel.joinGroupState.collectAsStateWithLifecycle()
    val editGroupState by viewModel.editGroupState.collectAsStateWithLifecycle()
    val currentIsMyGroup by viewModel.isMyGroup.collectAsStateWithLifecycle()

    // 수정 다이얼로그 상태
    var showEditDialog by remember { mutableStateOf(false) }

    // 컴포넌트가 처음 생성될 때 그룹 상세 정보 조회
    LaunchedEffect(groupId, isMyGroup) {
        viewModel.setInitialMyGroupState(isMyGroup)
        viewModel.getGroupDetail(groupId)
    }

    // 가입 성공 시 처리
    LaunchedEffect(joinGroupState) {
        when (joinGroupState) {
            is JoinGroupUiState.Success -> {
                Log.d("GroupDetailScreen", "그룹 가입 성공!")
                // 성공 처리 후 상태 초기화
                viewModel.resetJoinGroupState()
            }
            is JoinGroupUiState.Error -> {
                Log.e("GroupDetailScreen", "그룹 가입 실패: ${(joinGroupState as JoinGroupUiState.Error).message}")
                // 여기서 사용자에게 에러 메시지를 보여줄 수 있습니다 (Toast, Snackbar 등)
            }
            else -> {}
        }
    }

    LaunchedEffect(editGroupState) {
        when (editGroupState) {
            is EditGroupUiState.Success -> {
                Log.d("GroupDetailScreen", "그룹 수정 성공!")
                viewModel.resetEditGroupState()
                showEditDialog = false
                // 수정 완료 후 화면 새로고침
                viewModel.getGroupDetail(groupId)
            }
            is EditGroupUiState.Error -> {
                Log.e("GroupDetailScreen", "그룹 수정 실패: ${(editGroupState as EditGroupUiState.Error).message}")
                // TODO: 사용자에게 에러 메시지 표시 (Toast, Snackbar 등)
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        val title = when (val currentState = uiState) {
            is GroupDetailUiState.Success -> currentState.groupDetail.roomTitle
            else -> "모임 상세"
        }

        CustomTopAppBar(
            title = title,
            navController = navController,
            ismygroup = isMyGroup,
            isDetailScreen = true,
            onEditClick = if (currentIsMyGroup) {
                { showEditDialog = true }
            } else null
        )

        when (val currentState = uiState) {
            is GroupDetailUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFDED0BB)
                    )
                }
            }

            is GroupDetailUiState.Success -> {
                GroupDetailContent(
                    groupDetail = currentState.groupDetail,
                    isMyGroup = currentIsMyGroup,
                    isJoining = joinGroupState is JoinGroupUiState.Loading,
                    navController = navController,
                    groupId = groupId,
                    onJoinClick = { viewModel.joinGroup(groupId) },
                    onDeleteClick = {
                        viewModel.deleteGroup(groupId)
                        navController.popBackStack() },
                    onLeaveClick = { /* Todo: 탈퇴 로직 구현 */ }
                )

                // 수정 다이얼로그 - 항상 렌더링되도록 수정
                if (showEditDialog) {
                    GroupEditDialog(
                        groupDetail = currentState.groupDetail,
                        onDismiss = {
                            showEditDialog = false
                        },
                        onSave = { editData ->
                            Log.d("GroupDetailScreen", "모임 수정 데이터: $editData")
                            viewModel.updateGroup(groupId, editData)
                        }
                    )
                }
            }

            is GroupDetailUiState.Error -> {
                Log.d("GroupDetailScreen", "Error: ${currentState.message}")
            }
        }
    }
}

@Composable
private fun GroupDetailContent(
    groupDetail: GroupDetailResponse,
    isMyGroup: Boolean,
    isJoining: Boolean,
    navController: NavHostController,
    groupId: Long,
    onJoinClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onLeaveClick: () -> Unit
) {
    val dummyMembers = listOf(
        "허지명" to 80,
        "송진우" to 70,
        "송하윤" to 40,
        "홍은솔" to 60
    )

    val listState = rememberLazyListState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ScreenSize.width * 0.08f),
        state = listState
    ) {
        item {
            Spacer(modifier = Modifier.height(ScreenSize.height * 0.02f))

            Text(
                "모임 정보",
                fontWeight = FontWeight.Bold,
                fontSize = ScreenSize.width.value.times(0.045f).sp
            )

            Spacer(modifier = Modifier.height(ScreenSize.height * 0.015f))
            InfoRow("카테고리", when(groupDetail.category) {
                "STUDY" -> "학습"
                "READING" -> "독서"
                "REVIEW" -> "첨삭"
                else -> groupDetail.category
            })
            InfoRow("시작 시간", "매주 ${groupDetail.schedule}")
            InfoRow("참여 인원", "${groupDetail.memberCount}/${groupDetail.maxMemberCount}명")
            InfoRow("모임 설명", groupDetail.description)
            InfoRow("최소 평점", "4점")
            Spacer(modifier = Modifier.height(ScreenSize.height * 0.01f))
            // 구분선 추가
            Divider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = Color(0xFFE0E0E0)
            )

            Spacer(modifier = Modifier.height(ScreenSize.height * 0.03f))

            Text(
                "문서 보기",
                fontWeight = FontWeight.Bold,
                fontSize = ScreenSize.width.value.times(0.045f).sp
            )

            Spacer(modifier = Modifier.height(ScreenSize.height * 0.01f))
            Row(
                modifier = Modifier.clickable {
                    navController.currentBackStackEntry?.savedStateHandle?.set("groupId", groupId)
                    navController.navigate(Screen.PdfReadScreen.route)
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "PDF",
                        color = Color.Gray,
                        fontSize = ScreenSize.width.value.times(0.035f).sp
                    )
                    Text("스크립트 문서", fontWeight = FontWeight.Medium)
                    Text(
                        "12 페이지",
                        fontSize = ScreenSize.width.value.times(0.03f).sp,
                        color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.weight(1f))

                // photoUrl이 있을 경우 처리 (현재는 기본 이미지 사용)
                Image(
                    painter = painterResource(id = R.drawable.ic_pdf),
                    contentDescription = null,
                    modifier = Modifier.size(ScreenSize.width * 0.15f)
                )
            }
            Spacer(modifier = Modifier.height(ScreenSize.height * 0.01f))
            // 구분선 추가
            Divider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = Color(0xFFE0E0E0)
            )

            Spacer(modifier = Modifier.height(ScreenSize.height * 0.03f))

            Text(
                "참여자 목록",
                fontWeight = FontWeight.Bold,
                fontSize = ScreenSize.width.value.times(0.045f).sp
            )

            Spacer(modifier = Modifier.height(ScreenSize.height * 0.01f))
            Row(horizontalArrangement = Arrangement.spacedBy(ScreenSize.width * 0.02f)) {
                repeat(groupDetail.memberCount) {
                    Image(
                        painter = painterResource(id = R.drawable.profile_image),
                        contentDescription = null,
                        modifier = Modifier
                            .size(ScreenSize.width * 0.12f)
                            .clip(CircleShape)
                    )
                }
            }

            if(isMyGroup){
                Spacer(modifier = Modifier.height(ScreenSize.height * 0.02f))
                // 구분선 추가
                Divider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = Color(0xFFE0E0E0)
                )

                Spacer(modifier = Modifier.height(ScreenSize.height * 0.03f))
            }
            else{
                Spacer(modifier = Modifier.height(ScreenSize.height * 0.04f))
            }


            if (!isMyGroup) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ScreenSize.height * 0.065f)
                        .clip(RoundedCornerShape(ScreenSize.width * 0.03f))
                        .background(Color(0xFFDED0BB))
                        .clickable(enabled = !isJoining) {
                            onJoinClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "참여하기",
                        color = Color.White,
                        fontSize = ScreenSize.width.value.times(0.04f).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    "진도 현황",
                    fontWeight = FontWeight.Bold,
                    fontSize = ScreenSize.width.value.times(0.045f).sp
                )
                ProgressStatusCard(75, 10, dummyMembers)

                Spacer(modifier = Modifier.height(ScreenSize.height * 0.05f))

                // 모임장이면 모임 삭제, 일반 멤버면 모임 탈퇴 버튼
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ScreenSize.height * 0.065f)
                        .clip(RoundedCornerShape(ScreenSize.width * 0.03f))
                        .background(
                            Color(0xFFD32F2F)
                        )
                        .clickable {
                            if (groupDetail.isHost) {
                                onDeleteClick()
                            } else {
                                onLeaveClick()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (groupDetail.isHost) "모임 삭제" else "모임 탈퇴",
                        color = Color.White,
                        fontSize = ScreenSize.width.value.times(0.04f).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(ScreenSize.height * 0.05f))
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    val screenW = ScreenSize.width
    val screenH = ScreenSize.height

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = screenH * 0.005f),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = screenW.value.times(0.035f).sp, color = Color.Gray)
        Text(value, fontSize = screenW.value.times(0.035f).sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ProgressStatusCard(
    overallProgress: Int,
    overallDiff: Int,
    members: List<Pair<String, Int>>,
) {
    val barColor = Color(0xFFDDDDDD)
    val barWidth = ScreenSize.width * 0.15f
    val barHeight = ScreenSize.height * 0.2f
    val fontSize = ScreenSize.width.value * 0.038f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ScreenSize.width * 0.05f)
            .background(Color.White, RoundedCornerShape(ScreenSize.width * 0.03f))
    ) {
        Text(text = "문서 읽기 진도", color = Color.Gray, fontSize = fontSize.sp)
        Text(
            text = "$overallProgress%",
            fontSize = (fontSize + 8).sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "전체 ${if (overallDiff >= 0) "+" else ""}$overallDiff%",
            fontSize = fontSize.sp,
            color = if (overallDiff >= 0) Color(0xFF2E7D32) else Color.Red
        )

        Spacer(modifier = Modifier.height(ScreenSize.height * 0.02f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            members.forEach { (name, percent) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .width(barWidth)
                            .height(barHeight)
                            .background(barColor, RoundedCornerShape(4.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(percent / 100f)
                                .align(Alignment.BottomCenter)
                                .background(MainColor, RoundedCornerShape(4.dp))
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = name, fontSize = fontSize.sp, color = Color.DarkGray)
                }
            }
        }
    }
}