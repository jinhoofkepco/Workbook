package com.mathworkbook.app.core.files

import android.content.Context
import android.net.Uri
import com.mathworkbook.app.core.database.AnswerFieldEntity
import com.mathworkbook.app.core.database.AnswerRuleEntity
import com.mathworkbook.app.core.database.ChapterEntity
import com.mathworkbook.app.core.database.ChoiceEntity
import com.mathworkbook.app.core.database.MathDao
import com.mathworkbook.app.core.database.ProblemEntity
import com.mathworkbook.app.core.database.ProblemTemplateEntity
import com.mathworkbook.app.core.database.WorkbookEntity
import com.mathworkbook.app.core.domain.AnswerFieldType
import com.mathworkbook.app.core.domain.AnswerType
import com.mathworkbook.app.core.domain.ProblemType
import com.mathworkbook.app.core.domain.UnitType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

class WorkbookImportService(
    private val context: Context,
    private val dao: MathDao,
    private val fileStorage: FileStorage
) {
    suspend fun importZip(uri: Uri): String = withContext(Dispatchers.IO) {
        val entries = readZipEntries(uri)
        val workbookJson = findEntryBytes(entries, "workbook.json")?.toString(Charsets.UTF_8)
            ?: error("ZIP 안에 workbook.json 파일이 필요합니다.")
        val root = JSONObject(workbookJson)
        when (detectWorkbookManifestType(root)) {
            WorkbookManifestType.LegacyProblemSet -> Unit
            WorkbookManifestType.ScanPageCoordinates ->
                error("스캔형 문제집 JSON은 인식했지만, ZIP 가져오기 저장은 아직 1차 MVP 화면에만 연결되어 있습니다.")
        }
        val now = System.currentTimeMillis()
        val workbookObject = root.getJSONObject("workbook")
        val workbookId = workbookObject.optString("workbookId").ifBlank { UUID.randomUUID().toString() }

        dao.upsertWorkbook(
            WorkbookEntity(
                workbookId = workbookId,
                title = workbookObject.optString("title", "가져온 문제집"),
                description = workbookObject.optString("description", ""),
                grade = workbookObject.optString("grade", ""),
                subject = "math",
                createdAt = now,
                updatedAt = now,
                version = workbookObject.optInt("version", 1)
            )
        )

        val chapters = root.optJSONArray("chapters") ?: JSONArray()
        val defaultChapterId = if (chapters.length() > 0) {
            chapters.getJSONObject(0).optString("chapterId").ifBlank { "$workbookId-chapter-0" }
        } else {
            "$workbookId-chapter-1"
        }
        if (chapters.length() == 0) {
            dao.upsertChapter(
                ChapterEntity(
                    chapterId = defaultChapterId,
                    workbookId = workbookId,
                    title = "기본 진도",
                    orderIndex = 1
                )
            )
        }
        for (index in 0 until chapters.length()) {
            val chapter = chapters.getJSONObject(index)
            dao.upsertChapter(
                ChapterEntity(
                    chapterId = chapter.optString("chapterId").ifBlank { "$workbookId-chapter-$index" },
                    workbookId = workbookId,
                    title = chapter.optString("title", "${index + 1}단원"),
                    orderIndex = chapter.optInt("orderIndex", index + 1)
                )
            )
        }

        val problems = root.optJSONArray("problems") ?: JSONArray()
        for (index in 0 until problems.length()) {
            importProblem(workbookId, defaultChapterId, problems.getJSONObject(index), entries, index, now)
        }
        workbookId
    }

    private fun readZipEntries(uri: Uri): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "ZIP 파일을 열 수 없습니다." }
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) {
                        val output = ByteArrayOutputStream()
                        zip.copyTo(output)
                        result[entry.name.replace("\\", "/")] = output.toByteArray()
                    }
                    zip.closeEntry()
                }
            }
        }
        return result
    }

    private suspend fun importProblem(
        workbookId: String,
        defaultChapterId: String,
        problemJson: JSONObject,
        entries: Map<String, ByteArray>,
        index: Int,
        now: Long
    ) {
        val problemId = problemJson.optString("problemId").ifBlank { "$workbookId-problem-$index" }
        val imageEntryPath = problemJson.optString("imagePath").ifBlank { problemJson.optString("image") }
        val imagePath = findEntryBytes(entries, imageEntryPath)?.let { bytes ->
            fileStorage.saveImportedAsset(workbookId, imageEntryPath, bytes)
        }
        val sourceEntryPath = problemJson.optString("sourcePageImagePath")
        val sourcePagePath = findEntryBytes(entries, sourceEntryPath)?.let { bytes ->
            fileStorage.saveImportedAsset(workbookId, sourceEntryPath, bytes)
        }
        val maskOverlay = problemJson.opt("maskOverlayJson")?.toJsonString()
        val imageLayoutJson = buildImageLayoutJson(problemJson)

        dao.upsertProblem(
            ProblemEntity(
                problemId = problemId,
                workbookId = workbookId,
                chapterId = problemJson.optString("chapterId").ifBlank { defaultChapterId },
                problemType = enumOrDefault(problemJson.optString("problemType"), ProblemType.SHORT_NUMBER),
                questionText = problemJson.optString("questionText").ifBlank { null },
                questionLatex = problemJson.optString("questionLatex").ifBlank { null },
                imagePath = imagePath,
                sourcePageImagePath = sourcePagePath,
                imageCropRectJson = imageLayoutJson,
                maskOverlayJson = maskOverlay,
                difficulty = problemJson.optIntOrNull("difficulty"),
                orderIndex = problemJson.optInt("orderIndex", index + 1),
                hintText = problemJson.optString("hintText").ifBlank { null },
                hasGenerationTemplate = problemJson.has("template"),
                createdAt = now,
                updatedAt = now
            )
        )
        importAnswerFields(problemId, problemJson)
        importAnswerRules(problemId, problemJson)
        importChoices(problemId, problemJson)
        importTemplate(problemId, problemJson, now)
    }

    private suspend fun importAnswerFields(problemId: String, problemJson: JSONObject) {
        val fields = problemJson.optJSONArray("answerFields")
        if (fields == null || fields.length() == 0) {
            dao.upsertAnswerField(
                AnswerFieldEntity(
                    answerFieldId = "$problemId-answer",
                    problemId = problemId,
                    label = "답",
                    fieldType = AnswerFieldType.NUMBER,
                    orderIndex = 1,
                    positionJson = null,
                    required = true
                )
            )
            return
        }
        for (index in 0 until fields.length()) {
            val field = fields.getJSONObject(index)
            dao.upsertAnswerField(
                AnswerFieldEntity(
                    answerFieldId = field.optString("answerFieldId").ifBlank { "$problemId-field-$index" },
                    problemId = problemId,
                    label = field.optString("label", "답 ${index + 1}"),
                    fieldType = enumOrDefault(field.optString("fieldType"), AnswerFieldType.NUMBER),
                    orderIndex = field.optInt("orderIndex", index + 1),
                    positionJson = buildAnswerFieldMetaJson(field),
                    required = field.optBoolean("required", true)
                )
            )
        }
    }

    private suspend fun importAnswerRules(problemId: String, problemJson: JSONObject) {
        val rules = problemJson.optJSONArray("answerRules")
        if (rules == null || rules.length() == 0) {
            val correct = problemJson.optString("correctAnswerRaw")
            if (correct.isBlank()) return
            dao.upsertAnswerRule(
                AnswerRuleEntity(
                    answerRuleId = "$problemId-rule",
                    problemId = problemId,
                    answerFieldId = "$problemId-answer",
                    answerType = AnswerType.INTEGER,
                    correctAnswerRaw = correct,
                    normalizedAnswer = correct.trim().replace(",", ""),
                    allowEquivalentFraction = false,
                    requireSimplifiedFraction = false,
                    decimalTolerance = null,
                    allowMultipleAnswers = false,
                    acceptedAnswersJson = null,
                    unitType = UnitType.NONE,
                    manualGradingRequired = false
                )
            )
            return
        }
        for (index in 0 until rules.length()) {
            val rule = rules.getJSONObject(index)
            val correct = rule.optString("correctAnswerRaw")
            val answerType = enumOrDefault(rule.optString("answerType"), AnswerType.INTEGER)
            dao.upsertAnswerRule(
                AnswerRuleEntity(
                    answerRuleId = rule.optString("answerRuleId").ifBlank { "$problemId-rule-$index" },
                    problemId = problemId,
                    answerFieldId = rule.optString("answerFieldId").ifBlank { null },
                    answerType = answerType,
                    correctAnswerRaw = correct,
                    normalizedAnswer = rule.optString("normalizedAnswer").ifBlank { correct.trim().replace(",", "") },
                    allowEquivalentFraction = rule.optBoolean("allowEquivalentFraction", false),
                    requireSimplifiedFraction = rule.optBoolean("requireSimplifiedFraction", false),
                    decimalTolerance = rule.optDoubleOrNull("decimalTolerance"),
                    allowMultipleAnswers = rule.optBoolean("allowMultipleAnswers", false),
                    acceptedAnswersJson = rule.opt("acceptedAnswersJson")?.toJsonString(),
                    unitType = enumOrDefault(rule.optString("unitType"), UnitType.NONE),
                    manualGradingRequired = rule.optBoolean("manualGradingRequired", false) ||
                        rule.optBoolean("manualReviewRequired", false) ||
                        rule.optBoolean("skipAutoGrading", false) ||
                        answerType == AnswerType.MANUAL ||
                        answerType == AnswerType.MANUAL_REVIEW
                )
            )
        }
    }

    private suspend fun importChoices(problemId: String, problemJson: JSONObject) {
        val choices = problemJson.optJSONArray("choices") ?: return
        for (index in 0 until choices.length()) {
            val choice = choices.getJSONObject(index)
            dao.upsertChoice(
                ChoiceEntity(
                    choiceId = choice.optString("choiceId").ifBlank { "$problemId-choice-$index" },
                    problemId = problemId,
                    choiceText = choice.optString("choiceText"),
                    choiceValue = choice.optString("choiceValue", choice.optString("choiceText")),
                    isCorrect = choice.optBoolean("isCorrect", false),
                    orderIndex = choice.optInt("orderIndex", index + 1)
                )
            )
        }
    }

    private suspend fun importTemplate(problemId: String, problemJson: JSONObject, now: Long) {
        val template = problemJson.optJSONObject("template") ?: return
        dao.upsertTemplate(
            ProblemTemplateEntity(
                templateId = template.optString("templateId").ifBlank { "$problemId-template" },
                problemId = problemId,
                templateText = template.optString("templateText"),
                templateLatex = template.optString("templateLatex").ifBlank { null },
                variableRulesJson = template.opt("variableRulesJson")?.toJsonString() ?: "{}",
                answerFormulaJson = template.opt("answerFormulaJson")?.toJsonString() ?: "{}",
                validationRulesJson = template.opt("validationRulesJson")?.toJsonString(),
                enabled = template.optBoolean("enabled", true),
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, default: T): T {
        val normalized = raw
            ?.trim()
            ?.replace("-", "_")
            ?.uppercase()
            .orEmpty()
        return enumValues<T>().firstOrNull { it.name == normalized } ?: default
    }

    private fun findEntryBytes(entries: Map<String, ByteArray>, path: String): ByteArray? {
        if (path.isBlank()) return null
        val cleaned = path.replace("\\", "/").trim('/')
        return entries[cleaned] ?: entries.entries.firstOrNull { (entryName, _) ->
            entryName.endsWith("/$cleaned")
        }?.value
    }

    private fun buildImageLayoutJson(problemJson: JSONObject): String? {
        val crop = problemJson.opt("imageCropRectJson")
        val display = problemJson.opt("imageDisplayJson")
        val gradingPolicy = problemJson.opt("gradingPolicy")
        val solutionText = problemJson.optString("solutionText").ifBlank { null }
        val teacherMemo = problemJson.optString("teacherMemo").ifBlank { null }
        val answerNote = problemJson.optString("answerNote").ifBlank { null }
        if (crop == null && display == null && gradingPolicy == null && solutionText == null && teacherMemo == null && answerNote == null) {
            return null
        }
        val root = JSONObject()
        if (crop != null) root.put("crop", crop)
        if (display != null) root.put("display", display)
        if (gradingPolicy != null) root.put("gradingPolicy", gradingPolicy)
        if (solutionText != null) root.put("solutionText", solutionText)
        if (teacherMemo != null) root.put("teacherMemo", teacherMemo)
        if (answerNote != null) root.put("answerNote", answerNote)
        return root.toString()
    }

    private fun buildAnswerFieldMetaJson(field: JSONObject): String? {
        val rawPosition = field.opt("positionJson")
        val meta = when (rawPosition) {
            is JSONObject -> JSONObject(rawPosition.toString())
            null -> JSONObject()
            else -> JSONObject().put("position", rawPosition.toJsonString())
        }
        listOf(
            "keyboardType",
            "skipAutoGrading",
            "manualReviewRequired",
            "gradingPolicy",
            "prefix",
            "displayPrefix",
            "suffix",
            "displaySuffix",
            "disabled",
            "readOnly",
            "displayValue",
            "placeholder",
            "choiceOptions",
            "choiceStyle",
            "choiceValueStyle",
            "choiceMultiSelect"
        ).forEach { key ->
            if (field.has(key) && !field.isNull(key)) meta.put(key, field.get(key))
        }
        return if (meta.length() == 0) null else meta.toString()
    }

    private fun JSONObject.optIntOrNull(name: String): Int? = if (has(name) && !isNull(name)) optInt(name) else null

    private fun JSONObject.optDoubleOrNull(name: String): Double? = if (has(name) && !isNull(name)) optDouble(name) else null

    private fun Any.toJsonString(): String = when (this) {
        is JSONObject, is JSONArray -> toString()
        else -> toString()
    }
}
