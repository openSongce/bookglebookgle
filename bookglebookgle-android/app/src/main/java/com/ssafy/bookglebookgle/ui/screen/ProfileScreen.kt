package com.ssafy.bookglebookgle.ui.screen

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.navigation.Screen
import com.ssafy.bookglebookgle.ui.component.CustomTopAppBar
import com.ssafy.bookglebookgle.ui.theme.BaseColor
import com.ssafy.bookglebookgle.ui.theme.DeepMainColor
import com.ssafy.bookglebookgle.util.ScreenSize
import com.ssafy.bookglebookgle.viewmodel.ProfileUiState
import com.ssafy.bookglebookgle.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

@Composable
fun RatingStatisticItem(label: String, rating: Float, modifier: Modifier = Modifier) {
    val screenW = ScreenSize.width
    val screenH = ScreenSize.height

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ){
            Text(
                text = label,
                fontSize = screenW.value.times(0.032f).sp,
                color = Color(0xFF8D7E6E),
                fontWeight = FontWeight.Normal
            )

            Icon(
                painter = painterResource(id = R.drawable.ic_star),
                contentDescription = "Rating Icon",
                tint = Color.Unspecified, // 금색
                modifier = Modifier.size(screenW * 0.05f)
            )
        }


        Spacer(modifier = Modifier.height(screenH * 0.005f))

        Text(
            text = rating.toString(),
            fontSize = screenW.value.times(0.065f).sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavHostController, viewModel: ProfileViewModel = hiltViewModel()) {
    val profileImageSize = ScreenSize.width * 0.3f
    val iconSize = profileImageSize * 0.25f


    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        val logoutDone by viewModel.logoutCompleted.collectAsState()
        val uiState by viewModel.uiState.collectAsState()
        val saving by viewModel.saving.collectAsState()
        val nicknameError by viewModel.nicknameError.collectAsState()
        val snackbar = remember { SnackbarHostState() }
        val showEditor = remember { androidx.compose.runtime.mutableStateOf(false) }

        LaunchedEffect(Unit) {
            viewModel.loadProfile()
        }

        LaunchedEffect(Unit) {
            viewModel.events.collect { message ->
                launch {
                    try {
                        snackbar.currentSnackbarData?.dismiss() // 기존 스낵바 즉시 종료
                        snackbar.showSnackbar(
                            message = message,
                            duration = SnackbarDuration.Short // 짧은 duration 설정
                        )
                    } catch (e: Exception) {
                        Log.e("ProfileScreen", "Snackbar error: ${e.message}")
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            viewModel.saved.collect { showEditor.value = false }
        }

        if (logoutDone) {
            LaunchedEffect(Unit) {
                navController.navigate(Screen.LoginScreen.route) {
                    popUpTo(Screen.MainScreen.route) { inclusive = true }
                }
            }
        }

        CustomTopAppBar(title = "my_page", navController)

        Spacer(modifier = Modifier.height(ScreenSize.height * 0.03f))

        // 프로필 이미지 + 연필 아이콘
        when (val state = uiState) {
            is ProfileUiState.Loading -> {
                CircularProgressIndicator()
            }

            is ProfileUiState.Error -> {
                Text(text = state.message, color = Color.Red)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { viewModel.loadProfile() }) { Text("다시 시도") }
            }

            is ProfileUiState.Success -> {
                val data = state.data
                val profileImageSize = ScreenSize.width * 0.3f
                val iconSize = profileImageSize * 0.25f

                // 프로필 이미지/컬러 뱃지
                Box(
                    modifier = Modifier.size(profileImageSize),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    val avatarSize = ScreenSize.width * 0.3f

                    MemberAvatar(
                        nickname = data.nickname,
                        colorHex = data.profileColor,
                        size = avatarSize
                    )


                    // 우측 하단 편집 아이콘
                    Box(
                        modifier = Modifier
                            .size(iconSize)
                            .clip(CircleShape)
                            .border(1.dp, Color(0x11000000), CircleShape)
                            .background(Color.White)
                            .clickable { showEditor.value = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_edit),
                            contentDescription = "수정",
                            tint = Color.Black,
                            modifier = Modifier.size(iconSize * 0.6f) // fillMaxSize 대신 적절한 크기로
                        )
                    }
                }

                Spacer(modifier = Modifier.height(ScreenSize.height * 0.015f))

                Text(
                    data.nickname,
                    fontSize = ScreenSize.width.value.times(0.05f).sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    data.email,
                    fontSize = ScreenSize.width.value.times(0.035f).sp,
                    color = Color(0xFF8D7E6E)
                )

                Spacer(modifier = Modifier.height(ScreenSize.height * 0.03f))

                // 구분선
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .padding(horizontal = ScreenSize.width * 0.08f)
                        .background(Color(0xFFE5E5E5))
                )

                Spacer(modifier = Modifier.height(ScreenSize.height * 0.03f))

                // 버튼들
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenSize.width * 0.08f),
                    horizontalArrangement = Arrangement.spacedBy(ScreenSize.width * 0.04f)
                ) {
                    ProfileItemHorizontal("내 책장", Modifier.weight(1f)) { /* TODO */ }
                    ProfileItemHorizontal("로그아웃", Modifier.weight(1f)) {
                        viewModel.logout()
                    }
                }

                Spacer(modifier = Modifier.height(ScreenSize.height * 0.04f))

                // 내 통계
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenSize.width * 0.08f)
                ) {
                    Text(
                        "내 통계",
                        fontSize = ScreenSize.width.value.times(0.060f).sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(ScreenSize.height * 0.02f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ScreenSize.width * 0.08f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 서버 값 적용
                        CircularProgressChart(
                            totalMeetings = data.participatedGroups,
                            completedMeetings = data.completedGroups,
                            modifier = Modifier.weight(1.5f)
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(ScreenSize.height * 0.025f)
                        ) {
                            RatingStatisticItem(
                                label = "내 평점",
                                rating = data.avgRating
                            )
                            SimpleStatisticItem(
                                label = "총 활동 시간",
                                value = "${data.prettyActiveTime}"
                            )
                        }
                    }
                }

                if (showEditor.value) {
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    ModalBottomSheet(
                        onDismissRequest = { showEditor.value = false },
                        sheetState = sheetState
                    ) {
                        EditProfileSheetContent(
                            currentNickname = data.nickname,
                            currentColorHex = data.profileColor,
                            saving = saving,
                            nicknameError = nicknameError,
                            onCancel = { showEditor.value = false },
                            onSave = { newNick, newHex ->
                                viewModel.updateProfile(newNick, newHex)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CircularProgressChart(
    totalMeetings: Int,
    completedMeetings: Int,
    modifier: Modifier = Modifier
) {
    val completionRate = if (totalMeetings > 0) completedMeetings.toFloat() / totalMeetings else 0f
    val screenW = ScreenSize.width

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(screenW * 0.35f)
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val strokeWidth = 12.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)

                // 배경 원
                drawCircle(
                    color = Color(0xFFE5E5E5),
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // 진행률 원호
                if (completionRate > 0f) {
                    drawArc(
                        color = Color(0xFF5B7FFF),
                        startAngle = -90f,
                        sweepAngle = 360f * completionRate,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        topLeft = androidx.compose.ui.geometry.Offset(
                            center.x - radius,
                            center.y - radius
                        ),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                    )
                }
            }

            // 중앙 텍스트
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${(completionRate * 100).toInt()}%",
                    fontSize = screenW.value.times(0.045f).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = "완료율",
                    fontSize = screenW.value.times(0.025f).sp,
                    color = Color(0xFF8D7E6E)
                )
            }
        }

        Spacer(modifier = Modifier.height(ScreenSize.height * 0.01f))

        // 범례
        Row(
            horizontalArrangement = Arrangement.spacedBy(ScreenSize.width * 0.03f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(
                color = Color(0xFF5B7FFF),
                label = "완료",
                value = completedMeetings.toString()
            )
            LegendItem(
                color = Color(0xFFE5E5E5),
                label = "미완료",
                value = (totalMeetings - completedMeetings).toString()
            )
        }
    }
}

@Composable
fun LegendItem(
    color: Color,
    label: String,
    value: String
) {
    val screenW = ScreenSize.width

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            text = "$label $value",
            fontSize = screenW.value.times(0.025f).sp,
            color = Color(0xFF8D7E6E)
        )
    }
}

@Composable
fun ProfileItemHorizontal(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val screenW = ScreenSize.width
    val screenH = ScreenSize.height

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(screenW * 0.05f))
            .background(Color(0xFFF5F2F1))
            .clickable { onClick() }
            .padding(vertical = screenH * 0.018f),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = screenW.value.times(0.04f).sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black
        )
    }
}

@Composable
fun SimpleStatisticItem(label: String, value: String, modifier: Modifier = Modifier) {
    val screenW = ScreenSize.width
    val screenH = ScreenSize.height

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            fontSize = screenW.value.times(0.032f).sp,
            color = Color(0xFF8D7E6E),
            fontWeight = FontWeight.Normal
        )

        Spacer(modifier = Modifier.height(screenH * 0.005f))

        Text(
            text = value,
            fontSize = screenW.value.times(0.065f).sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}

@Composable
private fun MemberAvatar(
    nickname: String,
    colorHex: String?,
    size: Dp = ScreenSize.width * 0.12f
) {
    val bg = remember(colorHex) { hexToColorOrDefault(colorHex) }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        val initial = nickname.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        Text(
            text = initial,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.45f).sp
        )
    }
}

@Composable
private fun EditProfileSheetContent(
    currentNickname: String,
    currentColorHex: String?,
    saving: Boolean,
    nicknameError: String?,
    onCancel: () -> Unit,
    onSave: (String, String?) -> Unit
) {
    var nickname by remember { mutableStateOf(currentNickname) }
    var colorInput by remember { mutableStateOf(currentColorHex ?: "") }

    val previewColor = hexToColorOrDefault(colorInput)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row{
            Text("프로필 수정", fontSize = 18.sp, color = DeepMainColor)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_edit),
                contentDescription = "Edit Icon",
                tint = DeepMainColor,
                modifier = Modifier.size(20.dp)
            )
        }


        // 미리보기
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(previewColor),
                contentAlignment = Alignment.Center
            ) {
                val initial = nickname.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                Text(initial, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Text(nickname, fontWeight = FontWeight.SemiBold)
        }

        // 닉네임
        CompositionLocalProvider(
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = BaseColor,
                backgroundColor = BaseColor.copy(alpha = 0.3f)
            )
        ) {
            OutlinedTextField(
                value = nickname,
                onValueChange = { if (!saving) nickname = it },
                label = { Text("닉네임") },
                singleLine = true,
                enabled = !saving,
                isError = nicknameError != null,
                supportingText = {
                    if (nicknameError != null) {
                        Text(nicknameError, color = MaterialTheme.colorScheme.error)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BaseColor,
                    focusedLabelColor = BaseColor,
                    cursorColor = BaseColor
                )
            )
        }

        // 컬러 HEX
        CompositionLocalProvider(
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = BaseColor,
                backgroundColor = BaseColor.copy(alpha = 0.3f)
            )
        ) {
            OutlinedTextField(
                value = colorInput,
                onValueChange = { if (!saving) colorInput = it },
                label = { Text("프로필 색상 (예: #5B7FFF)") },
                singleLine = true,
                enabled = !saving,
                supportingText = {
                    val valid = isValidHex(colorInput)
                    Text(
                        if (valid) "유효한 색상입니다" else "형식: #RRGGBB 또는 RRGGBB",
                        color = if (valid) Color(0xFF4CAF50) else Color(0xFFD32F2F)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BaseColor,
                    focusedLabelColor = BaseColor,
                    cursorColor = BaseColor
                )
            )
        }

        // 팔레트(선택 사항)
        val palette = listOf("#5B7FFF", "#FF6B6B", "#7DDA58", "#FFB74D", "#9C27B0", "#00897B")
        FlowRowMainAxisWrap {
            palette.forEach { hex ->
                ColorChip(
                    hex = hex,
                    enabled = !saving, // 저장 중일 때 비활성화
                    onClick = {
                        if (!saving) colorInput = hex
                    }
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                enabled = !saving,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = BaseColor,
                    disabledContentColor = BaseColor.copy(alpha = 0.6f)
                ),
                border = BorderStroke(1.dp, BaseColor)
            ) {
                Text("취소")
            }

            Button(
                onClick = {
                    if (!saving) { // 추가 안전장치
                        val normalized = normalizeHexOrNull(colorInput)
                        onSave(nickname.trim(), normalized)
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !saving && nickname.isNotBlank(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BaseColor,
                    contentColor = Color.White,
                    disabledContainerColor = BaseColor.copy(alpha = 0.6f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                )
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                        color = Color.White
                    )
                } else {
                    Text("저장")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ColorChip(hex: String, enabled: Boolean = true, onClick: () -> Unit) {
    val color = hexToColorOrDefault(hex)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (enabled) color else color.copy(alpha = 0.5f))
            .border(1.dp, Color(0x11000000), CircleShape)
            .clickable(enabled = enabled) { onClick() }
    )
}

@Composable
private fun FlowRowMainAxisWrap(content: @Composable () -> Unit) {
    // 간단 대체: 가로 스페이싱만 필요한 경우 Row로 감싸도 OK.
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

private fun isValidHex(input: String): Boolean {
    val t = input.trim().uppercase()
    val s = if (t.startsWith("#")) t.drop(1) else t
    return s.length == 6 && s.all { it in "0123456789ABCDEF" }
}

private fun normalizeHexOrNull(input: String?): String? {
    if (input.isNullOrBlank()) return null
    val t = input.trim().uppercase()
    val s = if (t.startsWith("#")) t else "#$t"
    return if (isValidHex(s)) s else null
}

private fun hexToColorOrDefault(input: String?): Color =
    runCatching {
        val s = normalizeHexOrNull(input) ?: "#E0E0E0"
        Color(android.graphics.Color.parseColor(s))
    }.getOrDefault(Color(0xFFE0E0E0))
