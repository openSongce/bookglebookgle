package com.ssafy.bookglebookgle.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.util.ScreenSize
import com.ssafy.bookglebookgle.navigation.Screen
import org.bouncycastle.asn1.x500.style.RFC4519Style.c

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTopAppBar(
    title: String,
    navController : NavHostController,
    ismygroup : Boolean = false,
    isPdfView : Boolean = false,
    onBackPressed: (() -> Unit)? = null, // 뒤로가기 버튼 클릭 콜백
) {
    TopAppBar(
        title = {
            if(title == "main_home") {
                Text(
                    text = "북글북글",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 68.dp)
                )
            }
            else if(title == "create_group") {
                Text(
                    text = "모임 개설",
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp)
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
                    fontSize = 20.sp,
                    modifier = Modifier.padding(start = 24.dp).fillMaxWidth()
                )
            }
            else if(title == "my_group"){
                Text(
                    text = "내 모임",
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 16.dp)
                )
            }
            else if(isPdfView){
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }
            else if(ismygroup){
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 40.dp)
                )
            }
        },
        navigationIcon = {
            if(isPdfView || ismygroup) {
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
            if(title == "main_home"){
                Row(
                    modifier = Modifier
                        .padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.main_meetingplus),
                        contentDescription = "모임 추가",
                        modifier = Modifier
                            .size(20.dp)
                            .clickable{
                                navController.navigate(Screen.GroupRegisterScreen.route)
                            },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.main_alram),
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
            }else if(title == "my_page"){
                    Icon(
                        painter = painterResource(id = R.drawable.my_profile_setting),
                        contentDescription = "설정",
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(20.dp)
                            .clickable { /* TODO: 설정 클릭 동작 */ }
                    )
            }
            else if(title == "create_group") {
                Icon(
                    painter = painterResource(id = R.drawable.ic_cancel),
                    contentDescription = "취소",
                    modifier = Modifier
                        .size(36.dp)
                        .padding(end = 16.dp)
                        .clickable { onBackPressed?.invoke() }
                )
            }
            else if(isPdfView){
                Icon(
                    painter = painterResource(id = R.drawable.profile_image),
                    contentDescription = "접속인원",
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(32.dp)
                        .clip(CircleShape)

                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.White,
        )
    )
}