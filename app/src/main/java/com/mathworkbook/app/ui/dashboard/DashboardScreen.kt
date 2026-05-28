package com.mathworkbook.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathworkbook.app.core.domain.FinalStatus
import com.mathworkbook.app.ui.skin.LocalWorkbookSkin
import com.mathworkbook.app.ui.skin.SkinAssetImage

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

    Surface(modifier = modifier.fillMaxSize().statusBarsPadding(), color = Color(0xFFF7F8FA)) {
        Box(modifier = Modifier.fillMaxSize()) {
            SkinAssetImage(
                assetKey = "dashboardBackground",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.82f
            )
            if (state.selectedWorkbookId == null) {
                DashboardPageFrame()
            }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            state.message?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            if (state.selectedWorkbookId == null) {
                DashboardTitleHeader(isMasterMode = isMasterMode)
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalArrangement = Arrangement.spacedBy(42.dp)
                    ) {
                        gridItems(state.workbookSummaries) { summary ->
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                WorkbookCard(
                                    summary = summary,
                                    isMasterMode = isMasterMode,
                                    onClick = { viewModel.selectWorkbook(summary.workbook.workbookId) },
                                    onLongClick = if (isMasterMode) {
                                        { workbookToDelete = summary }
                                    } else {
                                        null
                                    },
                                    modifier = Modifier
                                        .width(if (isMasterMode) 214.dp else 198.dp)
                                        .aspectRatio(0.72f)
                                )
                            }
                        }
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
private fun DashboardTitleHeader(isMasterMode: Boolean) {
    val titleAssetKey = if (LocalWorkbookSkin.current?.assetPath("dashboardTitleFrame") != null) {
        "dashboardTitleFrame"
    } else {
        "dashboardTitleBanner"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(620.dp)
                .height(92.dp)
                .background(Color(0xBFFFFFFF), RoundedCornerShape(18.dp))
                .border(1.dp, Color(0x33A78BFA), RoundedCornerShape(18.dp))
        )
        SkinAssetImage(
            assetKey = titleAssetKey,
            modifier = Modifier
                .width(680.dp)
                .height(106.dp),
            contentScale = ContentScale.FillBounds,
            alpha = 0.98f
        )
        Column(
            modifier = Modifier
                .width(620.dp)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "박서아의 문제집들",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (isMasterMode) {
                Text(
                    "문제집을 길게 누르면 삭제할 수 있습니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun BoxScope.DashboardPageFrame() {
    SkinAssetImage(
        assetKey = "dashboardPageFrame",
        modifier = Modifier.matchParentSize(),
        contentScale = ContentScale.FillBounds,
        alpha = 0.94f
    )
    Box(
        modifier = Modifier
            .matchParentSize()
            .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 72.dp)
            .border(1.dp, Color(0x22A16207), RoundedCornerShape(28.dp))
    )
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
        WorkbookCoverView(
            summary = summary,
            compact = true,
            showStats = false,
            modifier = Modifier
                .weight(0.22f)
                .fillMaxSize()
                .clickable(onClick = onClickBook)
        )
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
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    WorkbookCoverView(
        summary = summary,
        compact = false,
        showStats = isMasterMode,
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    )
}

@Composable
private fun WorkbookCoverView(
    summary: WorkbookProgressSummary,
    compact: Boolean,
    showStats: Boolean,
    modifier: Modifier = Modifier
) {
    val coverShape = RoundedCornerShape(8.dp)
    val activeSkin = LocalWorkbookSkin.current
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF7EC)),
        border = BorderStroke(1.dp, Color(0xFFD8C6A3)),
        shape = coverShape
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFDF7EC))
                .clip(coverShape)
        ) {
            SkinAssetImage(
                assetKey = "bookCoverBase",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(if (compact) 18.dp else 24.dp)
                    .background(Color(0xFF6E7FA7))
                    .border(1.dp, Color(0xFF4D5D85))
            ) {
                if (activeSkin?.assetPath("bookCoverSpine") != null) {
                    SkinAssetImage(
                        assetKey = "bookCoverSpine",
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(if (compact) 6.dp else 10.dp)
                    .background(Color(0xFFEAF1FF), RoundedCornerShape(999.dp))
                    .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 3.dp)
            ) {
                Text(
                    "${summary.progressPercent}%",
                    color = Color(0xFF1D4ED8),
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (compact) 28.dp else 38.dp,
                        top = if (compact) 10.dp else 18.dp,
                        end = if (compact) 8.dp else 14.dp,
                        bottom = if (compact) 10.dp else 14.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 9.dp)
            ) {
                Text(
                    "문제집",
                    color = Color(0xFF6E7FA7),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    summary.workbook.title,
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (compact) 2 else 2
                )
                Text(
                    summary.workbook.description.ifBlank { "문제 ${summary.totalProblems}개" },
                    color = Color(0xFF6B7280),
                    maxLines = if (compact) 1 else 2,
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium
                )
                ProgressBar(summary.progressPercent)
                Text(
                    "${summary.solvedProblems}/${summary.totalProblems} 완료 · ${summary.progressPercent}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF374151)
                )
                if (showStats) {
                    CompactStats(summary.correctProblems, summary.wrongProblems, summary.attemptCount)
                }
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
        Box(modifier = Modifier.fillMaxWidth()) {
            SkinAssetImage(
                assetKey = "chapterRowTab",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds,
                alpha = 0.24f
            )
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
