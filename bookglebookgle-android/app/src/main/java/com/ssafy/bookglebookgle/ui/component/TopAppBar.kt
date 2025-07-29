package com.ssafy.bookglebookgle.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.util.ScreenSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTopAppBar(
    title: String,
    onBackPressed: (() -> Unit)? = null, // 뒤로가기 버튼 클릭 콜백
) {
    TopAppBar(
        title = {
            if(title == "main_home") {
                Text(
                    text = "북글북글",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 68.dp)
                )
            }
            else if(title == "create_group") {
                Text(
                    text = "모임 개설",
                    fontWeight = FontWeight.Bold,
                )
            }
            else if(title == "chat"){
                Text(
                    text = "채팅",
                    fontWeight = FontWeight.Bold,
                )
            }
            else if(title == "my_page") {
                Text(
                    text = "내 프로필",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 24.dp).fillMaxWidth()
                )
            }
            else if(title == "my_group"){
                Text(
                    text = "내 모임",
                    fontWeight = FontWeight.Bold,
                )
            }
            else { // 모임 상세, PDF 뷰어, AI 퀴즈 등에서 사용 될 TopAppBar Text
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        },
        navigationIcon = {
        },
        actions = {
            when (title) {
                "main_home" -> {
                    Row(
                        modifier = Modifier.padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.main_meetingplus),
                            contentDescription = "모임 추가",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(id = R.drawable.main_alarm),
                            contentDescription = "알람",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(id = R.drawable.main_search),
                            contentDescription = "검색",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                "my_page" -> {
                    Icon(
                        painter = painterResource(id = R.drawable.my_profile_setting), // 톱니바퀴 아이콘
                        contentDescription = "설정",
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(24.dp)
                            .clickable { /* TODO: 설정 클릭 동작 */ }
                    )
                }
            }
        }
        ,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.White,
        )
    )
}