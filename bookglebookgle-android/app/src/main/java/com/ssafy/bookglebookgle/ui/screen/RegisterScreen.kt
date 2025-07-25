package com.ssafy.bookglebookgle.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ssafy.bookglebookgle.entity.RegisterStep
import com.ssafy.bookglebookgle.viewmodel.RegisterViewModel



@Composable
fun RegisterScreen(navController: NavController, registerViewModel: RegisterViewModel = hiltViewModel()) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(registerViewModel.registerSuccess) {
        if (registerViewModel.registerSuccess) {
            navController.navigate("main") {
                popUpTo("register") { inclusive = true } // 뒤로가기 시 회원가입 화면 안 보이게
            }
        }
    }


    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxW = maxWidth
        val maxH = maxHeight
        val innerPadding = maxW * 0.02f

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = maxW * 0.08f)
                .padding(WindowInsets.systemBars.asPaddingValues()),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(maxH * 0.05f))


            Spacer(modifier = Modifier.height(maxH * 0.05f))
            Text("북글북글에서 사용할", fontWeight = FontWeight.Bold, fontSize = (maxW * 0.06f).value.sp)
            Spacer(modifier = Modifier.height(maxH * 0.008f))
            Text("개인 정보를 입력해주세요.", fontWeight = FontWeight.Bold, fontSize = (maxW * 0.06f).value.sp)
            Spacer(modifier = Modifier.height(maxH * 0.01f))

            Text(
                text = when (registerViewModel.step) {
                    RegisterStep.EMAIL -> "*입력한 이메일로 인증코드가 발송됩니다."
                    RegisterStep.DETAILS -> "닉네임은 공백없이 12자 이하\n기호는 _ - 만 사용 가능합니다."
                },
                color = Color.Gray,
                fontSize = (maxW * 0.032f).value.sp,
                modifier = Modifier.padding(start = maxW * 0.01f)
            )

            Spacer(modifier = Modifier.height(maxH * 0.05f))

            if (registerViewModel.step == RegisterStep.EMAIL) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1F3F5), RoundedCornerShape(maxW * 0.02f))
                        .padding(vertical = maxH * 0.005f)
                        .padding(end = maxW * 0.03f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = registerViewModel.email,
                        onValueChange = registerViewModel::onEmailChange,
                        placeholder = { Text("이메일을 입력해주세요.") },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = innerPadding)
                            .background(Color.Transparent),
                        shape = RoundedCornerShape(maxW * 0.02f),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                keyboardController?.hide()  // 👈 키보드 내려감
                            }
                        )
                    )
                    Box(
                        modifier = Modifier
                            .clickable(enabled = isValidEmail(registerViewModel.email) && registerViewModel.isRequestButtonEnabled) {
                                keyboardController?.hide()
                                registerViewModel.onRequestAuthCode()
                            }
                            .wrapContentWidth()
                            .background(
                                if (isValidEmail(registerViewModel.email) && registerViewModel.isRequestButtonEnabled)
                                    Color(0xFFADB5BD) else Color(0xFFDEE2E6),
                                RoundedCornerShape(maxW * 0.015f)
                            )
                            .padding(horizontal = maxW * 0.03f, vertical = maxH * 0.01f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("중복 확인", color = Color.White, fontSize = (maxW * 0.03f).value.sp)
                    }

                }

                Spacer(modifier = Modifier.height(maxH * 0.025f))


                if (registerViewModel.isAuthFieldVisible) {
                    CustomInputField(
                        "인증 코드를 입력해주세요.",
                        maxW,
                        value = registerViewModel.authCode,
                        onValueChange = registerViewModel::onAuthCodeChange
                    )
                    Spacer(modifier = Modifier.height(maxH * 0.025f))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = registerViewModel.nickname,
                        onValueChange = registerViewModel::onNicknameChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("닉네임") },
                        enabled = !registerViewModel.isNicknameValid
                    )

                    Spacer(modifier = Modifier.width(maxW * 0.02f))

                    Box(
                        modifier = Modifier
                            .clickable(
                                enabled = registerViewModel.nickname.isNotBlank()
                                        && !registerViewModel.isNicknameChecking
                                        && !registerViewModel.isNicknameValid
                            ) {
                                keyboardController?.hide()
                                registerViewModel.onCheckNickname()
                            }
                            .background(
                                when {
                                    registerViewModel.isNicknameValid -> Color(0xFF51CF66) // 초록
                                    registerViewModel.nickname.isNotBlank() -> Color(0xFFADB5BD)
                                    else -> Color(0xFFDEE2E6)
                                },
                                RoundedCornerShape(maxW * 0.015f)
                            )
                            .padding(horizontal = maxW * 0.03f, vertical = maxH * 0.01f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (registerViewModel.isNicknameValid) "사용 가능" else "중복 확인",
                            color = Color.White,
                            fontSize = (maxW * 0.03f).value.sp
                        )
                    }
                }


                Spacer(modifier = Modifier.height(maxH * 0.015f))
                CustomInputField("비밀번호", maxW, registerViewModel.password, onValueChange = registerViewModel::onPasswordChange, isPassword = true)
                Spacer(modifier = Modifier.height(maxH * 0.015f))
                CustomInputField("비밀번호 확인", maxW, registerViewModel.confirmPassword, onValueChange = registerViewModel::onConfirmPasswordChange, isPassword = true)
            }

            // 에러 메시지
            registerViewModel.errorMessage?.let {
                Text(
                    text = it,
                    color = Color.Red,
                    fontSize = (maxW * 0.03f).value.sp,
                    modifier = Modifier.padding(top = maxH * 0.01f).padding(start = maxW * 0.01f)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxH * 0.065f)
                    .background(Color(0xFFDED0BB), RoundedCornerShape(maxW * 0.02f))
                    .clickable { registerViewModel.onNextOrSubmit() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (registerViewModel.step == RegisterStep.EMAIL) "다음" else "회원가입",
                    color = Color.White,
                    fontSize = (maxW * 0.04f).value.sp
                )
            }

            Spacer(modifier = Modifier.height(maxH * 0.005f))

            TextButton(onClick = { navController.popBackStack() }) {
                Text("이미 계정이 있으신가요? 로그인", color = Color.Gray, fontSize = (maxW * 0.03f).value.sp)
            }
        }
    }
}

fun isValidEmail(email: String): Boolean {
    return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
}

@Composable
fun CustomInputField(
    hint: String,
    maxW: Dp,
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(hint) },
        shape = RoundedCornerShape(maxW * 0.02f),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF1F3F5), RoundedCornerShape(maxW * 0.02f)),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (isPassword) KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
        else KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                focusManager.clearFocus()
                keyboardController?.hide()
            }
        )
    )
}

