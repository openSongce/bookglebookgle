package com.ssafy.bookglebookgle.util

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ssafy.bookglebookgle.repository.GroupRepositoryImpl
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

@HiltWorker
class GroupCreationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val groupRepository: GroupRepositoryImpl,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "GroupCreationWorker"
        private const val NETWORK_TIMEOUT_MS = 300_000L
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Worker 시작 - Hilt 의존성 주입 성공!")

        return try {
            // 입력 데이터 받기
            val groupInfoJson = inputData.getString("groupInfo") ?: return Result.failure()
            val pdfFilePath = inputData.getString("pdfFilePath") ?: return Result.failure()
            val isOcrRequired = inputData.getBoolean("isOcrRequired", true)
            val groupName = inputData.getString("groupName") ?: "새 모임"

            Log.d(TAG, "작업 데이터: groupName=$groupName, isOcrRequired=$isOcrRequired")

            // PDF 파일 확인
            val pdfFile = File(pdfFilePath)
            if (!pdfFile.exists()) {
                Log.e(TAG, "PDF 파일을 찾을 수 없음: $pdfFilePath")
                notificationHelper.showGroupCreationFailedNotification("PDF 파일을 찾을 수 없습니다.")
                return Result.failure()
            }

            val fileSizeMB = pdfFile.length() / (1024 * 1024)
            Log.d(TAG, "PDF 파일 크기: ${fileSizeMB}MB")

            // 처리 중 알림
            if (fileSizeMB > 50) {
                notificationHelper.showProcessingNotification(groupName, "큰 파일 처리 중...")
            } else if (isOcrRequired) {
                notificationHelper.showProcessingNotification(groupName, "PDF 텍스트 인식 중...")
            }

            // MultipartBody 생성
            val groupInfoRequestBody = groupInfoJson.toRequestBody("application/json".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData(
                "file",
                pdfFile.name,
                pdfFile.asRequestBody("application/pdf".toMediaTypeOrNull())
            )

            Log.d(TAG, "API 호출 시작 - OCR Required: $isOcrRequired")

            // API 호출
            val response = withTimeout(NETWORK_TIMEOUT_MS) {
                if (isOcrRequired) {
                    groupRepository.createGroup(groupInfoRequestBody, filePart)
                } else {
                    groupRepository.createGroupWithoutOcr(groupInfoRequestBody, filePart)
                }
            }

            // 처리 중 알림 취소
            notificationHelper.cancelProcessingNotification()

            if (response.isSuccessful) {
                Log.d(TAG, "모임 생성 성공 - 응답코드: ${response.code()}")

                val responseBodyString = response.body()?.string()
                Log.d(TAG, "서버 응답: $responseBodyString")

                notificationHelper.showGroupCreationSuccessNotification(
                    groupName = groupName,
                    isOcrProcessed = isOcrRequired,
                    responseBody = responseBodyString
                )

                cleanupTempFile(pdfFile)
                Result.success()
            } else {
                val errorMessage = response.errorBody()?.string() ?: "서버 오류 (${response.code()})"
                Log.e(TAG, "모임 생성 실패 - Code: ${response.code()}, Message: $errorMessage")

                notificationHelper.showGroupCreationFailedNotification(
                    "서버 오류가 발생했습니다. (${response.code()})"
                )
                Result.failure()
            }

        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "모임 생성 타임아웃 발생", e)
            notificationHelper.cancelProcessingNotification()
            notificationHelper.showGroupCreationTimeoutNotification()
            Result.failure()

        } catch (e: CancellationException) {
            Log.e(TAG, "모임 생성 작업이 취소됨", e)
            notificationHelper.cancelProcessingNotification()
            notificationHelper.showGroupCreationCancelledNotification()
            Result.failure()

        } catch (e: Exception) {
            Log.e(TAG, "모임 생성 중 예외 발생", e)
            notificationHelper.cancelProcessingNotification()
            notificationHelper.showGroupCreationFailedNotification("네트워크 오류가 발생했습니다.")
            Result.failure()
        }
    }

    private fun cleanupTempFile(pdfFile: File) {
        try {
            if (pdfFile.parent?.contains("cache") == true) {
                pdfFile.delete()
                Log.d(TAG, "임시 PDF 파일 삭제 완료")
            }
        } catch (e: Exception) {
            Log.w(TAG, "임시 파일 삭제 실패", e)
        }
    }
}