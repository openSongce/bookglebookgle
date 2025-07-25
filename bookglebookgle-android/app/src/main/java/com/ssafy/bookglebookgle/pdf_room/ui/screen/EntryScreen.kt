package com.ssafy.bookglebookgle.pdf_room.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.bookglebookgle.R

// Font families
private val JakartaSansBold = FontFamily(Font(R.font.jakarta_sans_bold_700, FontWeight.Bold))

@Composable
fun EntryScreen(
    onStartClicked: () -> Unit
) {
    // Auto-click start button when screen loads
    LaunchedEffect(Unit) {
        onStartClicked()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background( color = Color.Red),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Icon
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = "App Icon",
            modifier = Modifier.wrapContentSize()
        )

        // App Name
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 28.sp,
            fontFamily = JakartaSansBold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(100.dp))

        // Start Button
        Button(
            onClick = onStartClicked,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Red
            ),
            shape = RoundedCornerShape(5.dp)
        ) {
            Text(
                text = "START",
                fontSize = 23.sp,
                fontFamily = JakartaSansBold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 60.dp)
            )
        }
    }
}