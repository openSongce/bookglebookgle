package com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.PDFView
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.Coordinates
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.TextSelectionData
import com.ssafy.bookglebookgle.pdf.utils.Utils
import com.ssafy.bookglebookgle.ui.theme.BaseColor
import com.ssafy.bookglebookgle.ui.theme.MainColor
import kotlin.math.roundToInt

// 메인 클래스 - XML 버전과 동일한 생성자 패턴
class TextSelectionOptionsWindow(
    private val context: android.content.Context,
    private val listener: Listener,
) {
    companion object {
        private const val CLOSE_ICON_ID = "CLOSE_ICON_STRING"
    }

    private lateinit var pdfView: PDFView

    // Compose 상태들
    var isShowing by mutableStateOf(false)
        private set
    var currentX by mutableStateOf(0f)
        private set
    var currentY by mutableStateOf(0f)
        private set
    var selectedTextData by mutableStateOf<TextSelectionData?>(null)
        private set
    var showColorPicker by mutableStateOf(false)
        private set

    // XML 버전과 완전히 동일한 색상 리스트 (CLOSE_ICON_ID 포함)
    private val colorsList = listOf(
        "#FFFF00", "#00FF00", "#0000FF", "#FFA500", "#FFC0CB",
        "#800080", "#00FFFF", "#FF0000", "#008080", "#E6E6FA",
    )

    private var colorOptionSize = 20

    // XML 버전과 동일한 초기화
    init {
        colorOptionSize = Utils.convertDpToPixel(context, 17f)
    }

    interface Listener {
        fun onAddHighlightClick(
            snippet: String,
            color: String,
            page: Int,
            coordinates: Coordinates,
        )

        fun onAddNotClick(snippet: String, page: Int, coordinates: Coordinates)
    }

    fun attachToPdfView(pdfView: PDFView) {
        this.pdfView = pdfView
    }

    fun show(x: Float, y: Float, selectedText: TextSelectionData) {
        if (isShowing) return

        this.selectedTextData = selectedText
        this.currentX = x
        this.currentY = y
        this.showColorPicker = false
        this.isShowing = true
    }

    fun dismiss(clearTextSelection: Boolean = false) {
        isShowing = false
        showColorPicker = false
        if (clearTextSelection && ::pdfView.isInitialized) {
            try {
                // PDF 텍스트 선택 해제 로직
                 pdfView.clearAllTextSelectionAndCoordinates()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Composable
    fun ComposeContent() {
        val density = LocalDensity.current

        if (isShowing && selectedTextData != null) {
            // XML 버전과 동일한 계산
            val optionWindowHeight = Utils.convertDpToPixel(context, 40f)

            val calculatedY = if (currentY - (optionWindowHeight * 1.5) <= 0) {
                optionWindowHeight.toFloat() * 0.5
            } else {
                currentY - (optionWindowHeight * 1.5)
            }

            Popup(
                alignment = Alignment.TopStart,
                offset = with(density) {
                    IntOffset(
                        x = (currentX - 60.dp.toPx()).toInt(),  // 팝업 너비의 절반 정도 왼쪽으로 이동
                        y = calculatedY.roundToInt()
                    )
                },
                onDismissRequest = { dismiss(false) },
                properties = PopupProperties(focusable = true)
            ) {
                Card(
                    modifier = Modifier
                        .padding(4.dp)
                        .wrapContentSize(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF2E6)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (!showColorPicker) {
                        OptionContainer()
                    } else {
                        ColorContainer()
                    }
                }
            }
        }
    }

    @Composable
    private fun OptionContainer() {
        Row(
            modifier = Modifier.padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    selectedTextData?.let { data ->
                        val selectedText = data.getSelectedText()
                        if (selectedText.isNotEmpty()) {
                            listener.onAddNotClick(
                                selectedText,
                                data.getPdfPageNumber(),
                                data.getStartEndCoordinates()
                            )
                        }
                    }
                },
            ) {
                Text(
                    text = "댓글 작성",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
            }
            // 세로 구분선 추가
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(Color.Gray)
            )

            TextButton(
                onClick = { showColorPicker = true },
                modifier = Modifier.padding(4.dp)
            ) {
                Text(
                    text = "하이라이트",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
            }
        }
    }

    @Composable
    private fun ColorContainer() {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "색상 선택",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )

                IconButton(
                    onClick = {
                        // XML 버전의 CLOSE_ICON_ID 클릭과 동일한 동작
                        showColorPicker = false
                    },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "닫기",
                        tint = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // XML 버전과 동일한 색상 처리 로직
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                items(colorsList.size) { index ->
                    val colorItem = colorsList[index]
                    val isCloseIcon = colorItem == CLOSE_ICON_ID

                    if (isCloseIcon) {
                        // XML 버전의 AppCompatImageView와 동일한 동작
                        Box(
                            modifier = Modifier
                                .size((colorOptionSize + 1).dp)
                                .clickable {
                                    // XML 버전의 CLOSE_ICON_ID 클릭과 동일
                                    showColorPicker = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                        }
                    } else {
                        // XML 버전의 색상 View와 동일한 동작
                        val color = Color(android.graphics.Color.parseColor(colorItem))

                        Box(
                            modifier = Modifier
                                .size(35.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = 1.dp,
                                    color = Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable {
                                    // XML 버전의 onColorSelected와 완전히 동일한 로직
                                    selectedTextData?.let { data ->
                                        val selectedText = data.getSelectedText()
                                        if (selectedText.isNotEmpty()) {
                                            listener.onAddHighlightClick(
                                                selectedText,
                                                colorItem, // 색상 처리도 동일
                                                data.getPdfPageNumber(),
                                                data.getStartEndCoordinates()
                                            )
                                        }
                                    }
                                    dismiss(true)
                                }
                        )
                    }
                }
            }
        }
    }
}