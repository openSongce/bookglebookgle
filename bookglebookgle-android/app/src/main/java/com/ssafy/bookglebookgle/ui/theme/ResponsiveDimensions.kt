// ui/theme/ResponsiveDimensions.kt
package com.ssafy.bookglebookgle.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 반응형 디자인을 위한 디멘션 클래스
 * 태블릿과 모바일에 따라 다른 크기값을 제공합니다.
 */
data class ResponsiveDimensions(
    val isTablet: Boolean,

    // Padding & Margin
    val paddingSmall: Dp,
    val paddingMedium: Dp,
    val paddingLarge: Dp,
    val paddingExtraLarge: Dp,

    // Button Heights
    val buttonHeightSmall: Dp,
    val buttonHeightMedium: Dp,
    val buttonHeightLarge: Dp,

    // Corner Radius
    val cornerRadiusSmall: Dp,
    val cornerRadiusMedium: Dp,
    val cornerRadiusLarge: Dp,

    // Icon Sizes
    val iconSizeSmall: Dp,
    val iconSizeMedium: Dp,
    val iconSizeLarge: Dp,
    val iconSizeExtraLarge: Dp,
    val iconSizeXLarge: Dp, // 추가

    // Logo & Avatar Sizes
    val logoSize: Dp,
    val avatarSize: Dp, // 추가
    val avatarSizeSmall: Dp,
    val avatarSizeMedium: Dp,
    val avatarSizeLarge: Dp,

    // Text Sizes
    val textSizeCaption: TextUnit,
    val textSizeBody: TextUnit,
    val textSizeSubtitle: TextUnit,
    val textSizeTitle: TextUnit,
    val textSizeHeadline: TextUnit,

    // Spacing
    val spacingTiny: Dp,
    val spacingSmall: Dp,
    val spacingMedium: Dp,
    val spacingLarge: Dp,
    val spacingExtraLarge: Dp,

    // Content Max Width (태블릿에서 콘텐츠 제한)
    val contentMaxWidth: Dp,

    // Card & Container
    val cardElevation: Dp,
    val containerPadding: Dp,

    // Input Fields
    val inputFieldHeight: Dp,
    val inputFieldMinHeight: Dp, // 추가
    val inputFieldPadding: Dp,

    // Card & Item Sizes
    val cardImageAspectRatio: Float,
    val cardInnerPadding: Dp,
    val itemImageSize: Dp,
    val dividerThickness: Dp,
    val recommendCardImageHeight: Dp, // 추천 카드 이미지 고정 높이

    // List & Grid
    val listItemSpacing: Dp,
    val recommendCardWidth: Float, // 화면 너비 대비 비율
    val recommendCardHeight: Dp, // 고정 높이로 변경

    // FAB & Chat 관련
    val fabSize: Dp, // 추가

    val dropdownWidth: Dp,
) {
    companion object {
        // 태블릿 판단 기준 (7인치 이상)
        const val TABLET_BREAKPOINT_DP = 600

        /**
         * 모바일용 디멘션
         */
        fun createMobileDimensions(): ResponsiveDimensions {
            return ResponsiveDimensions(
                isTablet = false,

                // Padding & Margin
                paddingSmall = 8.dp,
                paddingMedium = 16.dp,
                paddingLarge = 24.dp,
                paddingExtraLarge = 32.dp,

                // Button Heights
                buttonHeightSmall = 32.dp,
                buttonHeightMedium = 40.dp,
                buttonHeightLarge = 48.dp,

                // Corner Radius
                cornerRadiusSmall = 4.dp,
                cornerRadiusMedium = 8.dp,
                cornerRadiusLarge = 12.dp,

                // Icon Sizes
                iconSizeSmall = 8.dp,
                iconSizeMedium = 20.dp,
                iconSizeLarge = 24.dp,
                iconSizeExtraLarge = 32.dp,
                iconSizeXLarge = 48.dp,

                // Logo & Avatar Sizes
                logoSize = 100.dp,
                avatarSize = 40.dp,
                avatarSizeSmall = 32.dp,
                avatarSizeMedium = 48.dp,
                avatarSizeLarge = 64.dp,

                // Text Sizes
                textSizeCaption = 12.sp,
                textSizeBody = 14.sp,
                textSizeSubtitle = 16.sp,
                textSizeTitle = 18.sp,
                textSizeHeadline = 20.sp,

                // Spacing
                spacingTiny = 4.dp,
                spacingSmall = 8.dp,
                spacingMedium = 16.dp,
                spacingLarge = 24.dp,
                spacingExtraLarge = 32.dp,

                // Content Max Width
                contentMaxWidth = Dp.Infinity,

                // Card & Container
                cardElevation = 4.dp,
                containerPadding = 16.dp,

                // Input Fields
                inputFieldHeight = 64.dp,
                inputFieldMinHeight = 40.dp,
                inputFieldPadding = 16.dp,

                // Card & Item Sizes
                cardImageAspectRatio = 16f / 9f,
                cardInnerPadding = 12.dp,
                itemImageSize = 64.dp,
                dividerThickness = 0.5.dp,
                recommendCardImageHeight = 120.dp, // 모바일 추천 카드 이미지 높이

                // List & Grid
                listItemSpacing = 8.dp,
                recommendCardWidth = 0.8f,
                recommendCardHeight = 180.dp, // 고정 높이

                // FAB & Chat 관련
                fabSize = 40.dp,

                dropdownWidth = 140.dp,
            )
        }

        /**
         * 태블릿용 디멘션
         */
        fun createTabletDimensions(): ResponsiveDimensions {
            return ResponsiveDimensions(
                isTablet = true,

                // Padding & Margin
                paddingSmall = 12.dp,
                paddingMedium = 24.dp,
                paddingLarge = 32.dp,
                paddingExtraLarge = 48.dp,

                // Button Heights
                buttonHeightSmall = 40.dp,
                buttonHeightMedium = 48.dp,
                buttonHeightLarge = 56.dp,

                // Corner Radius
                cornerRadiusSmall = 6.dp,
                cornerRadiusMedium = 12.dp,
                cornerRadiusLarge = 16.dp,

                // Icon Sizes
                iconSizeSmall = 12.dp,
                iconSizeMedium = 24.dp,
                iconSizeLarge = 28.dp,
                iconSizeExtraLarge = 36.dp,
                iconSizeXLarge = 56.dp,

                // Logo & Avatar Sizes
                logoSize = 120.dp,
                avatarSize = 48.dp,
                avatarSizeSmall = 40.dp,
                avatarSizeMedium = 56.dp,
                avatarSizeLarge = 80.dp,

                // Text Sizes
                textSizeCaption = 14.sp,
                textSizeBody = 16.sp,
                textSizeSubtitle = 18.sp,
                textSizeTitle = 20.sp,
                textSizeHeadline = 24.sp,

                // Spacing
                spacingTiny = 6.dp,
                spacingSmall = 12.dp,
                spacingMedium = 20.dp,
                spacingLarge = 32.dp,
                spacingExtraLarge = 48.dp,

                // Content Max Width (태블릿에서는 제한)
                contentMaxWidth = 480.dp,

                // Card & Container
                cardElevation = 6.dp,
                containerPadding = 24.dp,

                // Input Fields
                inputFieldHeight = 72.dp,
                inputFieldMinHeight = 48.dp,
                inputFieldPadding = 20.dp,

                // Card & Item Sizes
                cardImageAspectRatio = 16f / 9f,
                cardInnerPadding = 16.dp,
                itemImageSize = 80.dp,
                dividerThickness = 1.dp,
                recommendCardImageHeight = 140.dp, // 태블릿 추천 카드 이미지 높이

                // List & Grid
                listItemSpacing = 12.dp,
                recommendCardWidth = 0.65f, // 태블릿에서는 좀 더 좁게
                recommendCardHeight = 200.dp, // 고정 높이

                // FAB & Chat 관련
                fabSize = 48.dp,

                dropdownWidth = 160.dp,
            )
        }
    }
}

/**
 * 현재 화면 크기에 따라 적절한 ResponsiveDimensions를 제공하는 Composable
 */
@Composable
fun rememberResponsiveDimensions(): ResponsiveDimensions {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    return remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        val screenWidthDp = configuration.screenWidthDp
        val screenHeightDp = configuration.screenHeightDp

        val isTablet = screenWidthDp >= ResponsiveDimensions.TABLET_BREAKPOINT_DP ||
                screenHeightDp >= ResponsiveDimensions.TABLET_BREAKPOINT_DP

        if (isTablet) {
            ResponsiveDimensions.createTabletDimensions()
        } else {
            ResponsiveDimensions.createMobileDimensions()
        }
    }
}

/**
 * 편의를 위한 확장 함수들
 */

// Button 관련
val ResponsiveDimensions.defaultButtonHeight: Dp
    get() = if (isTablet) buttonHeightMedium else buttonHeightLarge

val ResponsiveDimensions.defaultCornerRadius: Dp
    get() = cornerRadiusMedium

val ResponsiveDimensions.defaultPadding: Dp
    get() = paddingMedium

val ResponsiveDimensions.defaultSpacing: Dp
    get() = spacingMedium

val ResponsiveDimensions.defaultTextSize: TextUnit
    get() = textSizeBody

val ResponsiveDimensions.defaultIconSize: Dp
    get() = iconSizeMedium

// 특정 용도별 크기
val ResponsiveDimensions.socialButtonHeight: Dp
    get() = if (isTablet) 56.dp else 48.dp

val ResponsiveDimensions.inputFieldCornerRadius: Dp
    get() = defaultCornerRadius

val ResponsiveDimensions.socialButtonSpacing: Dp
    get() = if (isTablet) 20.dp else 16.dp

val ResponsiveDimensions.formSpacing: Dp
    get() = if (isTablet) 12.dp else 8.dp

// MainScreen 관련
val ResponsiveDimensions.screenWidthDp: Dp
    get() = if (isTablet) 600.dp else 360.dp // 기본값, 실제로는 LocalConfiguration에서 가져옴

val ResponsiveDimensions.screenHeightDp: Dp
    get() = if (isTablet) 960.dp else 640.dp // 기본값, 실제로는 LocalConfiguration에서 가져옴