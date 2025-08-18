package com.ssafy.bookglebookgle.pdf.ui

import android.os.Parcelable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.ui.theme.MainColor
import kotlinx.parcelize.Parcelize

// Font families
private val JakartaSansBold = FontFamily(Font(R.font.jakarta_sans_bold_700, FontWeight.Bold))
private val JakartaSansRegular = FontFamily(Font(R.font.jakarta_sans_regular_400, FontWeight.Normal))

@Parcelize
data class MoreOptionModel(
    val id: Int,
    val title: String,
): Parcelable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionPickBottomSheet(
    title: String,
    options: List<MoreOptionModel>,
    onOptionSelected: (MoreOptionModel) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = MainColor)
                    .padding(horizontal = 15.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    fontSize = 18.sp,
                    fontFamily = JakartaSansBold,
                    color = Color.White
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close_white),
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            // Options List
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(options) { option ->
                    OptionItem(
                        option = option,
                        onOptionClick = {
                            onOptionSelected(option)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionItem(
    option: MoreOptionModel,
    onOptionClick: () -> Unit
) {
    Text(
        text = option.title,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOptionClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        fontSize = 16.sp,
        fontFamily = JakartaSansRegular,
        color = Color.Black
    )
}