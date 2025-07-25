package com.ssafy.bookglebookgle.navigation

import androidx.annotation.DrawableRes
import com.ssafy.bookglebookgle.R

sealed class BottomNavItem(
    val label: String,
    val route: String,
    @DrawableRes val iconRes: Int,
    @DrawableRes val selectedIconRes: Int
) {
    object Home : BottomNavItem("홈", "main_home", R.drawable.main_home, R.drawable.main_selected_home)
    object Chat : BottomNavItem("채팅", "main_chat", R.drawable.main_chat, R.drawable.main_selected_chat)
    object Meeting : BottomNavItem("내 모임", "main_meeting", R.drawable.main_meeting, R.drawable.main_selected_meeting)
    object Profile : BottomNavItem("프로필", "main_profile", R.drawable.main_profile, R.drawable.main_selected_profile)

    companion object {
        val items = listOf(Home, Chat, Meeting, Profile)
    }
}
