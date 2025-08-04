package com.ssafy.bookglebookgle.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.entity.GroupDetailResponse
import com.ssafy.bookglebookgle.ui.screen.DateTimePickerDialog
import com.ssafy.bookglebookgle.ui.theme.BaseColor
import com.ssafy.bookglebookgle.ui.theme.MainColor
import kotlin.math.roundToInt

// 모임 수정 데이터 클래스
data class GroupEditData(
    val roomTitle: String,
    val category: String,
    val description: String,
    val maxMemberCount: Int,
    val schedule: String,
    val minRequiredRating: Int
)

@Composable
fun GroupEditDialog(
    groupDetail: GroupDetailResponse,
    onDismiss: () -> Unit,
    onSave: (GroupEditData) -> Unit
) {
    // 초기값 설정
    var selectedCategory by remember {
        mutableStateOf(
            when(groupDetail.category) {
                "STUDY" -> "학습"
                "READING" -> "독서"
                "REVIEW" -> "첨삭"
                else -> "독서"
            }
        )
    }
    var roomTitle by remember { mutableStateOf(groupDetail.roomTitle) }
    var groupDescription by remember { mutableStateOf(groupDetail.description) }
    var maxMembers by remember { mutableIntStateOf(groupDetail.maxMemberCount) }
    var selectedDateTime by remember { mutableStateOf(groupDetail.schedule) }
    var minRequiredRating by remember { mutableIntStateOf(4) } // 기본값
    var showDateTimePicker by remember { mutableStateOf(false) }

    val categories = listOf("독서", "학습", "첨삭")
    val unselectedColor = Color(0xFFE0E0E0)
    val backgroundColor = Color.White

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 70.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 다이얼로그 헤더
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "모임 수정",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row {
                        TextButton(
                            onClick = onDismiss
                        ) {
                            Text("취소", color = Color.Gray)
                        }

                        TextButton(
                            onClick = {
                                val editData = GroupEditData(
                                    roomTitle = roomTitle,
                                    category = when(selectedCategory) {
                                        "독서" -> "READING"
                                        "학습" -> "STUDY"
                                        "첨삭" -> "REVIEW"
                                        else -> "READING"
                                    },
                                    description = groupDescription,
                                    maxMemberCount = maxMembers,
                                    schedule = selectedDateTime,
                                    minRequiredRating = minRequiredRating
                                )
                                onSave(editData)
                            }
                        ) {
                            Text("저장", color = BaseColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Divider(color = Color(0xFFE0E0E0))

                // 스크롤 가능한 컨텐츠
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item {
                        // 모임 제목
                        Text(
                            text = "모임 제목",
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        CompositionLocalProvider(
                            LocalTextSelectionColors provides TextSelectionColors(
                                handleColor = BaseColor,
                                backgroundColor = BaseColor.copy(alpha = 0.3f)
                            )
                        ) {
                            OutlinedTextField(
                                value = roomTitle,
                                onValueChange = { roomTitle = it },
                                placeholder = { Text("모임 제목을 입력해주세요", color = Color.Gray) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BaseColor,
                                    focusedLabelColor = BaseColor,
                                    cursorColor = BaseColor
                                )
                            )
                        }
                    }

                    item {
                        // 모임 설명
                        Text(
                            text = "모임 설명",
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        CompositionLocalProvider(
                            LocalTextSelectionColors provides TextSelectionColors(
                                handleColor = BaseColor,
                                backgroundColor = BaseColor.copy(alpha = 0.3f)
                            )
                        ) {
                            OutlinedTextField(
                                value = groupDescription,
                                onValueChange = { groupDescription = it },
                                placeholder = { Text("모임에 대한 설명을 작성해주세요", color = Color.Gray) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                shape = RoundedCornerShape(8.dp),
                                maxLines = 5,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BaseColor,
                                    focusedLabelColor = BaseColor,
                                    cursorColor = BaseColor
                                )
                            )
                        }
                    }

                    item {
                        // 카테고리
                        Text(
                            text = "카테고리",
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categories.forEach { category ->
                                Button(
                                    onClick = { selectedCategory = category },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedCategory == category) Color(0xFFEFE5D8) else Color.White,
                                        contentColor = if (selectedCategory == category) Color.Black else Color.Gray
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (selectedCategory == category) Color(0xFFEFE5D8) else unselectedColor
                                    )
                                ) {
                                    Text(
                                        text = category,
                                        fontSize = 14.sp,
                                        fontWeight = if (selectedCategory == category) FontWeight.Medium else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    item {
                        // 최대 참여 인원수
                        Text(
                            text = "최대 참여 인원수",
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            (1..6).forEach { number ->
                                Button(
                                    onClick = { maxMembers = number },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (maxMembers == number) Color(0xFFEFE5D8) else Color.White,
                                        contentColor = if (maxMembers == number) Color.Black else Color.Gray
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (maxMembers == number) Color(0xFFEFE5D8) else unselectedColor
                                    ),
                                    contentPadding = PaddingValues(4.dp)
                                ) {
                                    Text(
                                        text = "${number}명",
                                        fontSize = 12.sp,
                                        fontWeight = if (maxMembers == number) FontWeight.Medium else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    item {
                        // 모임 시작 시간
                        Text(
                            text = "모임 시작 시간",
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDateTimePicker = true }
                                .border(
                                    width = 1.dp,
                                    color = if (selectedDateTime.isNotEmpty()) Color(0xFFEFE5D8) else unselectedColor,
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = backgroundColor
                            ),
                            shape = RoundedCornerShape(8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_clock),
                                    contentDescription = "시간 선택",
                                    modifier = Modifier.size(24.dp),
                                    tint = if (selectedDateTime.isNotEmpty()) Color(0xFFEFE5D8) else Color.Gray
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (selectedDateTime.isEmpty()) "날짜와 시간을 선택해주세요" else selectedDateTime,
                                    fontSize = 16.sp,
                                    color = if (selectedDateTime.isEmpty()) Color.Gray else Color.Black,
                                    fontWeight = if (selectedDateTime.isNotEmpty()) FontWeight.Medium else FontWeight.Normal
                                )
                            }
                        }
                    }

                    item {
                        // 최소 평점 요구사항
                        Text(
                            text = "최소 요구 평점",
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "💡설정한 평점 이상의 사용자만 모임에 참여할 수 있습니다",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF8FAFC)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_star),
                                            contentDescription = "평점",
                                            tint = Color(0xFFFFD700),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "최소 평점",
                                            fontSize = 14.sp,
                                            color = Color(0xFF4A5568),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Text(
                                        text = "${minRequiredRating}점 이상",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MainColor
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFFF8FAFC)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                                    ) {
                                        RatingSlider(
                                            value = minRequiredRating,
                                            onValueChange = { rating -> minRequiredRating = rating },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 날짜/시간 선택 다이얼로그
                if (showDateTimePicker) {
                    DateTimePickerDialog(
                        onDateTimeSelected = { dateTime ->
                            selectedDateTime = dateTime
                        },
                        onDismiss = { showDateTimePicker = false }
                    )
                }
            }
        }
    }
}

// Register 화면에서 가져온 RatingSlider 컴포넌트
@Composable
private fun RatingSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(value.toFloat()) }
    val density = LocalDensity.current

    val displayPosition = if (isDragging) dragPosition else value.toFloat()

    BoxWithConstraints(
        modifier = modifier.height(60.dp)
    ) {
        val sliderWidth = maxWidth - 32.dp
        val stepWidth = sliderWidth / 5f
        val containerWidthPx = with(density) { sliderWidth.toPx() }

        fun getPositionFromTouch(touchX: Float): Float {
            val ratio = (touchX / containerWidthPx).coerceIn(0f, 1f)
            return ratio * 5f
        }

        fun snapToNearestInteger(position: Float): Int {
            return position.roundToInt().coerceIn(0, 5)
        }

        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                (0..5).forEach { rating ->
                    val nearestValue = snapToNearestInteger(displayPosition)
                    Text(
                        text = rating.toString(),
                        fontSize = 12.sp,
                        fontWeight = if (nearestValue == rating) FontWeight.Bold else FontWeight.Normal,
                        color = if (nearestValue == rating) MainColor else Color(0xFF64748B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .align(Alignment.Center)
                        .background(
                            Color(0xFFE2E8F0),
                            RoundedCornerShape(3.dp)
                        )
                )

                Box(
                    modifier = Modifier
                        .width(stepWidth * displayPosition)
                        .height(6.dp)
                        .align(Alignment.CenterStart)
                        .background(
                            MainColor,
                            RoundedCornerShape(3.dp)
                        )
                )

                Box(
                    modifier = Modifier
                        .size(if (isDragging) 28.dp else 24.dp)
                        .offset(x = (stepWidth * displayPosition) - if (isDragging) 14.dp else 12.dp)
                        .align(Alignment.CenterStart)
                        .background(
                            Color.White,
                            CircleShape
                        )
                        .border(
                            if (isDragging) 3.dp else 2.dp,
                            if (isDragging) MainColor else Color(0xFFE2E8F0),
                            CircleShape
                        )
                        .clickable {
                            // 클릭으로도 값 변경 가능
                            val nearestValue = snapToNearestInteger(displayPosition)
                            if (nearestValue < 5) {
                                onValueChange(nearestValue + 1)
                            } else {
                                onValueChange(0)
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isDragging) 10.dp else 8.dp)
                            .align(Alignment.Center)
                            .background(
                                MainColor,
                                CircleShape
                            )
                    )
                }
            }
        }
    }
}