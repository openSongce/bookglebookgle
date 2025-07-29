package com.ssafy.bookglebookgle.ui.screen

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.pdf.tools.AppFileManager
import com.ssafy.bookglebookgle.ui.component.CustomTopAppBar
import com.ssafy.bookglebookgle.util.PdfAnalysisResult
import com.ssafy.bookglebookgle.util.PdfOcrChecker
import com.ssafy.bookglebookgle.viewmodel.GroupRegisterViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

private const val TAG = "싸피_GroupRegisterScreen"

@Composable
fun DateTimePickerDialog(
    onDateTimeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val calendar = Calendar.getInstance()
    val currentMonth = calendar.get(Calendar.MONTH) + 1
    val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

    var selectedMonth by remember { mutableIntStateOf(currentMonth) }
    var selectedDay by remember { mutableIntStateOf(currentDay) }
    var selectedHour by remember { mutableIntStateOf(9) }
    var selectedMinute by remember { mutableIntStateOf(0) }

    // 현재 월부터 12월까지
    val availableMonths = (currentMonth..12).toList()

    // 선택된 월에 따른 일 계산
    val availableDays = remember(selectedMonth) {
        val startDay = if (selectedMonth == currentMonth) currentDay else 1
        val daysInMonth = when (selectedMonth) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> 28 // 2025년은 평년
            else -> 31
        }
        (startDay..daysInMonth).toList()
    }

    // 선택된 일이 범위를 벗어나면 조정
    LaunchedEffect(availableDays) {
        if (selectedDay !in availableDays) {
            selectedDay = availableDays.first()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            LazyColumn(
                modifier = Modifier.padding(24.dp)
            ) {
                item {
                    // 헤더
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "모임 시작 시간",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2D3748)
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.ic_clock),
                            contentDescription = "시계",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 날짜 선택 카드
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF8FAFC)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_calendar),
                                    contentDescription = "날짜",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "날짜 선택",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF4A5568)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 월 선택
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "월",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(availableMonths) { month ->
                                            Card(
                                                modifier = Modifier
                                                    .size(width = 50.dp, height = 40.dp)
                                                    .clickable { selectedMonth = month },
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (selectedMonth == month)
                                                        Color(0xFF81C4E8) else Color.White
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                border = if (selectedMonth != month)
                                                    androidx.compose.foundation.BorderStroke(
                                                        1.dp,
                                                        Color(0xFFE2E8F0)
                                                    )
                                                else null
                                            ) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "${month}월",
                                                        fontSize = 12.sp,
                                                        fontWeight = if (selectedMonth == month) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (selectedMonth == month) Color.White else Color(
                                                            0xFF4A5568
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 일 선택
                            Column {
                                Text(
                                    text = "일",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                LazyVerticalGrid(
                                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(
                                        7
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.height(120.dp)
                                ) {
                                    items(availableDays) { day ->
                                        Card(
                                            modifier = Modifier
                                                .aspectRatio(1f)
                                                .clickable { selectedDay = day },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (selectedDay == day)
                                                    Color(0xFF81C4E8) else Color.White
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            border = if (selectedDay != day)
                                                androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    Color(0xFFE2E8F0)
                                                )
                                            else null
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = day.toString(),
                                                    fontSize = 14.sp,
                                                    fontWeight = if (selectedDay == day) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (selectedDay == day) Color.White else Color(
                                                        0xFF4A5568
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 시간 선택 카드
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF8FAFC)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_clock),
                                    contentDescription = "시간",
                                    tint = Color(0xFF81C4E8),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "시간 선택",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF4A5568)
                                )
                            }

                            // 선택된 시간 표시
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF81C4E8).copy(alpha = 0.1f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = String.format("%02d:%02d", selectedHour, selectedMinute),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF81C4E8),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // 시간 선택
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "시",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    LazyVerticalGrid(
                                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(
                                            4
                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.height(120.dp)
                                    ) {
                                        items((9..22).toList()) { hour ->
                                            Card(
                                                modifier = Modifier
                                                    .aspectRatio(1f)
                                                    .clickable { selectedHour = hour },
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (selectedHour == hour)
                                                        Color(0xFF81C4E8) else Color.White
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                border = if (selectedHour != hour)
                                                    androidx.compose.foundation.BorderStroke(
                                                        1.dp,
                                                        Color(0xFFE2E8F0)
                                                    )
                                                else null
                                            ) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = String.format("%02d", hour),
                                                        fontSize = 12.sp,
                                                        fontWeight = if (selectedHour == hour) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (selectedHour == hour) Color.White else Color(
                                                            0xFF4A5568
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 분 선택
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "분",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(0, 30).forEach { minute ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(40.dp)
                                                    .clickable { selectedMinute = minute },
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (selectedMinute == minute)
                                                        Color(0xFF81C4E8) else Color.White
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                border = if (selectedMinute != minute)
                                                    androidx.compose.foundation.BorderStroke(
                                                        1.dp,
                                                        Color(0xFFE2E8F0)
                                                    )
                                                else null
                                            ) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = String.format("%02d분", minute),
                                                        fontSize = 14.sp,
                                                        fontWeight = if (selectedMinute == minute) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (selectedMinute == minute) Color.White else Color(
                                                            0xFF4A5568
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 버튼
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF1F5F9),
                                contentColor = Color(0xFF64748B)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("취소", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = {
                                val dateTimeString = String.format(
                                    "2025-%02d-%02d-%02d:%02d",
                                    selectedMonth, selectedDay, selectedHour, selectedMinute
                                )
                                onDateTimeSelected(dateTimeString)
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF81C4E8)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "확인",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupRegisterScreen(
    navController: NavHostController,
    viewModel: GroupRegisterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val categories = listOf("독서", "학습", "첨삭")
    val scrollState = rememberScrollState()

    // 색상 정의
    val selectedColor = Color(0xFF81C4E8)
    val unselectedColor = Color(0xFFE0E0E0)
    val backgroundColor = Color.White

    val context = LocalContext.current

    // PDF
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var hasRequestedPermissionOnce by remember { mutableStateOf(false) }

    // PDF 선택 상태 관리
    var selectedPdfFile by remember { mutableStateOf<File?>(null) }
    var selectedPdfFileName by remember { mutableStateOf("") }
    var isPdfImported by remember { mutableStateOf(false) }
    var isOcrRequired by remember { mutableStateOf<Boolean?>(null) } // OCR 필요 여부
    var isAnalyzing by remember { mutableStateOf(false) } // 분석 중 상태
    var pdfAnalysisResult by remember { mutableStateOf<PdfAnalysisResult?>(null) } // 분석 결과

    val displayFileName = if (selectedPdfFileName.length > 25) {
        selectedPdfFileName.take(22) + "..."
    } else selectedPdfFileName

    // 성공/실패 처리
    LaunchedEffect(uiState) {
        when {
            uiState.isSuccess -> {
                Log.d(TAG, "모임 생성 성공!")

                // 1. ViewModel 폼 초기화
                viewModel.resetForm()

                // 3. UI 상태 초기화 (성공 플래그 리셋)
                viewModel.resetState()

                // 4. 화면 이동
                navController.popBackStack()

                Log.d(TAG, "모든 상태가 초기화되었습니다")
            }

            uiState.errorMessage != null -> {
                Log.e(TAG, "모임 생성 실패: ${uiState.errorMessage}")
            }
        }
    }

    // 권한 확인 함수
    fun checkStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            // Android 13 이상: 새로운 미디어 권한들 중 하나라도 있으면 됨
            ContextCompat.checkSelfPermission(
                context,
                "android.permission.READ_MEDIA_IMAGES"
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 12 이하: READ_EXTERNAL_STORAGE 권한 확인
            ContextCompat.checkSelfPermission(
                context,
                "android.permission.READ_EXTERNAL_STORAGE"
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Activity result launcher for PDF picking
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                isAnalyzing = true // 분석 시작

                context.contentResolver?.openInputStream(it)?.use { input ->
                    val saveFile = AppFileManager.getNewPdfFile(context)
                    FileOutputStream(saveFile).use { output ->
                        input.copyTo(output)
                    }
                    viewModel.updatePdfFile(saveFile)
                    selectedPdfFile = saveFile
                    selectedPdfFileName = saveFile.name
                    isPdfImported = true

                    // PDF OCR 필요 여부 확인 (백그라운드에서 실행)
                    kotlin.concurrent.thread {
                        try {
                            val analysisResult = PdfOcrChecker.analyzePdf(context, saveFile)

                            // UI 스레드에서 상태 업데이트
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                pdfAnalysisResult = analysisResult
                                isOcrRequired = analysisResult.isOcrRequired
                                isAnalyzing = false

                                Log.d(TAG, "PDF 분석 완료 - OCR 필요: ${analysisResult.isOcrRequired}")
                                Log.d(TAG, "분석 결과: $analysisResult")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "PDF 분석 중 오류", e)
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                isOcrRequired = true // 오류 시 OCR 필요로 설정
                                isAnalyzing = false
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    // 권한 요청 런처
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRequestedPermissionOnce = true // 권한 요청했음을 표시

        if (granted) {
            Log.d(TAG, "저장소 권한이 승인되었습니다")
            pendingAction?.invoke()
            pendingAction = null
        } else {
            Log.e(TAG, "저장소 권한이 거부되었습니다")
            pendingAction = null
        }
    }

    // 설정으로 이동하는 런처
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // 설정에서 돌아왔을 때 권한 다시 확인
        if (checkStoragePermissions()) {
        }
    }

    // 권한 요청 함수
    fun requestStoragePermissions() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            "android.permission.READ_MEDIA_IMAGES"
        } else {
            "android.permission.READ_EXTERNAL_STORAGE"
        }

        Log.d(TAG, "권한 요청: $permission")
        permissionLauncher.launch(permission)
    }

    // PDF 업로드 클릭 시 권한 체크 함수
    fun handleAddPdfClick() {
        Log.d(TAG, "PDF 추가 버튼 클릭")

        if (!checkStoragePermissions()) {
            Log.d(TAG, "권한이 없음")

            if (hasRequestedPermissionOnce) {
                // 이미 한 번 권한을 요청했었다면 → 설정으로 이동 다이얼로그
                Log.d(TAG, "이미 권한 요청했음 - 설정 다이얼로그 표시")
                showPermissionDialog = true
            } else {
                // 처음 권한 요청 → 시스템 권한 다이얼로그
                Log.d(TAG, "첫 권한 요청 - 시스템 다이얼로그 표시")
                requestStoragePermissions()
            }
        } else {
            Log.d(TAG, "권한 확인됨 - 옵션 표시")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        CustomTopAppBar(
            title = "create_group",
            navController = navController,
            onBackPressed = { navController.popBackStack() }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            Text(
                text = "파일 업로드",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        handleAddPdfClick()
                        if (checkStoragePermissions()) {
                            pdfPickerLauncher.launch(arrayOf("application/pdf"))
                        } else {
                            pendingAction = {
                                pdfPickerLauncher.launch(arrayOf("application/pdf"))
                            }
                        }
                    }
                    .border(
                        width = 1.dp,
                        color = if (selectedPdfFile != null) selectedColor else unselectedColor,
                        shape = RoundedCornerShape(8.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = backgroundColor
                ),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(10.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_pdf),
                        contentDescription = "pdf_upload",
                        modifier = Modifier.size(40.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (selectedPdfFileName.isNotEmpty()) displayFileName else "PDF 업로드",
                            color = if (selectedPdfFile != null) Color.Black else Color.Gray,
                            fontSize = 16.sp,
                            fontWeight = if (selectedPdfFile != null) FontWeight.Medium else FontWeight.Normal
                        )
                        if (selectedPdfFile != null) {
                            Text(
                                text = "파일이 선택되었습니다",
                                color = selectedColor,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        } else {
                            Text(
                                text = "PDF 파일을 선택해주세요",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    if (selectedPdfFile != null) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close_white),
                            contentDescription = "파일 제거",
                            modifier = Modifier
                                .size(24.dp)
                                .clickable {
                                    selectedPdfFile?.delete()
                                    selectedPdfFile = null
                                    selectedPdfFileName = ""
                                    isOcrRequired = null
                                    pdfAnalysisResult = null
                                    isAnalyzing = false
                                    isPdfImported = false

                                    viewModel.resetPdfFile()
                                },
                            tint = Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "모임 정보",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 모임 제목 입력
            OutlinedTextField(
                value = viewModel.groupName,
                onValueChange = { viewModel.updateGroupName(it) },
                placeholder = { Text("모임 제목을 입력해주세요", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = selectedColor,
                    focusedLabelColor = selectedColor,
                    cursorColor = selectedColor
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 모임 설명 작성
            OutlinedTextField(
                value = viewModel.groupDescription,
                onValueChange = { viewModel.updateGroupDescription(it) },
                placeholder = { Text("모임에 대한 설명을 작성해주세요", color = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(8.dp),
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = selectedColor,
                    focusedLabelColor = selectedColor,
                    cursorColor = selectedColor
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 카테고리 토글 버튼
            Text(
                text = "카테고리",
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    Button(
                        onClick = { viewModel.updateCategory(category) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (viewModel.selectedCategory == category) selectedColor else Color.White,
                            contentColor = if (viewModel.selectedCategory == category) Color.White else Color.Gray
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (viewModel.selectedCategory == category) selectedColor else unselectedColor
                        )
                    ) {
                        Text(
                            text = category,
                            fontSize = 14.sp,
                            fontWeight = if (viewModel.selectedCategory == category) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 최대 참여 인원수 토글 버튼
            Text(
                text = "최대 참여 인원수",
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (1..6).forEach { number ->
                    Button(
                        onClick = { viewModel.updateMaxMembers(number) },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (viewModel.maxMembers == number) selectedColor else Color.White,
                            contentColor = if (viewModel.maxMembers == number) Color.White else Color.Gray
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (viewModel.maxMembers == number) selectedColor else unselectedColor
                        ),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text(
                            text = "${number}명",
                            fontSize = 12.sp,
                            fontWeight = if (viewModel.maxMembers == number) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 모임 시작 시간 설정
            Text(
                text = "모임 시작 시간",
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.showDateTimePicker() }
                    .border(
                        width = 1.dp,
                        color = if (viewModel.selectedDateTime.isNotEmpty()) selectedColor else unselectedColor,
                        shape = RoundedCornerShape(8.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = backgroundColor
                ),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_clock),
                        contentDescription = "시간 선택",
                        modifier = Modifier.size(24.dp),
                        tint = if (viewModel.selectedDateTime.isNotEmpty()) selectedColor else Color.Gray
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (viewModel.selectedDateTime.isEmpty()) "날짜와 시간을 선택해주세요" else viewModel.selectedDateTime,
                        fontSize = 16.sp,
                        color = if (viewModel.selectedDateTime.isEmpty()) Color.Gray else Color.Black,
                        fontWeight = if (viewModel.selectedDateTime.isNotEmpty()) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 최소 평점 요구사항 설정
            Text(
                text = "최소 요구 평점",
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "설정한 평점 이상의 사용자만 모임에 참여할 수 있습니다",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 평점 선택 카드
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF8FAFC)
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // 현재 선택된 평점 표시
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_star),
                                contentDescription = "평점",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "최소 평점",
                                fontSize = 14.sp,
                                color = Color(0xFF4A5568),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "${viewModel.minRequiredRating}점 이상",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF81C4E8)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 평점 선택 그리드 (0 ~ 5, 1점 단위)
                    LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(6),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(80.dp)
                    ) {
                        items((0..5).toList()) { rating ->
                            Card(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clickable { viewModel.updateMinRequiredRating(rating) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (viewModel.minRequiredRating == rating)
                                        Color(0xFF81C4E8) else Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = if (viewModel.minRequiredRating != rating)
                                    androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        Color(0xFFE2E8F0)
                                    )
                                else null
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = rating.toString(),
                                            fontSize = 14.sp,
                                            fontWeight = if (viewModel.minRequiredRating == rating)
                                                FontWeight.Bold else FontWeight.Normal,
                                            color = if (viewModel.minRequiredRating == rating)
                                                Color.White else Color(0xFF4A5568)
                                        )

                                        // 별 아이콘 표시 (선택된 경우에만)
                                        if (viewModel.minRequiredRating == rating) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_star),
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 평점 설명
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "💡 0점: 모든 사용자 참여 가능, 높은 점수일수록 더 신뢰할 수 있는 사용자들과 모임할 수 있어요",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }



            Spacer(modifier = Modifier.height(32.dp))

            // 모임 개설 버튼
            Button(
                onClick = {
                    // 현재 상태 로깅
                    viewModel.logCurrentState()

                    // PDF 파일 확인
                    val currentPdfFile = selectedPdfFile
                    if (currentPdfFile == null || !currentPdfFile.exists()) {
                        Log.e(TAG, "PDF 파일이 선택되지 않았거나 존재하지 않습니다")
                        // 에러 처리 (토스트 메시지 등)
                        return@Button
                    }

                    // OCR 분석이 완료되었는지 확인
                    if (isAnalyzing) {
                        Log.w(TAG, "PDF 분석이 아직 진행 중입니다")
                        return@Button
                    }

                    // OCR 필요 여부 확인 (null인 경우 안전하게 true로 설정)
                    val ocrRequired = isOcrRequired ?: true

                    Log.d(
                        TAG, """
            모임 개설 시작:
            - 제목: ${viewModel.groupName}
            - 카테고리: ${viewModel.selectedCategory}
            - 인원: ${viewModel.maxMembers}명
            - 시간: ${viewModel.selectedDateTime}
            - PDF 파일: ${currentPdfFile.name}
            - 파일 크기: ${currentPdfFile.length()} bytes
            - OCR 필요: $ocrRequired
        """.trimIndent()
                    )

                    // 분석 결과 로깅
                    pdfAnalysisResult?.let { result ->
                        Log.d(
                            TAG, """
                PDF 분석 결과:
                - 총 페이지: ${result.totalPages}
                - 분석된 페이지: ${result.analyzedPages}
                - 텍스트 페이지: ${result.textPages}
                - 이미지 페이지: ${result.imagePages}
                - 텍스트 비율: ${(result.textPageRatio * 100).toInt()}%
                - 평균 텍스트 길이: ${result.avgTextPerPage.toInt()}자
                - OCR 필요: ${result.isOcrRequired}
            """.trimIndent()
                        )
                    }
                    // 그룹 생성 실행
                    viewModel.createGroupWithPdf(
                        isOcrRequired = ocrRequired
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = selectedColor
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = run {
                    val isValid = viewModel.isFormValid() && !isAnalyzing
                    Log.d(TAG, """
            버튼 활성화 조건:
            - viewModel.isFormValid(): ${viewModel.isFormValid()}
            - !isAnalyzing: ${!isAnalyzing}
            - selectedPdfFile != null: ${selectedPdfFile != null}
            - 최종 결과: $isValid
        """.trimIndent())
                    isValid
                }
            ) {
                Text(
                    text = if (uiState.isLoading) "생성 중..." else "모임 개설하기",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 날짜/시간 선택 다이얼로그
        if (viewModel.showDateTimePicker) {
            DateTimePickerDialog(
                onDateTimeSelected = { dateTime ->
                    viewModel.updateDateTime(dateTime)
                },
                onDismiss = { viewModel.hideDateTimePicker() }
            )
        }
    }

    // 권한 설정 다이얼로그
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = {
                Text(
                    text = "권한 필요",
                )
            },
            text = {
                Text(
                    text = "PDF 파일에 접근하려면 저장소 권한이 필요합니다.\n설정에서 권한을 허용해주세요.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        // 앱 설정으로 이동
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        settingsLauncher.launch(intent)
                    }
                ) {
                    Text(
                        text = "설정으로 이동",
                        color = Color.Black
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPermissionDialog = false }
                ) {
                    Text(
                        text = "취소",
                        color = Color.Gray
                    )
                }
            }
        )
    }
}