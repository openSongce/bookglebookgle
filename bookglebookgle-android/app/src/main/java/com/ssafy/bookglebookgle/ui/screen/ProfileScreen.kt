package com.ssafy.bookglebookgle.ui.screen

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.navigation.NavKeys
import com.ssafy.bookglebookgle.navigation.Screen
import com.ssafy.bookglebookgle.ui.component.CustomTopAppBar
import com.ssafy.bookglebookgle.ui.theme.BaseColor
import com.ssafy.bookglebookgle.ui.theme.DeepMainColor
import com.ssafy.bookglebookgle.ui.theme.ResponsiveDimensions
import com.ssafy.bookglebookgle.ui.theme.defaultCornerRadius
import com.ssafy.bookglebookgle.ui.theme.defaultPadding
import com.ssafy.bookglebookgle.ui.theme.rememberResponsiveDimensions
import com.ssafy.bookglebookgle.viewmodel.ProfileUiState
import com.ssafy.bookglebookgle.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch
import java.util.Locale

// 키 문자열 ↔ 로컬 드로어블 매핑
private val AVATAR_RES_MAP = mapOf(
    "whitebear" to R.drawable.whitebear_no_bg,
    "penguin"   to R.drawable.penguin_no_bg,
    "squirrel"  to R.drawable.squirrel_no_bg,
    "rabbit"    to R.drawable.rabbit_no_bg,
    "dog"       to R.drawable.dog_no_bg,
    "cat"       to R.drawable.cat_no_bg
)

private fun keyToResId(key: String?): Int? = key?.let { AVATAR_RES_MAP[it] }

/** ---- 통계 타이틀 + 값 ---- */
@Composable
fun RatingStatisticItem(
    label: String,
    rating: Float,
    dimensions: ResponsiveDimensions,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                fontSize = dimensions.textSizeBody,
                color = Color(0xFF8D7E6E),
                fontWeight = FontWeight.Normal
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_star),
                contentDescription = "Rating Icon",
                tint = Color.Unspecified,
                modifier = Modifier.size(
                    if (dimensions.isTablet) dimensions.iconSizeLarge else dimensions.iconSizeMedium
                )
            )
        }

        Spacer(modifier = Modifier.height(dimensions.spacingTiny))

        Text(
            text = String.format(Locale.getDefault(), "%.2f", rating),
            fontSize = if (dimensions.isTablet) 26.sp else 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavHostController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val dimensions = rememberResponsiveDimensions()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val logoutDone by viewModel.logoutCompleted.collectAsState()
        val uiState by viewModel.uiState.collectAsState()
        val saving by viewModel.saving.collectAsState()
        val nicknameError by viewModel.nicknameError.collectAsState()
        val snackbar = remember { SnackbarHostState() }
        val showEditor = remember { mutableStateOf(false) }

        LaunchedEffect(Unit) { viewModel.loadProfile() }

        LaunchedEffect(Unit) {
            viewModel.events.collect { message ->
                launch {
                    try {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar(message = message, duration = SnackbarDuration.Short)
                    } catch (e: Exception) {
                        Log.e("ProfileScreen", "Snackbar error: ${e.message}")
                    }
                }
            }
        }

        LaunchedEffect(Unit) { viewModel.saved.collect { showEditor.value = false } }

        if (logoutDone) {
            LaunchedEffect(Unit) {
                navController.navigate(Screen.LoginScreen.route) {
                    popUpTo(Screen.MainScreen.route) { inclusive = true }
                }
            }
        }

        CustomTopAppBar(title = "my_page", navController)

        // 콘텐츠 래퍼: 폭 제한 + 좌우 패딩 (ChatListScreen 규칙과 동일)
        Column(
            modifier = Modifier
                .widthIn(
                    max = if (dimensions.isTablet) dimensions.contentMaxWidth * 1.5f
                    else Dp.Infinity
                )
                .fillMaxWidth()
                .padding(horizontal = dimensions.defaultPadding)
        ) {
            Spacer(modifier = Modifier.height(dimensions.spacingLarge))

            when (val state = uiState) {
                is ProfileUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }

                is ProfileUiState.Error -> {
                    Text(text = state.message, color = Color.Red)
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    Button(onClick = { viewModel.loadProfile() }) { Text("다시 시도") }
                }

                is ProfileUiState.Success -> {
                    val data = state.data

                    val avatarMainSize =
                        if (dimensions.isTablet) dimensions.avatarSizeLarge * 1.6f
                        else dimensions.avatarSizeLarge * 1.2f
                    val editIconSize =
                        if (dimensions.isTablet) dimensions.iconSizeExtraLarge
                        else dimensions.iconSizeLarge

                    // 프로필 이미지/컬러 뱃지 + 편집 버튼
                    Box(
                        modifier = Modifier
                            .size(avatarMainSize)
                            .align(Alignment.CenterHorizontally),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        MemberAvatar(
                            nickname = data.nickname,
                            colorHex = data.profileColor,
                            profileImgKey = data.profileImgUrl,
                            size = avatarMainSize
                        )

                        Box(
                            modifier = Modifier
                                .size(editIconSize)
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
                                modifier = Modifier.size(editIconSize * 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(dimensions.spacingSmall))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            data.nickname,
                            fontSize = dimensions.textSizeHeadline,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            data.email,
                            fontSize = dimensions.textSizeBody,
                            color = Color(0xFF8D7E6E)
                        )
                    }

                    Spacer(modifier = Modifier.height(dimensions.spacingLarge))

                    // 구분선
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFE5E5E5))
                    )

                    Spacer(modifier = Modifier.height(dimensions.spacingLarge))

                    // 내 통계
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "내 통계",
                            fontSize = dimensions.textSizeHeadline,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(60.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingLarge),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val chartSize = if (dimensions.isTablet) 220.dp else 140.dp

                            CircularProgressChart(
                                totalMeetings = data.participatedGroups,
                                completedMeetings = data.completedGroups,
                                size = chartSize,
                                dimensions = dimensions,
                                modifier = Modifier.weight(1.5f)
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
                            ) {
                                RatingStatisticItem(
                                    label = "내 평점",
                                    rating = data.avgRating,
                                    dimensions = dimensions
                                )
                                SimpleStatisticItem(
                                    label = "총 활동 시간",
                                    value = "${data.prettyActiveTime}",
                                    dimensions = dimensions
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(150.dp))

                    // 버튼들
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
                    ) {
                        ProfileItemHorizontal(
                            label = "회원탈퇴",
                            dimensions = dimensions,
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.deleteAccount()
                            navController.navigate(Screen.LoginScreen.route)

                        }

                        ProfileItemHorizontal(
                            label = "로그아웃",
                            dimensions = dimensions,
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.logout()
                        }
                    }

                    if (showEditor.value) {
                        val sheetState =
                            rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        ModalBottomSheet(
                            onDismissRequest = { showEditor.value = false },
                            sheetState = sheetState
                        ) {
                            EditProfileSheetContent(
                                currentNickname = data.nickname,
                                currentColorHex = data.profileColor,
                                currentImageKey = data.profileImgUrl,
                                saving = saving,
                                nicknameError = nicknameError,
                                onCancel = { showEditor.value = false },
                                onSave = { newNick, newHex, imageKey ->
                                    viewModel.updateProfile(newNick, newHex, imageKey)
                                },
                                dimensions = dimensions
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(dimensions.spacingLarge))
        }
    }
}

/** ---- 원형 진행 차트 ---- */
@Composable
fun CircularProgressChart(
    totalMeetings: Int,
    completedMeetings: Int,
    size: Dp,
    dimensions: ResponsiveDimensions,
    modifier: Modifier = Modifier
) {
    val completionRate =
        if (totalMeetings > 0) completedMeetings.toFloat() / totalMeetings else 0f

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = if (dimensions.isTablet) 14.dp.toPx() else 12.dp.toPx()
                val radius = (size.toPx() - strokeWidth) / 2
                val center =
                    androidx.compose.ui.geometry.Offset(this.size.width / 2, this.size.height / 2)

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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(completionRate * 100).toInt()}%",
                    fontSize = if (dimensions.isTablet) 18.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = "완료율",
                    fontSize = dimensions.textSizeCaption,
                    color = Color(0xFF8D7E6E)
                )
            }
        }

        Spacer(modifier = Modifier.height(dimensions.spacingSmall))

        // 범례
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(
                color = Color(0xFF5B7FFF),
                label = "완료",
                value = completedMeetings.toString(),
                dimensions = dimensions
            )
            LegendItem(
                color = Color(0xFFE5E5E5),
                label = "미완료",
                value = (totalMeetings - completedMeetings).toString(),
                dimensions = dimensions
            )
        }
    }
}

/** ---- 범례 한 항목 ---- */
@Composable
fun LegendItem(
    color: Color,
    label: String,
    value: String,
    dimensions: ResponsiveDimensions
) {
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
            fontSize = dimensions.textSizeCaption,
            color = Color(0xFF8D7E6E)
        )
    }
}

/** ---- 가로 버튼 카드 ---- */
@Composable
fun ProfileItemHorizontal(
    label: String,
    dimensions: ResponsiveDimensions,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(dimensions.defaultCornerRadius))
            .background(Color(0xFFF5F2F1))
            .clickable { onClick() }
            .padding(vertical = if (dimensions.isTablet) 14.dp else 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = if (dimensions.isTablet) dimensions.textSizeSubtitle else dimensions.textSizeBody,
            fontWeight = FontWeight.Medium,
            color = if(label == "회원탈퇴") Color.Red else Color.Black
        )
    }
}

/** ---- 통계 단순 항목 ---- */
@Composable
fun SimpleStatisticItem(
    label: String,
    value: String,
    dimensions: ResponsiveDimensions,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            fontSize = dimensions.textSizeBody,
            color = Color(0xFF8D7E6E),
            fontWeight = FontWeight.Normal
        )

        Spacer(modifier = Modifier.height(dimensions.spacingTiny))

        Text(
            text = value,
            fontSize = if (dimensions.isTablet) 26.sp else 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}

/** ---- 아바타 원형 ---- */
@Composable
private fun MemberAvatar(
    nickname: String,
    colorHex: String?,
    profileImgKey: String? = null,
    size: Dp
) {
    val bg = remember(colorHex) { hexToColorOrDefault(colorHex) }
    val resId = remember(profileImgKey) { keyToResId(profileImgKey) }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        if (resId != null) {
            Icon(
                painter = painterResource(id = resId),
                contentDescription = "avatar",
                tint = Color.Unspecified,
                modifier = Modifier.size(size * 0.8f)
            )
        } else {
            val initial = nickname.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            Text(
                text = initial,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.45f).sp
            )
        }
    }
}

/** ---- 편집 시트 ---- */
@Composable
private fun EditProfileSheetContent(
    currentNickname: String,
    currentColorHex: String?,
    saving: Boolean,
    nicknameError: String?,
    currentImageKey: String?,
    onCancel: () -> Unit,
    onSave: (String, String?, String?) -> Unit,
    dimensions: ResponsiveDimensions
) {
    var nickname by remember { mutableStateOf(currentNickname) }
    var colorInput by remember { mutableStateOf(currentColorHex ?: "") }
    var selectedImageKey by remember { mutableStateOf(currentImageKey) }

    val avatarPreviewSize =
        if (dimensions.isTablet) dimensions.avatarSizeLarge else dimensions.avatarSizeMedium
    val chipSize = if (dimensions.isTablet) 72.dp else 56.dp
    val chipGap = dimensions.spacingSmall
    val borderW = 1.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = dimensions.defaultPadding,
                end = dimensions.defaultPadding,
                bottom = dimensions.spacingLarge
            ),
        verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("프로필 수정", fontSize = dimensions.textSizeSubtitle, color = DeepMainColor)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_edit),
                contentDescription = "Edit Icon",
                tint = DeepMainColor,
                modifier = Modifier.size(
                    if (dimensions.isTablet) dimensions.iconSizeLarge else dimensions.iconSizeMedium
                )
            )
        }

        // 미리보기
        Row(verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(
                nickname = nickname,
                colorHex = colorInput,
                profileImgKey = selectedImageKey,
                size = avatarPreviewSize
            )
            Spacer(Modifier.width(dimensions.spacingSmall))
            Text(nickname, fontWeight = FontWeight.SemiBold, fontSize = dimensions.textSizeSubtitle)
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
                shape = RoundedCornerShape(dimensions.defaultCornerRadius),
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
                shape = RoundedCornerShape(dimensions.defaultCornerRadius),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BaseColor,
                    focusedLabelColor = BaseColor,
                    cursorColor = BaseColor
                )
            )
        }

        // 팔레트(선택)
        val palette = listOf("#5B7FFF", "#FF6B6B", "#7DDA58", "#FFB74D", "#9C27B0", "#00897B")
        FlowRowMainAxisWrap {
            palette.forEach { hex ->
                ColorChip(
                    hex = hex,
                    enabled = !saving,
                    onClick = { if (!saving) colorInput = hex }
                )
            }
        }

        Text("프로필 이미지 (선택)", fontWeight = FontWeight.SemiBold)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(chipGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 없음(이니셜)
            AvatarChoiceNone(
                nickname = nickname,
                colorHex = colorInput,
                selected = selectedImageKey == null,
                size = chipSize,
                borderW = borderW
            ) { if (!saving) selectedImageKey = null }

            // 6개 이미지
            AVATAR_RES_MAP.forEach { (key, resId) ->
                AvatarChoiceImage(
                    resId = resId,
                    selected = selectedImageKey == key,
                    size = chipSize,
                    borderW = borderW
                ) { if (!saving) selectedImageKey = key }
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
                shape = RoundedCornerShape(dimensions.defaultCornerRadius),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = BaseColor,
                    disabledContentColor = BaseColor.copy(alpha = 0.6f)
                ),
                border = BorderStroke(1.dp, BaseColor)
            ) { Text("취소") }

            Button(
                onClick = {
                    if (!saving) {
                        val normalized = normalizeHexOrNull(colorInput)
                        onSave(nickname.trim(), normalized, selectedImageKey)
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !saving && nickname.isNotBlank(),
                shape = RoundedCornerShape(dimensions.defaultCornerRadius),
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
        Spacer(Modifier.height(dimensions.spacingSmall))
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

@Composable
private fun AvatarChoiceNone(
    nickname: String,
    colorHex: String?,
    selected: Boolean,
    size: Dp,
    borderW: Dp,
    onClick: () -> Unit
) {
    val borderColor = if (selected) BaseColor else Color(0x11000000)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(hexToColorOrDefault(colorHex))
            .border(borderW, borderColor, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        val initial = nickname.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        Text(
            text = initial,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.4f).sp
        )
    }
}

@Composable
private fun AvatarChoiceImage(
    resId: Int,
    selected: Boolean,
    size: Dp,
    borderW: Dp,
    onClick: () -> Unit
) {
    val borderColor = if (selected) BaseColor else Color(0x11000000)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(borderW, borderColor, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = resId),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(size * 0.9f)
        )
    }
}
