package com.mathworkbook.app.ui.gpt

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mathworkbook.app.core.gpt.WorkbookGptGateway
import java.io.File
import kotlinx.coroutines.launch

data class GptProblemContext(
    val problemId: String,
    val workbookTitle: String,
    val chapterTitle: String,
    val problemPosition: String,
    val questionText: String,
    val imagePath: String?,
    val fieldsSummary: String,
    val choicesSummary: String,
    val currentAnswer: String,
    val storedAnswer: String,
    val teacherNotes: String,
    val hintText: String
)

fun GptProblemContext.toPrompt(userPrompt: String): String {
    return buildString {
        appendLine(userPrompt.ifBlank { DefaultGptPrompt })
        appendLine()
        appendLine("[현재 문제]")
        appendLine("문제 ID: $problemId")
        appendLine("문제집: $workbookTitle")
        appendLine("단원: $chapterTitle")
        appendLine("위치: $problemPosition")
        if (questionText.isNotBlank()) {
            appendLine()
            appendLine("문제 글:")
            appendLine(questionText)
        }
        if (!imagePath.isNullOrBlank()) {
            appendLine()
            appendLine("문제 이미지를 함께 첨부했습니다. 이미지가 보이면 이미지 내용을 우선 기준으로 확인해 주세요.")
        }
        if (fieldsSummary.isNotBlank()) {
            appendLine()
            appendLine("답칸:")
            appendLine(fieldsSummary)
        }
        if (choicesSummary.isNotBlank()) {
            appendLine()
            appendLine("보기:")
            appendLine(choicesSummary)
        }
        if (currentAnswer.isNotBlank()) {
            appendLine()
            appendLine("현재 입력/표시된 답:")
            appendLine(currentAnswer)
        }
        if (storedAnswer.isNotBlank()) {
            appendLine()
            appendLine("앱에 저장된 정답:")
            appendLine(storedAnswer)
        }
        if (teacherNotes.isNotBlank()) {
            appendLine()
            appendLine("교사용 메모/풀이:")
            appendLine(teacherNotes)
        }
        if (hintText.isNotBlank()) {
            appendLine()
            appendLine("힌트:")
            appendLine(hintText)
        }
    }
}

const val DefaultGptPrompt =
    "현재 수학 문제를 검토해 주세요. 첨부된 문제 이미지가 있으면 이미지 내용을 우선 확인하고, 필요한 경우 정답과 짧은 풀이를 한국어로 설명해 주세요. 답칸의 displayPrefix/displaySuffix는 학생이 직접 입력하지 않는 표시 단위이며, showPrefixInInput/showSuffixInInput이 true이면 입력칸에도 보이는 단위입니다."

@Composable
fun GptAssistPanel(
    gateway: WorkbookGptGateway,
    prompt: String,
    problemContext: GptProblemContext?,
    sendNonce: Int,
    onSaveExplanation: (prompt: String, explanationText: String, explanationHtml: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status by gateway.status.collectAsState()
    val scope = rememberCoroutineScope()
    var saveStatus by remember { mutableStateOf("") }
    Card(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("GPT", fontWeight = FontWeight.Bold)
                    Text(
                        listOf(status, saveStatus).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4B5563)
                    )
                }
                OutlinedButton(
                    enabled = problemContext != null,
                    onClick = {
                        scope.launch {
                            val text = gateway.captureLatestAssistantText()
                            if (text.isNullOrBlank()) {
                                saveStatus = "저장할 답변을 찾지 못했습니다"
                            } else {
                                val html = gateway.captureLatestAssistantHtml().orEmpty()
                                onSaveExplanation(prompt, text, html)
                                saveStatus = "설명 저장됨"
                            }
                        }
                    }
                ) {
                    Text("설명 저장")
                }
                OutlinedButton(onClick = onDismiss) {
                    Text("닫기")
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        gateway.provideView().also { view ->
                            (view.parent as? ViewGroup)?.removeView(view)
                        }
                    }
                )
                if (problemContext == null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(12.dp),
                        color = Color(0xFFFFF7ED),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "문제를 연 뒤 g를 누르면 현재 문제가 전송됩니다.",
                            modifier = Modifier.padding(10.dp),
                            color = Color(0xFF9A3412)
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(sendNonce, problemContext?.problemId) {
        if (sendNonce <= 0 || problemContext == null) return@LaunchedEffect
        gateway.sendProblem(
            prompt = problemContext.toPrompt(prompt),
            imagePath = problemContext.imagePath?.takeIf { File(it).exists() }
        )
    }
}

@Composable
fun GptPromptDialog(
    initialPrompt: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val context = LocalContext.current
    var prompt by remember(initialPrompt) { mutableStateOf(initialPrompt.ifBlank { DefaultGptPrompt }) }
    var speechStatus by remember { mutableStateOf("") }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startPromptSpeechRecognition(
                context = context,
                setStatus = { speechStatus = it },
                setText = { text -> prompt = text },
                replaceRecognizer = { next ->
                    recognizer?.destroy()
                    recognizer = next
                }
            )
        } else {
            speechStatus = "마이크 권한이 필요합니다."
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recognizer?.destroy()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GPT 프롬프트") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    minLines = 7,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("g 버튼으로 보낼 기본 지시문") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                                speechStatus = "음성 인식 서비스를 찾지 못했습니다."
                                return@OutlinedButton
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                            ) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                startPromptSpeechRecognition(
                                    context = context,
                                    setStatus = { speechStatus = it },
                                    setText = { text -> prompt = text },
                                    replaceRecognizer = { next ->
                                        recognizer?.destroy()
                                        recognizer = next
                                    }
                                )
                            }
                        }
                    ) {
                        Text("음성")
                    }
                    OutlinedButton(onClick = { prompt = DefaultGptPrompt }) {
                        Text("기본값")
                    }
                }
                if (speechStatus.isNotBlank()) {
                    Text(speechStatus, style = MaterialTheme.typography.labelSmall, color = Color(0xFF4B5563))
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("취소")
            }
        },
        confirmButton = {
            Button(onClick = { onSave(prompt) }) {
                Text("저장")
            }
        }
    )
}

private fun startPromptSpeechRecognition(
    context: Context,
    setStatus: (String) -> Unit,
    setText: (String) -> Unit,
    replaceRecognizer: (SpeechRecognizer) -> Unit
) {
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    recognizer.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) {
            setStatus("듣고 있습니다.")
        }

        override fun onBeginningOfSpeech() {
            setStatus("말하는 중입니다.")
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            setStatus("텍스트로 바꾸는 중입니다.")
        }

        override fun onError(error: Int) {
            setStatus(speechErrorMessage(error))
        }

        override fun onResults(results: android.os.Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                setText(matches.first())
                setStatus("입력됐습니다.")
            } else {
                setStatus("다시 말해 주세요.")
            }
        }

        override fun onPartialResults(partialResults: android.os.Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                setStatus(matches.first())
            }
        }

        override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
    })
    replaceRecognizer(recognizer)
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
        putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "GPT 프롬프트")
    }
    recognizer.startListening(intent)
}

private fun speechErrorMessage(error: Int): String {
    return when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "말을 인식하지 못했습니다."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "말소리가 들리지 않았습니다."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크를 확인해 주세요."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 필요합니다."
        else -> "다시 눌러 말해 주세요."
    }
}
