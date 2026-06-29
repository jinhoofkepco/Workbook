package com.mathworkbook.app.ui.exam

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType as ImeKeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathworkbook.app.core.domain.AnswerFieldType
import com.mathworkbook.app.core.domain.ProblemType
import com.mathworkbook.app.ui.components.HandwritingCanvas
import com.mathworkbook.app.ui.components.HandwritingState
import com.mathworkbook.app.ui.components.ProblemWorksheetBackground
import com.mathworkbook.app.ui.components.estimateWorksheetContentHeightDp
import com.mathworkbook.app.ui.components.rememberHandwritingState
import org.json.JSONObject

@Composable
fun ExamScreen(
    viewModel: ExamViewModel,
    isMasterMode: Boolean = false,
    questionTextSizeSp: Int = 24,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val handwritingState = rememberHandwritingState()

    LaunchedEffect(state.currentProblem?.problemId) {
        handwritingState.clear()
    }

    Surface(modifier = modifier.fillMaxSize(), color = Color(0xFFF7F8FA)) {
        if (state.loading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            when (state.mode) {
                ExamMode.TAKING -> TakingScreen(state, viewModel, handwritingState, isMasterMode, questionTextSizeSp)
                ExamMode.REVIEW -> ReviewBeforeSubmitScreen(state, viewModel)
                ExamMode.RESULT -> ExamResultScreen(state)
            }
        }
    }
}

@Composable
private fun TakingScreen(
    state: ExamUiState,
    viewModel: ExamViewModel,
    handwritingState: HandwritingState,
    isMasterMode: Boolean,
    questionTextSizeSp: Int
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1.5f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.examTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("${state.currentIndex + 1}/${state.problems.size}")
                }
                Text(if (isMasterMode) "마스터 · 시험" else "학생 · 시험", color = MaterialTheme.colorScheme.primary)
                OutlinedButton(onClick = { viewModel.toggleStar() }) {
                    Text(if (state.starredProblemIds.contains(state.currentProblem?.problemId)) "★" else "☆")
                }
            }

            Text("풀이 과정", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            HandwritingCanvas(
                state = handwritingState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentHeight = estimateWorksheetContentHeightDp(state.currentProblem).dp,
                backgroundContent = {
                    ProblemWorksheetBackground(
                        problem = state.currentProblem,
                        questionTextSizeSp = questionTextSizeSp
                    )
                }
            )
        }

        Column(
            modifier = Modifier
                .width(380.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("문제 이동", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ExamProblemNavigator(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.weight(1f)
            )
            Text(if (isMasterMode) "제출 답안" else "답안 입력", style = MaterialTheme.typography.titleMedium)
            ExamAnswerInputs(state = state, viewModel = viewModel, readOnly = isMasterMode)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        viewModel.saveCurrentSolution(handwritingState.toVectorJson())
                        viewModel.movePrevious()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("이전")
                }
                OutlinedButton(
                    onClick = {
                        viewModel.saveCurrentSolution(handwritingState.toVectorJson())
                        viewModel.goReview()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("검토")
                }
                Button(
                    onClick = {
                        viewModel.saveCurrentSolution(handwritingState.toVectorJson())
                        viewModel.moveNext()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("다음")
                }
            }
        }
    }
}

@Composable
private fun ExamProblemNavigator(
    state: ExamUiState,
    viewModel: ExamViewModel,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(state.problems) { index, problem ->
            val answered = state.answers
                .filterKeys { it.startsWith("${problem.problemId}:") }
                .values
                .any { it.isNotBlank() }
            val starred = state.starredProblemIds.contains(problem.problemId)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.moveToProblem(index) },
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        index == state.currentIndex -> MaterialTheme.colorScheme.primaryContainer
                        answered -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${index + 1}", fontWeight = FontWeight.Bold)
                    Text(if (answered) "풀이 완료" else "미입력", modifier = Modifier.weight(1f), maxLines = 1)
                    Text(if (starred) "★" else "")
                }
            }
        }
    }
}

@Composable
private fun ExamAnswerInputs(state: ExamUiState, viewModel: ExamViewModel, readOnly: Boolean) {
    if (state.currentProblem?.problemType == ProblemType.MULTIPLE_CHOICE) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.choices.forEach { choice ->
                FilterChip(
                    selected = state.selectedChoiceIds.contains(choice.choiceId),
                    onClick = { if (!readOnly) viewModel.toggleChoice(choice.choiceId) },
                    label = { Text(choice.choiceText) }
                )
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.fields.filterNot { it.fieldType.isWorksheetOnlyField() }.forEach { field ->
            val inputPrefix = answerFieldInputPrefix(field.positionJson)
            val inputSuffix = answerFieldInputSuffix(field.positionJson)
            OutlinedTextField(
                value = state.answers["${state.currentProblem?.problemId}:${field.answerFieldId}"].orEmpty(),
                onValueChange = { if (!readOnly) viewModel.updateInput(field.answerFieldId, it) },
                label = { Text(field.label) },
                readOnly = readOnly,
                prefix = inputPrefix.takeIf { it.isNotBlank() }?.let { text -> { Text(text) } },
                suffix = inputSuffix.takeIf { it.isNotBlank() }?.let { text -> { Text(text) } },
                keyboardOptions = keyboardOptionsFor(field.fieldType),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun keyboardOptionsFor(fieldType: AnswerFieldType): KeyboardOptions {
    val keyboardType = when (fieldType) {
        AnswerFieldType.NUMBER,
        AnswerFieldType.MONEY,
        AnswerFieldType.ANGLE -> ImeKeyboardType.Decimal
        else -> ImeKeyboardType.Text
    }
    return KeyboardOptions(keyboardType = keyboardType)
}

private fun AnswerFieldType.isWorksheetOnlyField(): Boolean {
    return this == AnswerFieldType.DRAWING || this == AnswerFieldType.TABLE
}

private fun answerFieldInputPrefix(positionJson: String?): String {
    return answerFieldMeta(positionJson)
        ?.inputAffixForInput("showPrefixInInput", "inputPrefix", "displayPrefix", "prefix")
        .orEmpty()
}

private fun answerFieldInputSuffix(positionJson: String?): String {
    return answerFieldMeta(positionJson)
        ?.inputAffixForInput("showSuffixInInput", "inputSuffix", "displaySuffix", "suffix")
        .orEmpty()
}

private fun answerFieldMeta(positionJson: String?): JSONObject? {
    if (positionJson.isNullOrBlank()) return null
    return runCatching { JSONObject(positionJson) }.getOrNull()
}

private fun JSONObject.inputAffixForInput(
    showKey: String,
    inputKey: String,
    displayKey: String,
    fallbackKey: String
): String {
    if (!optBoolean(showKey, false) && !optBoolean("showAffixInInput", false)) return ""
    return optString(inputKey)
        .ifBlank { optString(displayKey) }
        .ifBlank { optString(fallbackKey) }
}

@Composable
private fun ReviewBeforeSubmitScreen(state: ExamUiState, viewModel: ExamViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("제출 전 검토", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.problems) { index, problem ->
                val fields = state.answers.filterKeys { it.startsWith("${problem.problemId}:") }
                val answered = fields.values.any { it.isNotBlank() }
                val starred = state.starredProblemIds.contains(problem.problemId)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.moveToProblem(index) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (answered) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${index + 1}. ${if (starred) "★ " else ""}${problem.questionText.orEmpty()}")
                        Text(if (answered) "답안 입력됨" else "미입력")
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.moveToProblem(0) }, modifier = Modifier.weight(1f)) {
                Text("수정하기")
            }
            Button(onClick = viewModel::submitFinal, modifier = Modifier.weight(1f)) {
                Text("최종 제출")
            }
        }
    }
}

@Composable
private fun ExamResultScreen(state: ExamUiState) {
    val result = state.result
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("시험 결과", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("점수 ${"%.1f".format(result?.score ?: 0.0)}점")
        Text("정답 ${result?.correctCount ?: 0}개")
        Text("오답 ${result?.wrongCount ?: 0}개")
        Text("미입력 ${result?.blankCount ?: 0}개")
    }
}
