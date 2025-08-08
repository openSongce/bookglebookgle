package com.ssafy.bookglebookgle.notification

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.permissions.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NotificationPermissionRequester(
    autoRequest: Boolean = true,
    onGranted: () -> Unit
) {
    val context = LocalContext.current

    // Android 13 미만은 권한 필요 없음
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        LaunchedEffect(Unit) { onGranted() }
        return
    }

    val permissionState = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)

    // 권한이 허용되면 콜백 1회 호출
    LaunchedEffect(permissionState.status.isGranted) {
        if (permissionState.status.isGranted) onGranted()
    }

    when {
        permissionState.status.isGranted -> Unit

        permissionState.status.shouldShowRationale -> {
            // 사용자가 한 번 거절했을 때: 이유 다이얼로그
            PermissionRationaleDialog(
                onDismiss = { /* no-op */ },
                onRequest = { permissionState.launchPermissionRequest() },
                onOpenSettings = { openAppNotificationSettings(context) }
            )
        }

        else -> {
            // 아직 묻지 않았거나 "다시 묻지 않음"으로 거절됐을 때
            if (autoRequest) {
                LaunchedEffect(Unit) { permissionState.launchPermissionRequest() }
            } else {
                PermissionAskDialog(
                    onAsk = { permissionState.launchPermissionRequest() },
                    onOpenSettings = { openAppNotificationSettings(context) }
                )
            }
        }
    }
}

@Composable
private fun PermissionAskDialog(
    onAsk: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("알림 권한이 필요해요") },
        text = { Text("독서 리마인드 등 중요한 알림을 받기 위해 알림 권한을 허용해주세요.") },
        confirmButton = {
            TextButton(onClick = onAsk) { Text("허용 요청") }
        },
        dismissButton = {
            TextButton(onClick = onOpenSettings) { Text("설정 열기") }
        }
    )
}

@Composable
private fun PermissionRationaleDialog(
    onDismiss: () -> Unit,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("알림 권한이 거부됨") },
        text = { Text("그룹 스케줄 알림을 받으려면 알림 권한이 필요합니다.") },
        confirmButton = { TextButton(onClick = onRequest) { Text("다시 요청") } },
        dismissButton = { TextButton(onClick = onOpenSettings) { Text("설정 열기") } }
    )
}

private fun openAppNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
