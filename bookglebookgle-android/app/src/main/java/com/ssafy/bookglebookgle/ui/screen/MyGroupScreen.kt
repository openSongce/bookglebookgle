package com.ssafy.bookglebookgle.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.ssafy.bookglebookgle.ui.component.CustomTopAppBar

// 데이터 클래스
data class Group(
    val id: String,
    val category: GroupCategory,
    val title: String,
    val description: String,
    val currentMembers: Int,
    val maxMembers: Int,
    val imageRes: Int? = null
)

enum class GroupCategory(val displayName: String, val backgroundColor: Color) {
    READING("독서", Color(0xFFB8C5B8)),
    STUDY("학습", Color(0xFFB8C5B8)),
    REVIEW("첨삭", Color(0xFFE8D5C4))
}

@Composable
fun MyGroupScreen(
    navController: NavHostController,
    groups: List<Group> = getSampleGroups(),
    onGroupClick: (Group) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        CustomTopAppBar(
            title = "my_group",
            navController = navController,
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            items(groups) { group ->
                GroupCard(
                    group = group,
                    onClick = { onGroupClick(group) }
                )
            }
        }
    }
}

@Composable
fun GroupCard(
    group: Group,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // 오른쪽 콘텐츠
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 카테고리와 참여 인원을 나란히 배치
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryTag(category = group.category)

                    Text(
                        text = "${group.currentMembers}명 정원",
                        fontSize = 12.sp,
                        color = Color(0xFF888888)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 제목
                Text(
                    text = group.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 설명
                Text(
                    text = group.description,
                    fontSize = 13.sp,
                    color = Color(0xFF666666),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(
                        color = Color(0xFFF0F0F0),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // 실제 이미지 대신 식물 아이콘 표시 (이미지와 유사하게)
                Text(
                    text = "🌿",
                    fontSize = 32.sp
                )
            }
        }
    }
}

@Composable
fun CategoryTag(category: GroupCategory) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = category.backgroundColor,
        modifier = Modifier.wrapContentSize()
    ) {
        Text(
            text = category.displayName,
            color = Color.Black,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// 샘플 데이터
fun getSampleGroups(): List<Group> {
    return listOf(
        Group(
            id = "1",
            category = GroupCategory.READING,
            title = "웹 프로젝트 함께 하시는 분?",
            description = "웹 프로젝트 함께 하시는 분 찾습니다.",
            currentMembers = 2,
            maxMembers = 6
        ),
        Group(
            id = "2",
            category = GroupCategory.STUDY,
            title = "웹 프로젝트 함께 하시는 분?",
            description = "웹 프로젝트 함께 하시는 분 찾습니다.",
            currentMembers = 2,
            maxMembers = 6
        ),
        Group(
            id = "3",
            category = GroupCategory.REVIEW,
            title = "웹 프로젝트 함께 하시는 분?",
            description = "웹 프로젝트 함께 하시는 분 찾습니다.",
            currentMembers = 2,
            maxMembers = 6
        )
    )
}