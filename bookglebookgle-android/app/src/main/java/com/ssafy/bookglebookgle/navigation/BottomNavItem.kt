package com.ssafy.bookglebookgle.navigation

import androidx.annotation.DrawableRes
import com.ssafy.bookglebookgle.R

sealed class BottomNavItem(
    val label: String,
    val route: String,
    @DrawableRes val iconRes: Int,
    @DrawableRes val selectedIconRes: Int
) {
    // TODO: 라우트 이름을 실제 화면 라우트와 일치시켜야 함.
    object Home : BottomNavItem("홈", Screen.MainScreen.route, R.drawable.main_home, R.drawable.main_selected_home)
    object Chat : BottomNavItem("채팅", "main_chat", R.drawable.main_chat, R.drawable.main_selected_chat)
    object MyGroup : BottomNavItem("내 모임", "main_mygroup", R.drawable.main_meeting, R.drawable.main_selected_meeting)
    object Profile : BottomNavItem("프로필", "main_profile", R.drawable.main_profile, R.drawable.main_selected_profile)

    companion object {
        val items = listOf(Home, Chat, MyGroup, Profile)
    }
}
