package com.ssafy.bookglebookgle.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ssafy.bookglebookgle.ui.theme.rememberResponsiveDimensions

@Composable
fun BottomNavigationBar(
    modifier: Modifier = Modifier,
    selectedRoute: String?,
    onItemSelected: (String) -> Unit
) {
    // 반응형 디멘션 사용
    val dimensions = rememberResponsiveDimensions()

    // 태블릿에서는 중앙 정렬과 최대 너비 제한
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White),
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .then(
                    if (dimensions.isTablet) {
                        Modifier.widthIn(max = dimensions.contentMaxWidth * 1.5f)
                    } else {
                        Modifier.fillMaxWidth()
                    }
                )
                .padding(
                    horizontal = if (dimensions.isTablet) dimensions.paddingLarge else 0.dp,
                    vertical = dimensions.spacingSmall
                ),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BottomNavItem.items.forEach { item ->
                val selected = selectedRoute == item.route
                val iconRes = if (selected) item.selectedIconRes else item.iconRes

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onItemSelected(item.route)
                        }
                        .padding(vertical = dimensions.spacingSmall),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = iconRes),
                        contentDescription = item.label,
                        modifier = Modifier.size(dimensions.iconSizeLarge)
                    )
                    Text(
                        text = item.label,
                        color = if (selected) Color.Black else Color.Gray,
                        fontSize = dimensions.textSizeCaption
                    )
                }
            }
        }
    }
}