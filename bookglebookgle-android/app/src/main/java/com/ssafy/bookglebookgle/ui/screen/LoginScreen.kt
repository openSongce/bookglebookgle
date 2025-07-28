package com.ssafy.bookglebookgle.ui.screen

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.viewmodel.LoginViewModel
import kotlinx.coroutines.launch
import androidx.credentials.*
import com.google.android.libraries.identity.googleid.*
import android.util.Log
import androidx.credentials.exceptions.GetCredentialException
import com.ssafy.bookglebookgle.BuildConfig


@Composable
fun LoginScreen(navController: NavController, loginViewModel: LoginViewModel = hiltViewModel()) {

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val id = loginViewModel.id.value
    val password = loginViewModel.password.value
    val loginResult = loginViewModel.loginSuccess.value

    val context = LocalContext.current

    val window = (LocalView.current.context as Activity).window

    val lifecycleOwner = LocalLifecycleOwner.current

    val clientId = BuildConfig.GOOGLE_CLIENT_ID

    SideEffect {
        WindowCompat.getInsetsController(window, window.decorView)?.isAppearanceLightStatusBars = true
    }

    // 로그인 성공 시 화면 전환
    LaunchedEffect(loginResult) {
        when (loginResult) {
            true -> {
                navController.navigate("main") {
                    popUpTo("login") { inclusive = true }
                }
            }
            false -> {
                Toast.makeText(context, "로그인 실패", Toast.LENGTH_SHORT).show()
                loginViewModel.loginSuccess.value = null // ✅ 다시 초기화
            }
            null -> {} // 아무것도 안함
        }
    }


    // 에러 메세지 토스트
    LaunchedEffect(loginViewModel.errorMessage.value) {
        loginViewModel.errorMessage.value?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            loginViewModel.errorMessage.value = null
        }
    }


    //Google Login
    val startGoogleLogin = {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId) // 웹 클라이언트 ID 꼭 바꿔!
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(context)

        lifecycleOwner.lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = context as Activity
                )

                val credential = result.credential
                if (
                    credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    loginViewModel.googleLogin(idToken)
                }

            } catch (e: GetCredentialException) {
                Log.e("GOOGLE_LOGIN", "Credential 요청 실패", e)
                Toast.makeText(context, "구글 로그인 실패", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // UI
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val maxW = maxWidth
        val maxH = maxHeight

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = maxW * 0.08f)// 전체 가로 패딩을 화면의 8%로
                .padding(WindowInsets.systemBars.asPaddingValues()),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // 로고 (원형 + 책 아이콘)
            Surface(
                shape = CircleShape,
                color = Color(0xFFF5F0E6),
                modifier = Modifier.size(maxW * 0.3f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.login_logo),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.weight(0.3f))

            Text(
                text = "회원 서비스 이용을 위해\n로그인해주세요",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(0.5f))

            OutlinedTextField(
                value = id,
                onValueChange = { loginViewModel.id.value = it },
                placeholder = { Text("아이디를 입력해주세요.") },
                shape = RoundedCornerShape(maxW * 0.02f),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        keyboardController?.hide()  // 👈 키보드 내려감
                    }
                )
            )

            Spacer(modifier = Modifier.weight(0.25f))

            OutlinedTextField(
                value = password,
                onValueChange = { loginViewModel.password.value = it },
                placeholder = { Text("비밀번호를 입력해주세요.") },
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(maxW * 0.02f),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        keyboardController?.hide()  // 👈 키보드 내려감
                    }
                )
            )

            Spacer(modifier = Modifier.weight(0.25f))

            // 로그인 버튼
            Button(
                onClick = { loginViewModel.login() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (id.isNotBlank() && password.isNotBlank())
                        Color(0xFFDED0BB) else Color(0xFFCCC7C0)
                ),
                enabled = id.isNotBlank() && password.isNotBlank(),
                shape = RoundedCornerShape(maxW * 0.02f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxH * 0.06f)
            ) {
                Text("로그인", color = Color.White)
            }

            Spacer(modifier = Modifier.weight(0.25f))

            OrDivider()

            // 카카오 & 구글 로그인 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(4.5f / 1.5f) // 너비:높이 비율
                        .clip(RoundedCornerShape(maxW * 0.03f))
                        .background(Color(0xFFFEE500))
                        .clickable { /* 카카오 로그인 */ },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.login_kakao),
                            contentDescription = null,
                            modifier = Modifier.fillMaxHeight(0.5f)
                        )
                        Spacer(modifier = Modifier.width(maxW * 0.02f))
                        Text("시작하기", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.width(maxW * 0.04f))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(4.5f / 1.5f)
                        .clip(RoundedCornerShape(maxW * 0.03f))
                        .border(1.dp, Color.Gray, shape = RoundedCornerShape(maxW * 0.03f))
                        .clickable { startGoogleLogin() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.login_google),
                            contentDescription = null,
                            modifier = Modifier.fillMaxHeight(0.5f)
                        )
                        Spacer(modifier = Modifier.width(maxW * 0.02f))
                        Text("시작하기", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            TextButton(onClick = { /* 비밀번호 찾기 */ }) {
                Text("비밀번호 찾기", color = Color(0xFFCCC7C0), fontSize = maxW.value.times(0.03).sp)
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { navController.navigate("register") },
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFDED0BB)),
                shape = RoundedCornerShape(maxW * 0.02f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxH * 0.06f)
            ) {
                Text("회원가입", color = Color.White)
            }

            Spacer(modifier = Modifier.weight(0.5f))
        }
    }




}

@Composable
fun OrDivider(modifier: Modifier = Modifier, text: String = "또는") {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = Color.Gray)
        Text(
            text = "  $text  ",
            color = Color.Gray,
            style = MaterialTheme.typography.bodyMedium
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = Color.Gray)
    }
}