package com.ssafy.bookglebookgle.ui.screen

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.ssafy.bookglebookgle.ui.theme.BaseColor
import com.ssafy.bookglebookgle.ui.theme.MainColor
import com.ssafy.bookglebookgle.util.PdfAnalysisResult
import com.ssafy.bookglebookgle.util.PdfOcrChecker
import com.ssafy.bookglebookgle.viewmodel.GroupRegisterViewModel
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Calendar
import kotlin.math.roundToInt

private const val TAG = "싸피_GroupRegisterScreen"

// 요일 정보를 담는 데이터 클래스
data class DayInfo(
    val dayName: String,
    val dayOfMonth: Int,
    val month: Int,
    val isToday: Boolean
)

@Composable
fun DateTimePickerDialog(
    onDateTimeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val calendar = Calendar.getInstance()
    val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

    // 오늘부터 7일간의 요일 정보 생성 (월~일 순으로 정렬)
    val weekDays = remember {
        val days = mutableListOf<DayInfo>()
        val tempCalendar = Calendar.getInstance()

        for (i in 0..6) {
            val dayOfWeek = tempCalendar.get(Calendar.DAY_OF_WEEK)
            val dayOfMonth = tempCalendar.get(Calendar.DAY_OF_MONTH)
            val monthValue = tempCalendar.get(Calendar.MONTH)
            val month = monthValue + 1

            val dayName = when (dayOfWeek) {
                Calendar.SUNDAY -> "일"
                Calendar.MONDAY -> "월"
                Calendar.TUESDAY -> "화"
                Calendar.WEDNESDAY -> "수"
                Calendar.THURSDAY -> "목"
                Calendar.FRIDAY -> "금"
                Calendar.SATURDAY -> "토"
                else -> ""
            }

            days.add(
                DayInfo(
                    dayName = dayName,
                    dayOfMonth = dayOfMonth,
                    month = month,
                    isToday = i == 0
                )
            )
            tempCalendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        // 월요일부터 시작하도록 정렬
        val sortedDays = mutableListOf<DayInfo>()

        // 오늘이 몇 번째 요일인지 확인 (월요일 = 0, 화요일 = 1, ..., 일요일 = 6)
        val today = Calendar.getInstance()
        val todayDayOfWeek = today.get(Calendar.DAY_OF_WEEK)
        val mondayBasedToday = when (todayDayOfWeek) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }

        // 월요일부터 일요일까지 순서대로 정렬
        for (targetDay in 0..6) { // 0=월, 1=화, ..., 6=일
            val daysFromToday = (targetDay - mondayBasedToday + 7) % 7
            if (daysFromToday < days.size) {
                sortedDays.add(days[daysFromToday])
            }
        }

        sortedDays
    }

    var selectedDayIndex by remember { mutableIntStateOf(0) }
    var selectedHour by remember { mutableIntStateOf(9) }
    var selectedMinute by remember { mutableIntStateOf(0) }

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

                    // 요일 선택 카드
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF8FAFC)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
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
                                    text = "요일 선택",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF4A5568)
                                )
                            }

                            // 요일 선택 그리드
                            LazyVerticalGrid(
                                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(7),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.height(40.dp)
                            ) {
                                items(weekDays.size) { index ->
                                    val day = weekDays[index]
                                    Card(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clickable { selectedDayIndex = index },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (selectedDayIndex == index)
                                                Color(0xFFEFE5D8) else Color.White
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        border = if (selectedDayIndex != index)
                                            androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                Color(0xFFE2E8F0)
                                            )
                                        else null
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = day.dayName,
                                                fontSize = 12.sp,
                                                fontWeight = if (selectedDayIndex == index) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selectedDayIndex == index) Color.Black else Color(
                                                    0xFF4A5568
                                                )
                                            )
                                            Text(
                                                text = "${day.dayOfMonth}",
                                                fontSize = 10.sp,
                                                fontWeight = if (selectedDayIndex == index) FontWeight.Medium else FontWeight.Normal,
                                                color = if (selectedDayIndex == index) Color.Black else Color(
                                                    0xFF64748B
                                                )
                                            )
                                            if (day.isToday) {
                                                Text(
                                                    text = "오늘",
                                                    fontSize = 8.sp,
                                                    color = if (selectedDayIndex == index) Color.Black else Color(
                                                        0xFF81C4E8
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

                    // 시간 선택 카드 (간소화된 24시간 버전)
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
                                    tint = Color(0xFFEFE5D8),
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
                                    containerColor = Color(0xFFEFE5D8)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = String.format("%02d:%02d", selectedHour, selectedMinute),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            LazyVerticalGrid(
                                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(6),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.height(130.dp)
                            ) {
                                items(24) { hour ->
                                    Card(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clickable { selectedHour = hour },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (selectedHour == hour)
                                                Color(0xFFEFE5D8) else Color.White
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (selectedHour == hour) Color(0xFFEFE5D8) else Color(
                                                0xFFE2E8F0
                                            )
                                        )
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = String.format("%02d", hour),
                                                fontSize = 12.sp,
                                                fontWeight = if (selectedHour == hour) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selectedHour == hour) Color.Black else Color(
                                                    0xFF4A5568
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                listOf(0, 30).forEach { minute ->
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .clickable { selectedMinute = minute },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (selectedMinute == minute)
                                                Color(0xFFEFE5D8) else Color.White
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (selectedMinute == minute) Color(0xFFEFE5D8) else Color(
                                                0xFFE2E8F0
                                            )
                                        )
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = String.format("%02d분", minute),
                                                fontSize = 16.sp,
                                                fontWeight = if (selectedMinute == minute) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selectedMinute == minute) Color.Black else Color(
                                                    0xFF4A5568
                                                )
                                            )
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
                                val selectedDay = weekDays[selectedDayIndex]
                                // 요일을 전체 이름으로 변환
                                val fullDayName = when (selectedDay.dayName) {
                                    "월" -> "월요일"
                                    "화" -> "화요일"
                                    "수" -> "수요일"
                                    "목" -> "목요일"
                                    "금" -> "금요일"
                                    "토" -> "토요일"
                                    "일" -> "일요일"
                                    else -> selectedDay.dayName
                                }

                                // 한국어 형식으로 날짜/시간 문자열 생성: "목요일 14시 30분"
                                val formattedDateTime =
                                    "$fullDayName ${selectedHour}시 ${selectedMinute}분"
                                onDateTimeSelected(formattedDateTime)
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEFE5D8)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "확인",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
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

                // 2. UI 상태 초기화 (성공 플래그 리셋)
                viewModel.resetState()

                // 3. 화면 이동
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

    // URI에서 파일명을 추출하는 함수 추가 (import 섹션 다음에 추가)
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var fileName: String? = null

        // DocumentProvider를 통한 파일명 추출
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex)
            }
        }

        // 위에서 실패하면 URI path에서 추출
        if (fileName == null) {
            fileName = uri.path?.let { path ->
                path.substring(path.lastIndexOf('/') + 1)
            }
        }

        return fileName
    }

    // PDF 선택 런처
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                isAnalyzing = true // 분석 시작

                val originalFileName = getFileNameFromUri(context, it) ?: "선택된 파일"

                context.contentResolver?.openInputStream(it)?.use { input ->
//                    val saveFile = AppFileManager.getNewPdfFile(context)
                    val saveFile = File(context.cacheDir, originalFileName)
                    FileOutputStream(saveFile).use { output ->
                        input.copyTo(output)
                    }
                    viewModel.updatePdfFile(saveFile)
                    selectedPdfFile = saveFile
                    selectedPdfFileName = originalFileName
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
            pendingAction?.invoke()
            pendingAction = null
        } else {
            Toast.makeText(context, "저장소 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
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
        if (!checkStoragePermissions()) {
            Log.d(TAG, "권한이 없음")

            if (hasRequestedPermissionOnce) {
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

                        pendingAction = {
                            pdfPickerLauncher.launch(arrayOf("application/pdf"))
                        }

                        if (checkStoragePermissions()) {
                            pendingAction?.invoke()
                            pendingAction = null
                        }

                    }
                    .border(
                        width = 1.dp,
                        color = if (selectedPdfFile != null) BaseColor else unselectedColor,
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
                            text = if (selectedPdfFileName.isNotEmpty()) {
                                // 파일명이 너무 길면 앞부분만 표시
                                if (selectedPdfFileName.length > 25) {
                                    selectedPdfFileName.take(22) + "..."
                                } else {
                                    selectedPdfFileName
                                }
                            } else {
                                "PDF 업로드"
                            },
                            color = if (selectedPdfFile != null) Color.Black else Color.Gray,
                            fontSize = 16.sp,
                            fontWeight = if (selectedPdfFile != null) FontWeight.Medium else FontWeight.Normal
                        )
                        if (selectedPdfFile != null) {
                            Text(
                                text = "파일이 선택되었습니다",
                                color = BaseColor,
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

            CompositionLocalProvider(
                LocalTextSelectionColors provides TextSelectionColors(
                    handleColor = BaseColor, // 드래그 핸들(물방울) 색상
                    backgroundColor = BaseColor.copy(alpha = 0.3f) // 선택 영역 배경색 (투명도 적용)
                )
            ) {
                // 모임 제목 입력
                OutlinedTextField(
                    value = viewModel.groupName,
                    onValueChange = { viewModel.updateGroupName(it) },
                    placeholder = { Text("모임 제목을 입력해주세요", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BaseColor,
                        focusedLabelColor = BaseColor,
                        cursorColor = BaseColor
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            CompositionLocalProvider(
                LocalTextSelectionColors provides TextSelectionColors(
                    handleColor = BaseColor, // 드래그 핸들(물방울) 색상
                    backgroundColor = BaseColor.copy(alpha = 0.3f) // 선택 영역 배경색 (투명도 적용)
                )
            ) {
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
                        focusedBorderColor = BaseColor,
                        focusedLabelColor = BaseColor,
                        cursorColor = BaseColor
                    )
                )
            }

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
                            containerColor = if (viewModel.selectedCategory == category) Color(
                                0xFFEFE5D8
                            ) else Color.White,
                            contentColor = if (viewModel.selectedCategory == category) Color.Black else Color.Gray
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (viewModel.selectedCategory == category) Color(0xFFEFE5D8) else unselectedColor
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
                            containerColor = if (viewModel.maxMembers == number) Color(0xFFEFE5D8) else Color.White,
                            contentColor = if (viewModel.maxMembers == number) Color.Black else Color.Gray
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (viewModel.maxMembers == number) Color(0xFFEFE5D8) else unselectedColor
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
                        color = if (viewModel.selectedDateTime.isNotEmpty()) Color(0xFFEFE5D8) else unselectedColor,
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
                        tint = if (viewModel.selectedDateTime.isNotEmpty()) Color(0xFFEFE5D8) else Color.Gray
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
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
                            color = MainColor
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 평점 선택 그리드 (0 ~ 5, 1점 단위)
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

                                // 커스텀 슬라이더
                                RatingSlider(
                                    value = viewModel.minRequiredRating,
                                    onValueChange = { rating ->
                                        viewModel.updateMinRequiredRating(rating)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
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
                    containerColor = Color(0xFFEFE5D8)
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = run {
                    val isValid = viewModel.isFormValid() && !isAnalyzing
                    Log.d(
                        TAG, """
            버튼 활성화 조건:
            - viewModel.isFormValid(): ${viewModel.isFormValid()}
            - !isAnalyzing: ${!isAnalyzing}
            - selectedPdfFile != null: ${selectedPdfFile != null}
            - 최종 결과: $isValid
        """.trimIndent()
                    )
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
                            val intent =
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
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
}

@Composable
fun RatingSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(value.toFloat()) } // 연속적인 드래그 위치
    val density = LocalDensity.current

    // 드래그가 끝나지 않았다면 dragPosition, 끝났다면 value 사용
    val displayPosition = if (isDragging) dragPosition else value.toFloat()

    BoxWithConstraints(
        modifier = modifier.height(60.dp)
    ) {
        val sliderWidth = maxWidth - 32.dp // 좌우 여백 고려
        val stepWidth = sliderWidth / 5f // 0~5까지 6개 지점
        val containerWidthPx = with(density) { sliderWidth.toPx() }

        // 터치 위치를 연속적인 값으로 변환하는 함수 (0.0 ~ 5.0)
        fun getPositionFromTouch(touchX: Float): Float {
            val ratio = (touchX / containerWidthPx).coerceIn(0f, 1f)
            return ratio * 5f
        }

        // 연속값을 가장 가까운 정수로 스냅하는 함수
        fun snapToNearestInteger(position: Float): Int {
            return position.roundToInt().coerceIn(0, 5)
        }

        Column {
            // 평점 숫자 표시 - 가장 가까운 정수 기준으로 강조
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                (0..5).forEach { rating ->
                    val nearestValue = snapToNearestInteger(displayPosition)
                    Text(
                        text = rating.toString(),
                        fontSize = 12.sp,
                        fontWeight = if (nearestValue == rating) FontWeight.Bold else FontWeight.Normal,
                        color = if (nearestValue == rating) MainColor else Color(0xFF64748B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 슬라이더 트랙과 썸
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(horizontal = 16.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                // 드래그 시작 시 터치한 위치로 바로 이동
                                dragPosition = getPositionFromTouch(offset.x)
                            },
                            onDragEnd = {
                                isDragging = false
                                val finalValue = snapToNearestInteger(dragPosition)
                                onValueChange(finalValue) // 최종 값은 정수로 스냅
                            },
                            onDrag = { _, dragAmount ->
                                // 드래그 중 연속적인 위치 업데이트
                                val currentPositionPx = (dragPosition / 5f) * containerWidthPx
                                val newPositionPx = (currentPositionPx + dragAmount.x).coerceIn(
                                    0f,
                                    containerWidthPx
                                )
                                dragPosition = (newPositionPx / containerWidthPx) * 5f
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            // 터치 위치로 바로 이동하고 가장 가까운 정수로 스냅
                            val touchPosition = getPositionFromTouch(offset.x)
                            val snappedValue = snapToNearestInteger(touchPosition)
                            dragPosition = snappedValue.toFloat()
                            onValueChange(snappedValue)
                        }
                    }
            ) {
                // 백그라운드 트랙
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .align(Alignment.Center)
                        .background(
                            Color(0xFFE2E8F0),
                            RoundedCornerShape(3.dp)
                        )
                )

                // 활성화된 트랙 - 드래그 위치까지 채워짐 (0부터 현재 드래그 위치까지)
                Box(
                    modifier = Modifier
                        .width(stepWidth * displayPosition)
                        .height(6.dp)
                        .align(Alignment.CenterStart)
                        .background(
                            MainColor,
                            RoundedCornerShape(3.dp)
                        )
                )

                // 드래그 가능한 썸 - 드래그 위치에 따라 이동
                Box(
                    modifier = Modifier
                        .size(if (isDragging) 28.dp else 24.dp)
                        .offset(x = (stepWidth * displayPosition) - if (isDragging) 14.dp else 12.dp)
                        .align(Alignment.CenterStart)
                        .background(
                            Color.White,
                            CircleShape
                        )
                        .border(
                            if (isDragging) 3.dp else 2.dp,
                            if (isDragging) MainColor else Color(0xFFE2E8F0),
                            CircleShape
                        )
                ) {
                    // 썸 중앙의 작은 원
                    Box(
                        modifier = Modifier
                            .size(if (isDragging) 10.dp else 8.dp)
                            .align(Alignment.Center)
                            .background(
                                MainColor,
                                CircleShape
                            )
                    )
                }
            }
        }
    }
}