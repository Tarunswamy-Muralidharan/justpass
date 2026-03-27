package com.example.attendancewidgetlaudea.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.attendancewidgetlaudea.ui.components.BackgroundVariant
import com.example.attendancewidgetlaudea.ui.components.GlassListCard
import com.example.attendancewidgetlaudea.ui.components.LiquidGlassScaffold
import com.example.attendancewidgetlaudea.ui.viewmodel.LoginViewModel

@Composable
fun LoginScreen(viewModel: LoginViewModel = viewModel(), onLoginSuccess: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }

    // Typing animation for roll number placeholder
    val typingTexts = listOf("71762133XX", "71762104XX", "71551XXXXX")
    var currentTextIndex by remember { mutableIntStateOf(0) }
    var displayedChars by remember { mutableIntStateOf(0) }
    var isDeleting by remember { mutableStateOf(false) }
    val currentFullText = typingTexts[currentTextIndex]

    LaunchedEffect(currentTextIndex) {
        // Type forward
        displayedChars = 0
        isDeleting = false
        for (i in 1..currentFullText.length) {
            displayedChars = i
            delay(80L + (0..40L).random()) // Variable typing speed
        }
        delay(1500) // Pause when fully typed
        // Delete backward
        isDeleting = true
        for (i in currentFullText.length downTo 0) {
            displayedChars = i
            delay(40L)
        }
        delay(300)
        // Move to next text
        currentTextIndex = (currentTextIndex + 1) % typingTexts.size
    }

    val animatedPlaceholder = currentFullText.take(displayedChars)

    // Blinking cursor
    val cursorVisible by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "blink"
    )

    LaunchedEffect(uiState.isLoggedIn) { if (uiState.isLoggedIn) onLoginSuccess() }

    LiquidGlassScaffold(variant = BackgroundVariant.Login) { _ ->
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Text("Laudea Attendance", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text("PSG iTech", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(48.dp))

            GlassListCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    OutlinedTextField(value = uiState.rollNumber, onValueChange = { viewModel.updateRollNumber(it) },
                        label = { Text("Roll Number") },
                        placeholder = {
                            Row {
                                Text(
                                    animatedPlaceholder,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                if (uiState.rollNumber.isEmpty()) {
                                    Text(
                                        "▌",
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = cursorVisible * 0.7f)
                                    )
                                }
                            }
                        },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        enabled = !uiState.isLoading)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = uiState.password, onValueChange = { viewModel.updatePassword(it) },
                        label = { Text("Password") }, placeholder = { Text("Enter your password") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); viewModel.login() }),
                        trailingIcon = { TextButton(onClick = { passwordVisible = !passwordVisible }) { Text(if (passwordVisible) "Hide" else "Show") } },
                        enabled = !uiState.isLoading)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { viewModel.login() }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !uiState.isLoading) {
                        if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        else Text("Login", fontSize = 16.sp)
                    }
                }
            }

            uiState.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                GlassListCard(modifier = Modifier.fillMaxWidth(), tintColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)) {
                    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp), textAlign = TextAlign.Center)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text("Use your PSG iTech credentials to login", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.weight(1f))
            Text("built with love for PSG iTech", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 16.dp))
        }
    }
}
