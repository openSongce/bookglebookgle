package com.ssafy.bookglebookgle.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.entity.RegisterStep
import com.ssafy.bookglebookgle.ui.theme.BaseColor
import com.ssafy.bookglebookgle.ui.theme.*
import com.ssafy.bookglebookgle.viewmodel.RegisterViewModel

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun RegisterScreen(navController: NavController, registerViewModel: RegisterViewModel = hiltViewModel()) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // 반응형 디멘션 사용
    val dimensions = rememberResponsiveDimensions()

    LaunchedEffect(registerViewModel.registerSuccess) {
        if (registerViewModel.registerSuccess) {
            navController.navigate("main") {
                popUpTo("register") { inclusive = true }
            }
        }
    }

    LaunchedEffect(registerViewModel.loginFailed) {
        if (registerViewModel.loginFailed) {
            navController.navigate("login") {
                popUpTo("register") { inclusive = true }
            }
            registerViewModel.resetLoginFailed()
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(
                    max = if (dimensions.isTablet) dimensions.contentMaxWidth * 1.5f else Dp.Infinity
                )
                .fillMaxHeight()
                .padding(horizontal = dimensions.defaultPadding) // 좌우 패딩 유지
                .padding(WindowInsets.systemBars.asPaddingValues()),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(dimensions.spacingExtraLarge))
            Spacer(modifier = Modifier.height(dimensions.spacingExtraLarge))

            // 타이틀
            Text(
                "북글북글에서 사용할",
                fontWeight = FontWeight.Bold,
                fontSize = dimensions.textSizeHeadline
            )
            Spacer(modifier = Modifier.height(dimensions.spacingTiny))
            Text(
                "개인 정보를 입력해주세요.",
                fontWeight = FontWeight.Bold,
                fontSize = dimensions.textSizeHeadline
            )
            Spacer(modifier = Modifier.height(dimensions.spacingSmall))

            // 설명 텍스트
            Text(
                text = when (registerViewModel.step) {
                    RegisterStep.EMAIL -> "*입력한 이메일로 인증코드가 발송됩니다."
                    RegisterStep.DETAILS -> "닉네임은 공백없이 12자 이하\n기호는 _ - 만 사용 가능합니다."
                },
                color = Color.Gray,
                fontSize = dimensions.textSizeCaption,
                modifier = Modifier.padding(start = dimensions.spacingTiny)
            )

            Spacer(modifier = Modifier.height(dimensions.spacingExtraLarge))

            // 이메일 단계
            if (registerViewModel.step == RegisterStep.EMAIL) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1F3F5), RoundedCornerShape(dimensions.defaultCornerRadius))
                        .padding(vertical = dimensions.spacingSmall)
                        .padding(end = dimensions.spacingMedium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = registerViewModel.email,
                        onValueChange = registerViewModel::onEmailChange,
                        placeholder = {
                            Text(
                                "이메일을 입력해주세요.",
                                fontSize = dimensions.textSizeBody
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = dimensions.spacingSmall)
                            .background(Color.Transparent),
                        shape = RoundedCornerShape(dimensions.defaultCornerRadius),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = BaseColor,
                            unfocusedPlaceholderColor = Color.Gray.copy(alpha = 0.6f),
                            focusedPlaceholderColor = Color.Gray.copy(alpha = 0.6f)
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = dimensions.textSizeBody
                        )
                    )

                    // 중복 확인 버튼
                    Box(
                        modifier = Modifier
                            .clickable(
                                enabled = isValidEmail(registerViewModel.email) && registerViewModel.isRequestButtonEnabled
                            ) {
                                keyboardController?.hide()
                                registerViewModel.onRequestAuthCode()
                            }
                            .wrapContentWidth()
                            .background(
                                if (isValidEmail(registerViewModel.email) && registerViewModel.isRequestButtonEnabled)
                                    Color(0xFFADB5BD) else Color(0xFFDEE2E6),
                                RoundedCornerShape(dimensions.cornerRadiusSmall)
                            )
                            .padding(
                                horizontal = dimensions.spacingMedium,
                                vertical = dimensions.spacingSmall
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "중복 확인",
                            color = Color.White,
                            fontSize = dimensions.textSizeCaption
                        )
                    }
                }

                Spacer(modifier = Modifier.height(dimensions.spacingLarge))

                // 인증 코드 입력 필드
                if (registerViewModel.isAuthFieldVisible) {
                    CustomInputField(
                        hint = "인증 코드를 입력해주세요.",
                        value = registerViewModel.authCode,
                        onValueChange = registerViewModel::onAuthCodeChange,
                        dimensions = dimensions
                    )
                    Spacer(modifier = Modifier.height(dimensions.spacingLarge))
                }
            } else {
                // 개인정보 입력 단계
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompositionLocalProvider(
                        LocalTextSelectionColors provides TextSelectionColors(
                            handleColor = BaseColor,
                            backgroundColor = BaseColor.copy(alpha = 0.3f)
                        )
                    ) {
                        OutlinedTextField(
                            value = registerViewModel.nickname,
                            onValueChange = registerViewModel::onNicknameChange,
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    "닉네임",
                                    fontSize = dimensions.textSizeBody
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BaseColor,
                                cursorColor = BaseColor
                            ),
                            enabled = !registerViewModel.isNicknameValid,
                            shape = RoundedCornerShape(dimensions.defaultCornerRadius),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = dimensions.textSizeBody
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(dimensions.spacingSmall))

                    // 닉네임 중복 확인 버튼
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
                                    registerViewModel.isNicknameValid -> Color(0xFF51CF66)
                                    registerViewModel.nickname.isNotBlank() -> Color(0xFFADB5BD)
                                    else -> Color(0xFFDEE2E6)
                                },
                                RoundedCornerShape(dimensions.cornerRadiusSmall)
                            )
                            .padding(
                                horizontal = dimensions.spacingMedium,
                                vertical = dimensions.spacingSmall
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (registerViewModel.isNicknameValid) "사용 가능" else "중복 확인",
                            color = Color.White,
                            fontSize = dimensions.textSizeCaption
                        )
                    }
                }

                Spacer(modifier = Modifier.height(dimensions.formSpacing))

                CustomInputField(
                    hint = "비밀번호",
                    value = registerViewModel.password,
                    onValueChange = registerViewModel::onPasswordChange,
                    isPassword = true,
                    dimensions = dimensions
                )

                Spacer(modifier = Modifier.height(dimensions.formSpacing))

                CustomInputField(
                    hint = "비밀번호 확인",
                    value = registerViewModel.confirmPassword,
                    onValueChange = registerViewModel::onConfirmPasswordChange,
                    isPassword = true,
                    dimensions = dimensions
                )
            }

            // 에러 메시지
            registerViewModel.errorMessage?.let {
                Text(
                    text = it,
                    color = Color.Red,
                    fontSize = dimensions.textSizeCaption,
                    modifier = Modifier
                        .padding(top = dimensions.spacingSmall)
                        .padding(start = dimensions.spacingTiny)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 다음/회원가입 버튼
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensions.defaultButtonHeight)
                    .background(Color(0xFFDED0BB), RoundedCornerShape(dimensions.defaultCornerRadius))
                    .clickable { registerViewModel.onNextOrSubmit() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (registerViewModel.step == RegisterStep.EMAIL) "다음" else "회원가입",
                    color = Color.White,
                    fontSize = dimensions.textSizeSubtitle,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(dimensions.spacingTiny))

            // 로그인 링크
            TextButton(onClick = { navController.popBackStack() }) {
                Text(
                    "이미 계정이 있으신가요? 로그인",
                    color = Color.Gray,
                    fontSize = dimensions.textSizeCaption
                )
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
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
    dimensions: ResponsiveDimensions
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var passwordVisible by remember { mutableStateOf(false) }

    CompositionLocalProvider(
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = BaseColor,
            backgroundColor = BaseColor.copy(alpha = 0.3f)
        )
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    hint,
                    fontSize = dimensions.textSizeBody
                )
            },
            shape = RoundedCornerShape(dimensions.defaultCornerRadius),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF1F3F5), RoundedCornerShape(dimensions.defaultCornerRadius)),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                cursorColor = BaseColor
            ),
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (isPassword) KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ) else KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = dimensions.textSizeBody
            ),
            trailingIcon = if (isPassword) {
                {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible }
                    ) {
                        Icon(
                            painterResource(if (passwordVisible) R.drawable.noneye else R.drawable.eye),
                            contentDescription = if (passwordVisible) "비밀번호 숨기기" else "비밀번호 보이기",
                            tint = Color(0xFF8D7E6E),
                            modifier = Modifier.size(dimensions.defaultIconSize)
                        )
                    }
                }
            } else null
        )
    }
}