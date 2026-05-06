package com.justpass.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.justpass.app.ui.components.GlassListCard
import com.justpass.app.ui.viewmodel.OtpStep
import com.justpass.app.ui.viewmodel.TournamentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTournamentScreen(
    onBack: () -> Unit,
    viewModel: TournamentViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        (context as? android.app.Activity)?.let { viewModel.bindActivity(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Tournament", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (state.otpStep) {
                OtpStep.SUBMITTED -> SubmittedCard(onDone = onBack)
                else -> CreateForm(state, viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateForm(
    state: com.justpass.app.ui.viewmodel.TournamentUiState,
    viewModel: TournamentViewModel
) {
    GlassListCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EmojiEvents, null, Modifier.size(20.dp), tint = Color(0xFFFFD700))
                Spacer(Modifier.width(8.dp))
                Text("Your details (auto-filled)", fontWeight = FontWeight.Bold)
            }
            ReadOnlyRow("Name", state.myName.ifBlank { "—" })
            ReadOnlyRow("Roll", state.myRoll.ifBlank { "—" })
            ReadOnlyRow("Department", state.myDept.ifBlank { "—" })
        }
    }

    OutlinedTextField(
        value = state.tournamentName,
        onValueChange = viewModel::setTournamentName,
        label = { Text("Tournament name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    var formatExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = formatExpanded,
        onExpandedChange = { formatExpanded = !formatExpanded }
    ) {
        OutlinedTextField(
            value = state.format,
            onValueChange = {},
            readOnly = true,
            label = { Text("Format") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(formatExpanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = formatExpanded, onDismissRequest = { formatExpanded = false }) {
            listOf("Bullet", "Blitz", "Rapid", "Classical").forEach { f ->
                DropdownMenuItem(text = { Text(f) }, onClick = {
                    viewModel.setFormat(f); formatExpanded = false
                })
            }
        }
    }

    var pExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = pExpanded, onExpandedChange = { pExpanded = !pExpanded }) {
        OutlinedTextField(
            value = state.maxParticipants.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Max participants") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(pExpanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = pExpanded, onDismissRequest = { pExpanded = false }) {
            listOf(8, 16, 32).forEach { n ->
                DropdownMenuItem(text = { Text("$n") }, onClick = {
                    viewModel.setMaxParticipants(n); pExpanded = false
                })
            }
        }
    }

    OutlinedTextField(
        value = state.description,
        onValueChange = viewModel::setDescription,
        label = { Text("Description (optional)") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2, maxLines = 4
    )

    OutlinedTextField(
        value = state.phone,
        onValueChange = viewModel::setPhone,
        label = { Text("Phone number") },
        placeholder = { Text("10-digit IN or +<cc><number>") },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        enabled = state.otpStep != OtpStep.OTP_SENT && state.otpStep != OtpStep.VERIFYING
    )

    if (state.otpStep == OtpStep.OTP_SENT || state.otpStep == OtpStep.VERIFYING) {
        OutlinedTextField(
            value = state.otpInput,
            onValueChange = viewModel::setOtpInput,
            label = { Text("OTP (6 digits)") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }

    state.errorMessage?.let { msg ->
        Text(msg, color = Color(0xFFFF5252), fontSize = 13.sp)
    }

    when (state.otpStep) {
        OtpStep.IDLE, OtpStep.FAILED -> {
            Button(
                onClick = viewModel::sendOtp,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.tournamentName.isNotBlank() && state.phone.length >= 10
            ) {
                Text("Send OTP")
            }
        }
        OtpStep.SENDING -> {
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Sending OTP…")
            }
        }
        OtpStep.OTP_SENT -> {
            Button(
                onClick = viewModel::verifyOtp,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.otpInput.length == 6
            ) {
                Text("Verify & submit")
            }
        }
        OtpStep.VERIFYING, OtpStep.SUBMITTING -> {
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(if (state.otpStep == OtpStep.VERIFYING) "Verifying…" else "Submitting…")
            }
        }
        OtpStep.VERIFIED, OtpStep.SUBMITTED -> { /* handled by SubmittedCard */ }
    }
}

@Composable
private fun ReadOnlyRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp, color = Color(0xFF90A4AE), modifier = Modifier.width(100.dp))
        Text(value, fontSize = 13.sp, color = Color.White)
    }
}

@Composable
private fun SubmittedCard(onDone: () -> Unit) {
    GlassListCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.CheckCircle, null, Modifier.size(48.dp), tint = Color(0xFF00E676))
            Text("Request submitted!", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "Your tournament request is awaiting admin approval. You'll see it appear in the chess lobby once approved.",
                fontSize = 13.sp, color = Color(0xFFB0BEC5)
            )
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Back to Chess")
            }
        }
    }
}
