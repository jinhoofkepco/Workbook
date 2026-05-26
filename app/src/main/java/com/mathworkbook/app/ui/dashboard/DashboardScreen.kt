package com.mathworkbook.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathworkbook.app.core.domain.FinalStatus

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    isMasterMode: Boolean = false,
    refreshKey: Int = 0,
    focusedWorkbookId: String? = null,
    focusedChapterId: String? = null,
    focusedProblemId: String? = null,
    onOpenPracticeChapter: (workbookId: String, chapterId: String) -> Unit = { _, _ -> },
    onOpenMasterProblem: (workbookId: String, chapterId: String, problemId: String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var workbookToDelete by remember { mutableStateOf<WorkbookProgressSummary?>(null) }

    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) viewModel.refresh()
    }

    LaunchedEffect(focusedWorkbookId, focusedChapterId) {
        viewModel.focusLocation(focusedWorkbookId, focusedChapterId)
    }

    Surface(modifier = modifier.fillMaxSize(), color = Color(0xFFF7F8FA)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.message?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            if (state.selectedWorkbookId == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(48.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("문제집 선택", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            if (isMasterMode) "문제집을 길게 누르면 삭제할 수 있습니다." else "문제집을 골라 진도를 확인합니다.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    gridItems(state.workbookSummaries) { summary ->
                        WorkbookCard(
                            summary = summary,
                            isMasterMode = isMasterMode,
                            onClick = { viewModel.selectWorkbook(summary.workbook.workbookId) },
                            onLongClick = if (isMasterMode) {
                                { workbookToDelete = summary }
                            } else {
                                null
                            }
                        )
                    }
                }
            } else {
                val selectedSummary = state.workbookSummaries.firstOrNull {
                    it.workbook.workbookId == state.selectedWorkbookId
                }
                selectedSummary?.let { summary ->
                    SelectedWorkbookHeader(
                        summary = summary,
                        onClickBook = viewModel::clearSelectedWorkbook,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.2f)
                            .padding(start = 48.dp)
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.8f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listItems(state.chapterSummaries) { summary ->
                        val isFocusedChapter = summary.chapter.chapterId == focusedChapterId
                        ChapterRow(
                            summary = summary,
                            isMasterMode = isMasterMode,
                            expanded = state.selectedChapterId == summary.chapter.chapterId,
                            highlighted = isFocusedChapter,
                            onClick = {
                                if (isMasterMode) {
                                    viewModel.toggleChapterDetails(summary.chapter.chapterId)
                                } else {
                                    onOpenPracticeChapter(summary.chapter.workbookId, summary.chapter.chapterId)
                                }
                            }
                        )
                        if (isMasterMode && state.selectedChapterId == summary.chapter.chapterId) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 18.dp)) {
                                state.problemSummaries.forEach { problemSummary ->
                                    ProblemRow(
                                        summary = problemSummary,
                                        highlighted = problemSummary.problem.problemId == focusedProblemId,
                                        onClick = {
                                            onOpenMasterProblem(
                                                problemSummary.problem.workbookId,
                                                problemSummary.problem.chapterId,
                                                problemSummary.problem.problemId
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    workbookToDelete?.let { summary ->
        AlertDialog(
            onDismissRequest = { workbookToDelete = null },
            title = { Text("책 삭제") },
            text = {
                Text("'${summary.workbook.title}' 문제집과 포함된 문제, 풀이 기록, 시험 기록을 삭제합니다. 삭제 후에는 되돌릴 수 없습니다.")
            },
            dismissButton = {
                OutlinedButton(onClick = { workbookToDelete = null }) {
                    Text("취소")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteWorkbook(summary.workbook)
                        workbookToDelete = null
                    }
                ) {
                    Text("삭제")
                }
            }
        )
    }
}

@Composable
private fun SelectedWorkbookHeader(
    summary: WorkbookProgressSummary,
    onClickBook: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            modifier = Modifier
                .weight(0.22f)
                .fillMaxSize()
                .clickable(onClick = onClickBook),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE5E7EB))
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("책", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Text(summary.workbook.title, maxLines = 2, fontWeight = FontWeight.Bold)
            }
        }
        Card(
            modifier = Modifier
                .weight(0.78f)
                .fillMaxSize(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("현황", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                ProgressBar(summary.progressPercent)
                Text("${summary.solvedProblems}/${summary.totalProblems} 완료 · ${summary.progressPercent}%")
                CompactStats(summary.correctProblems, summary.wrongProblems, summary.attemptCount)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkbookCard(
    summary: WorkbookProgressSummary,
    isMasterMode: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("책", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text(summary.workbook.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(summary.workbook.description.ifBlank { "문제 ${summary.totalProblems}개" }, maxLines = 2)
            ProgressBar(summary.progressPercent)
            Text("${summary.solvedProblems}/${summary.totalProblems} 완료 · ${summary.progressPercent}%")
            if (isMasterMode) {
                CompactStats(summary.correctProblems, summary.wrongProblems, summary.attemptCount)
            }
        }
    }
}

@Composable
private fun ChapterRow(
    summary: ChapterProgressSummary,
    isMasterMode: Boolean,
    expanded: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (highlighted) Color(0xFFEFF6FF) else Color.White),
        border = if (highlighted) BorderStroke(2.dp, Color(0xFF2563EB)) else null
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${summary.chapter.orderIndex}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(summary.chapter.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("${summary.solvedProblems}/${summary.totalProblems} · ${summary.progressPercent}%")
                    if (isMasterMode) Text(if (expanded) "접기" else "문제 보기")
                }
                ProgressBar(summary.progressPercent)
                if (isMasterMode) CompactStats(summary.correctProblems, summary.wrongProblems, summary.attemptCount)
            }
        }
    }
}

@Composable
private fun ProblemRow(
    summary: ProblemProgressSummary,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (highlighted) Color(0xFFEFF6FF) else Color(0xFFFBFBFC)),
        border = if (highlighted) BorderStroke(2.dp, Color(0xFF2563EB)) else null
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${summary.problem.orderIndex}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    summary.problem.questionText?.take(36) ?: summary.problem.problemId,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1
                )
                Text(problemStatusLabel(summary), color = problemStatusColor(summary), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CompactStats(correct: Int, wrong: Int, attempts: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        StatPill("정답 $correct", Color(0xFFE8F5E9), Color(0xFF15803D), Modifier.weight(1f))
        StatPill("오답 $wrong", Color(0xFFFEE2E2), Color(0xFFB91C1C), Modifier.weight(1f))
        StatPill("풀이 $attempts", Color(0xFFEFF6FF), Color(0xFF1D4ED8), Modifier.weight(1f))
    }
}

@Composable
private fun StatPill(label: String, background: Color, foreground: Color, modifier: Modifier = Modifier) {
    Text(
        text = label,
        modifier = modifier
            .background(background, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        color = foreground,
        maxLines = 1,
        style = MaterialTheme.typography.labelMedium
    )
}

@Composable
private fun ProgressBar(percent: Int) {
    LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
}

private fun problemStatusLabel(summary: ProblemProgressSummary): String {
    return when {
        summary.isCorrect == true -> "정답"
        summary.isCorrect == false || summary.maxAttemptsReached -> "오답"
        summary.latestStatus == FinalStatus.IN_PROGRESS -> "진행중"
        else -> "미풀이"
    }
}

private fun problemStatusColor(summary: ProblemProgressSummary): Color {
    return when (problemStatusLabel(summary)) {
        "정답" -> Color(0xFF15803D)
        "오답" -> Color(0xFFB91C1C)
        "진행중" -> Color(0xFF374151)
        else -> Color(0xFF6B7280)
    }
}
