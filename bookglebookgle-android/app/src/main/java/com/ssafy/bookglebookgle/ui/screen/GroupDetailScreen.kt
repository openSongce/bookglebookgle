package com.ssafy.bookglebookgle.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.ui.component.CustomTopAppBar
import com.ssafy.bookglebookgle.util.ScreenSize



@Composable
fun GroupDetailScreen(
    navController: NavHostController,
    group: Group, // 데이터 모델
    isMyGroup: Boolean // 내 모임인지 여부
) {

    val dummyMembers = listOf(
        "이지원" to 80,
        "김지우" to 75,
        "박서연" to 73,
        "전승훈" to 70
    )

    Column(modifier = Modifier.fillMaxSize()) {
        CustomTopAppBar(title = group.title, navController = navController, ismygroup = true)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ScreenSize.width * 0.08f)
        ) {
            Spacer(modifier = Modifier.height(ScreenSize.height * 0.02f))

            // 모임 정보
            Text("모임 정보", fontWeight = FontWeight.Bold, fontSize = ScreenSize.width.value.times(0.045f).sp)

            Spacer(modifier = Modifier.height(ScreenSize.height * 0.015f))

            InfoRow("카테고리", group.category.displayName)
            InfoRow("시작 시간", "2025년 7월 20일 (토) 오후 10:00") // e.g. "2025년 7월 20일 (토) 오후 10:00"
            InfoRow("참여 인원", "${group.currentMembers}명")
            InfoRow("모임 설명", group.description)

            Spacer(modifier = Modifier.height(ScreenSize.height * 0.03f))

            // 문서 보기
            Text("문서 보기", fontWeight = FontWeight.Bold, fontSize = ScreenSize.width.value.times(0.045f).sp)
            Spacer(modifier = Modifier.height(ScreenSize.height * 0.01f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("PDF", color = Color.Gray, fontSize = ScreenSize.width.value.times(0.035f).sp)
                    Text("스크립트 문서", fontWeight = FontWeight.Medium)
                    Text("12 페이지", fontSize = ScreenSize.width.value.times(0.03f).sp, color = Color.Gray)
                }
                Spacer(modifier = Modifier.weight(1f))
                Image(
                    painter = painterResource(id = R.drawable.group_detail_pdf),
                    contentDescription = null,
                    modifier = Modifier.size(ScreenSize.width * 0.25f)
                )
            }

            Spacer(modifier = Modifier.height(ScreenSize.height * 0.03f))

            // 참여자 목록
            Text("참여자 목록", fontWeight = FontWeight.Bold, fontSize = ScreenSize.width.value.times(0.045f).sp)
            Spacer(modifier = Modifier.height(ScreenSize.height * 0.01f))
            Row(horizontalArrangement = Arrangement.spacedBy(ScreenSize.width * 0.02f)) {
                repeat(group.currentMembers) {
                    Image(
                        painter = painterResource(id = R.drawable.group_detail_profile), // 참가자 프로필
                        contentDescription = null,
                        modifier = Modifier
                            .size(ScreenSize.width * 0.12f)
                            .clip(CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 하단 버튼 또는 진도 현황
            if (!isMyGroup) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ScreenSize.height * 0.065f)
                        .clip(RoundedCornerShape(ScreenSize.width * 0.03f))
                        .background(Color(0xFFDED0BB))
                        .clickable { /* 참여 로직 */ },
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
                ProgressStatusCard(75,10,dummyMembers)
            }

            Spacer(modifier = Modifier.height(ScreenSize.height * 0.02f))
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
    members: List<Pair<String, Int>>, // 이름, 퍼센트
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
            .padding(ScreenSize.width * 0.05f)
    ) {
        Text(
            text = "진도 현황",
            fontWeight = FontWeight.Bold,
            fontSize = ScreenSize.width.value.times(0.05f).sp,
            color = Color.Black
        )
        Spacer(modifier = Modifier.height(ScreenSize.height * 0.015f))
        Text(text = "스크립트 읽기 진도", color = Color.Gray, fontSize = fontSize.sp)
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
                                .background(Color.Black, RoundedCornerShape(4.dp))
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = name, fontSize = fontSize.sp, color = Color.DarkGray)
                }
            }
        }
    }
}
