package com.mathworkbook.app.ui.components

import com.mathworkbook.app.core.database.ProblemEntity
import org.json.JSONObject

data class ProblemTeacherNotes(
    val solutionText: String? = null,
    val teacherMemo: String? = null,
    val answerNote: String? = null,
    val gradingMode: String? = null,
    val expectedSummary: String? = null
) {
    fun isEmpty(): Boolean = solutionText.isNullOrBlank() &&
        teacherMemo.isNullOrBlank() &&
        answerNote.isNullOrBlank() &&
        gradingMode.isNullOrBlank() &&
        expectedSummary.isNullOrBlank()
}

fun parseProblemTeacherNotes(problem: ProblemEntity?): ProblemTeacherNotes {
    val json = problem?.imageCropRectJson ?: return ProblemTeacherNotes()
    return runCatching {
        val root = JSONObject(json)
        val policy = root.optJSONObject("gradingPolicy")
        ProblemTeacherNotes(
            solutionText = root.optString("solutionText").ifBlank { null },
            teacherMemo = root.optString("teacherMemo").ifBlank { null },
            answerNote = root.optString("answerNote").ifBlank { null },
            gradingMode = policy?.optString("mode")?.ifBlank { null },
            expectedSummary = policy?.expectedSummary()
        )
    }.getOrDefault(ProblemTeacherNotes())
}

private fun JSONObject.expectedSummary(): String? {
    val parts = buildList {
        if (has("expectedValue")) add("기준값: ${opt("expectedValue")}")
        optJSONObject("expectedValues")?.let { values ->
            add("기준값: ${values.toKeyValueText()}")
        }
        optJSONObject("expectedSymbols")?.let { symbols ->
            add("기준 그림: ${symbols.toKeyValueText()}")
        }
    }
    return parts.joinToString(" / ").ifBlank { null }
}

private fun JSONObject.toKeyValueText(): String {
    val keys = keys()
    val parts = mutableListOf<String>()
    while (keys.hasNext()) {
        val key = keys.next()
        parts += "$key ${opt(key)}"
    }
    return parts.joinToString(", ")
}
