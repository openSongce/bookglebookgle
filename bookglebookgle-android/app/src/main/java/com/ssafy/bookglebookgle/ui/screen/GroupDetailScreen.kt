package com.ssafy.bookglebookgle.ui.screen

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.google.gson.Gson
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.entity.GroupDetail
import com.ssafy.bookglebookgle.entity.GroupMember
import com.ssafy.bookglebookgle.navigation.Screen
import com.ssafy.bookglebookgle.ui.component.CustomTopAppBar
import com.ssafy.bookglebookgle.ui.component.GroupEditDialog
import com.ssafy.bookglebookgle.ui.theme.MainColor
import com.ssafy.bookglebookgle.util.ScreenSize
import com.ssafy.bookglebookgle.util.UserInfoManager
import com.ssafy.bookglebookgle.viewmodel.EditGroupUiState
import com.ssafy.bookglebookgle.viewmodel.GroupDetailUiState
import com.ssafy.bookglebookgle.viewmodel.GroupDetailViewModel
import com.ssafy.bookglebookgle.viewmodel.JoinGroupUiState
import com.ssafy.bookglebookgle.viewmodel.RateMemberUiState

// 키 문자열 ↔ 로컬 드로어블
private val AVATAR_RES_MAP = mapOf(
    "whitebear" to R.drawable.whitebear_no_bg,
    "penguin"   to R.drawable.penguin_no_bg,
    "squirrel"  to R.drawable.squirrel_no_bg,
    "rabbit"    to R.drawable.rabbit_no_bg,
    "dog"       to R.drawable.dog_no_bg,
    "cat"       to R.drawable.cat_no_bg
)

private fun keyToResId(key: String?): Int? = key?.let { AVATAR_RES_MAP[it] }


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

    val hasRatedByMe by viewModel.hasRatedByMe.collectAsStateWithLifecycle()
    val myUserId by viewModel.currentUserId.collectAsStateWithLifecycle()
    val rateState by viewModel.rateMemberState.collectAsStateWithLifecycle()

    var showRateDialog by remember { mutableStateOf(false) }
    var pendingToId by remember { mutableStateOf<Long?>(null) }
    var pendingScore by remember { mutableStateOf(5f) } // 기본 5점


    val context = LocalContext.current

    // 수정 다이얼로그 상태
    var showEditDialog by remember { mutableStateOf(false) }

    if (showRateDialog) {
        val detail = (uiState as? GroupDetailUiState.Success)?.groupDetail
        RateMemberDialog(
            members = detail?.members.orEmpty(),
            myUserId = myUserId,
            onDismiss = { showRateDialog = false },
            onConfirm = { toId, score ->
                viewModel.rateMember(groupId, toId, score)
            }
        )
    }

    // 저장 결과 토스트/닫기
    LaunchedEffect(rateState) {
        when (rateState) {
            is RateMemberUiState.Success -> {
                Toast.makeText(context, "평가가 등록되었습니다.", Toast.LENGTH_SHORT).show()
                showRateDialog = false
                viewModel.resetRateMemberState()
            }
            is RateMemberUiState.Error -> {
                Toast.makeText(context, (rateState as RateMemberUiState.Error).message, Toast.LENGTH_LONG).show()
                viewModel.resetRateMemberState()
            }
            else -> Unit
        }
    }

    LaunchedEffect(groupId) {
        viewModel.setInitialMyGroupState(isMyGroup)
        viewModel.getGroupDetail(groupId)
    }

    val userInfoManager : UserInfoManager

    // 가입 성공 시 처리
    // GroupDetailScreen.kt의 LaunchedEffect(joinGroupState) 부분을 다음과 같이 수정하세요

    LaunchedEffect(joinGroupState) {
        when (joinGroupState) {
            is JoinGroupUiState.Success -> {
                Log.d("GroupDetailScreen", "그룹 가입 성공!")
                Toast.makeText(context, "그룹에 성공적으로 가입했습니다!", Toast.LENGTH_SHORT).show()
                viewModel.resetJoinGroupState()
                kotlinx.coroutines.delay(100)
                viewModel.getGroupDetail(groupId)
            }
            is JoinGroupUiState.Error -> {
                val errorMessage = (joinGroupState as JoinGroupUiState.Error).message
                Log.e("GroupDetailScreen", "그룹 가입 실패: $errorMessage")

                // 에러 메시지에 따른 토스트 메시지 분류
                val toastMessage = when {
                    // 이미 참가한 그룹
                    errorMessage.contains("이미 참가한 그룹") -> {
                        "이미 가입된 모임입니다."
                    }

                    // 그룹 정원 초과 (여러 패턴 추가)
                    errorMessage.contains("그룹 정원이 초과되었습니다") ||
                            errorMessage.contains("정원이 초과") ||
                            errorMessage.contains("정원") && errorMessage.contains("초과") -> {
                        "모임 정원이 가득 찼습니다."
                    }

                    // 평점 부족
                    errorMessage.contains("평점이 낮아") ||
                            errorMessage.contains("평점") && errorMessage.contains("참가할 수 없습니다") -> {
                        if (uiState is GroupDetailUiState.Success) {
                            val requiredRating = (uiState as GroupDetailUiState.Success).groupDetail.minRequiredRating
                            "평점이 부족해 가입할 수 없습니다.\n(최소 요구 평점: ${requiredRating}점)"
                        } else {
                            "평점이 부족해 가입할 수 없습니다."
                        }
                    }

                    // 가입 조건 불만족
                    errorMessage.contains("가입 조건을 만족하지 않습니다") -> {
                        "가입 조건을 만족하지 않습니다."
                    }

                    // 가입 요청 거부
                    errorMessage.contains("가입 요청이 거부되었습니다") -> {
                        "가입 요청이 거부되었습니다."
                    }

                    // 해당 그룹이 존재하지 않음
                    errorMessage.contains("해당 그룹이 존재하지 않습니다") ||
                            errorMessage.contains("존재하지 않") -> {
                        "존재하지 않는 모임입니다."
                    }

                    // 서버 오류
                    errorMessage.contains("서버 오류") -> {
                        "서버 오류가 발생했습니다.\n잠시 후 다시 시도해주세요."
                    }

                    // 기본 에러 메시지
                    else -> {
                        errorMessage.ifBlank { "모임 가입에 실패했습니다.\n다시 시도해주세요." }
                    }
                }

                Log.d("GroupDetailScreen", "최종 토스트 메시지: $toastMessage")
                Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()
                viewModel.resetJoinGroupState()
            }
            else -> {}
        }
    }

    LaunchedEffect(editGroupState) {
        when (editGroupState) {
            is EditGroupUiState.Success -> {
                Log.d("GroupDetailScreen", "그룹 수정 성공!")
                Toast.makeText(context, "모임 정보가 성공적으로 수정되었습니다!", Toast.LENGTH_SHORT).show()
                viewModel.resetEditGroupState()
                showEditDialog = false
                // 수정 완료 후 화면 새로고침
                viewModel.getGroupDetail(groupId)
            }
            is EditGroupUiState.Error -> {
                Log.e("GroupDetailScreen", "그룹 수정 실패: ${(editGroupState as EditGroupUiState.Error).message}")
                viewModel.resetEditGroupState()
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
                CustomTopAppBar(
                    title = title,
                    navController = navController,
                    ismygroup = currentIsMyGroup,
                    isHost = currentState.groupDetail.isHost,
                    isDetailScreen = true,
                    onEditClick = if (currentIsMyGroup) {
                        { showEditDialog = true }
                    } else null
                )
                GroupDetailContent(
                    context = context,
                    groupDetail = currentState.groupDetail,
                    isMyGroup = currentIsMyGroup,
                    isJoining = joinGroupState is JoinGroupUiState.Loading,
                    navController = navController,
                    groupId = groupId,
                    viewModel = viewModel,
                    hasRatedByMe = hasRatedByMe,                  // ★ 추가
                    onOpenRateDialog = { showRateDialog = true },
                    onJoinClick = { viewModel.joinGroup(groupId) },
                    onDeleteClick = {
                        viewModel.deleteGroup(groupId)
                        navController.popBackStack() },
                    onLeaveClick = {
                        viewModel.leaveGroup(groupId)
                        navController.popBackStack()
                    }
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
    context : Context,
    groupDetail: GroupDetail,
    isMyGroup: Boolean,
    isJoining: Boolean,
    navController: NavHostController,
    groupId: Long,
    viewModel: GroupDetailViewModel,
    hasRatedByMe: Boolean,
    onOpenRateDialog: () -> Unit,
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
            InfoRow("최소 평점", "${groupDetail.minRequiredRating}점")
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
                    if (isMyGroup) {
                        // 가입된 모임인 경우 PDF 화면으로 이동
                        // 1) 현재 상세 데이터 꺼내기
                        val success = (viewModel.uiState.value as? GroupDetailUiState.Success)
                        val detail = success?.groupDetail

                        // 2) 초기 멤버/진도 가공 (뷰모델의 헬퍼 사용)
                        val initialMembers = detail?.let { viewModel.run { it.toInitialMembers() } } ?: emptyList()
                        val initialProgress = detail?.let { viewModel.run { it.toInitialProgressMap() } } ?: emptyMap()

                        // 3) (있으면) 페이지 수 — 필드명은 프로젝트에 맞게 바꿔줘요 (예: detail.totalPageCount)
                        val pageCount = detail?.pageCount ?: 0


                        // 4) 다음 화면에서 꺼내 쓸 값들 저장
                        val gson = Gson()
                        navController.currentBackStackEntry?.savedStateHandle?.apply {
                            set("groupId", groupId)
                            set("isHost", detail?.isHost == true)
                            set("pageCount", pageCount) // 없으면 0으로 두고, PDF 렌더 후 갱신돼도 OK
                            set("initialMembersJson", gson.toJson(initialMembers))
                            set("initialProgressJson", gson.toJson(initialProgress))
                        }

                        // 5) 이동
                        navController.navigate(Screen.PdfReadScreen.route)
                    } else {
                        // 미가입 모임인 경우 토스트 메시지 표시
                        Toast.makeText(
                            context,
                            "모임에 가입해야 문서를 볼 수 있습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
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
                        "${groupDetail.pageCount} 페이지",
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
            val membersSorted = remember(groupDetail.members) {
                groupDetail.members.sortedByDescending { it.isHost } // 호스트가 앞
            }
            Row(horizontalArrangement = Arrangement.spacedBy(ScreenSize.width * 0.02f)) {
                membersSorted.forEach { m ->
                    MemberAvatar(m.userNickName, m.profileColor, m.isHost, m.photoUrl)
                }
            }

            if (isMyGroup && groupDetail.isCompleted) {
                Spacer(Modifier.height(ScreenSize.height * 0.02f))
                val enabled = hasRatedByMe == false
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ScreenSize.height * 0.065f)
                        .clip(RoundedCornerShape(ScreenSize.width * 0.03f))
                        .background(if (enabled) Color(0xFF4CAF50) else Color(0xFF9E9E9E))
                        .clickable(enabled = enabled) {
                            // 다이얼로그 열기
                            onOpenRateDialog()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (enabled) "팀원 평점 남기기" else "평가 완료됨",
                        color = Color.White,
                        fontSize = ScreenSize.width.value.times(0.04f).sp,
                        fontWeight = FontWeight.Bold
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
                ProgressStatusCard(
                    pageCount = groupDetail.pageCount,
                    members = groupDetail.members
                )


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
    pageCount: Int,
    members: List<GroupMember>,
) {
    val barColor = Color(0xFFDDDDDD)
    val barWidth = ScreenSize.width * 0.15f
    val barHeight = ScreenSize.height * 0.2f
    val fontSize = ScreenSize.width.value * 0.038f

    // 각 멤버 진도 % 계산: 서버 progressPercent 우선, 없으면 lastPageRead 기반 계산
    fun percentOf(m: GroupMember): Int {
        val server = m.progressPercent
        if (server in 0..100) return server
        if (pageCount <= 0) return 0
        val oneBased = (m.lastPageRead + 1).coerceAtLeast(0)
        return ((oneBased.toFloat() / pageCount.toFloat()) * 100f)
            .toInt().coerceIn(0, 100)
    }

    // 이름 길이 제한 함수
    fun truncateName(name: String): String {
        return if (name.length > 4) {
            name.take(3) + "..."
        } else {
            name
        }
    }

    val percents = members.map { percentOf(it) }
    val avg = if (percents.isNotEmpty()) percents.sum() / percents.size else 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(ScreenSize.width * 0.05f)
            .background(Color.White, RoundedCornerShape(ScreenSize.width * 0.03f))
    ) {
        Text(text = "문서 읽기 진도", color = Color.Gray, fontSize = fontSize.sp)
        Text(text = "$avg%", fontSize = (fontSize + 8).sp, fontWeight = FontWeight.Bold)
        Text(text = "전체 평균", fontSize = fontSize.sp, color = Color(0xFF2E7D32))

        Spacer(modifier = Modifier.height(ScreenSize.height * 0.02f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ScreenSize.width * 0.04f, Alignment.Start)
        ) {
            members.forEach { m ->
                val p = percentOf(m)

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
                                .fillMaxHeight(p / 100f)
                                .align(Alignment.BottomCenter)
                                .background(MainColor, RoundedCornerShape(4.dp))
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = truncateName(m.userNickName),
                        fontSize = fontSize.sp,
                        color = Color.DarkGray,
                        maxLines = 1
                    )
                    Text(
                        text = "$p%",
                        fontSize = (fontSize * 0.9f).sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}


fun hexToColor(hex: String?): Color {
    if (hex.isNullOrBlank()) return Color(0xFFE5E7EB)
    return try {
        val s = if (hex.startsWith("#")) hex else "#$hex"
        Color(android.graphics.Color.parseColor(s))
    } catch (e: IllegalArgumentException) {
        Color(0xFF9E9E9E) // fallback
    }
}

@Composable
private fun MemberAvatar(
    nickname: String,
    colorHex: String?,
    isHost: Boolean,
    profileImgKey: String? = null,
    size: Dp = ScreenSize.width * 0.12f
) {
    val bg = remember(colorHex) { hexToColor(colorHex) }
    val resId = remember(profileImgKey) { keyToResId(profileImgKey) }

    val borderW   = size * 0.016f                  // ≈ 2dp 비율
    val badgeSize = size * 0.36f                   // 왕관 배지 크기

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .then(
                if (isHost) Modifier.border(2.dp, Color(0xFFFFC107), CircleShape) // 금색 테두리
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (resId != null) {
            // 키가 유효하면 로컬 드로어블 표시
            androidx.compose.material3.Icon(
                painter = painterResource(id = resId),
                contentDescription = "avatar",
                tint = Color.Unspecified,
                modifier = Modifier.size(size * 0.8f)
            )
        } else {
            // 키가 없거나 매핑 실패 → 이니셜
            val initial = nickname.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            Text(
                text = initial,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.45f).sp
            )
        }

        if (isHost) {
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .align(Alignment.TopEnd)
                    .offset(x = badgeSize * 0.15f, y = -badgeSize * 0.55f), // 👉 밖으로!
                contentAlignment = Alignment.Center
            ) {
                // 배지 배경/테두리 (원하면)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape)
                        .background(Color(0xFFFFF8E1))
                        .border(borderW, Color(0xFFFFC107), CircleShape)
                )
                Text("👑", fontSize = (badgeSize.value * 0.55f).sp)
            }
        }
    }
}

@Composable
private fun RateMemberDialog(
    members: List<GroupMember>,
    myUserId: Long?,
    onDismiss: () -> Unit,
    onConfirm: (toId: Long, score: Float) -> Unit
) {
    if (myUserId == null) return

    val candidates = remember(members, myUserId) {
        members.filter { it.userId != myUserId } // 본인 제외
    }
    var selectedToId by remember { mutableStateOf<Long?>(candidates.firstOrNull()?.userId) }
    var score by remember { mutableStateOf(5f) } // 기본 5점

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("팀원 평점 남기기", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("대상 선택")
                Spacer(Modifier.height(6.dp))
                candidates.forEach { m ->
                    val selected = selectedToId == m.userId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Color(0xFFE8F5E9) else Color(0xFFF8FAFC))
                            .border(1.dp, if (selected) Color(0xFF4CAF50) else Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                            .clickable { selectedToId = m.userId }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(m.userNickName, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(6.dp))
                Text("점수: ${"%.1f".format(score)}")
                androidx.compose.material3.Slider(
                    value = score,
                    onValueChange = { score = it },
                    valueRange = 1f..5f,
                    steps = 8 // 0.5 단위로 주고 싶으면 8~; 아니면 steps=3(정수) 등
                )
            }
        },
        confirmButton = {
            val enabled = selectedToId != null
            androidx.compose.material3.TextButton(
                enabled = enabled,
                onClick = { selectedToId?.let { onConfirm(it, score) } }
            ) { Text("저장") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

