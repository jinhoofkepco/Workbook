package com.mathworkbook.app.core.generation

import com.mathworkbook.app.core.database.GeneratedProblemEntity
import com.mathworkbook.app.core.database.ProblemTemplateEntity
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.random.Random

class SimilarProblemGenerator(
    private val evaluator: SafeFormulaEvaluator = SafeFormulaEvaluator()
) {
    fun generate(
        template: ProblemTemplateEntity,
        studentId: String,
        reason: String,
        random: Random = Random.Default
    ): GeneratedProblemEntity? {
        repeat(100) {
            val variables = sampleVariables(template.variableRulesJson, random)
            if (!validate(template.validationRulesJson, variables)) return@repeat
            val question = replaceVariables(template.templateText, variables)
            val latex = template.templateLatex?.let { replaceVariables(it, variables) }
            val answers = calculateAnswers(template.answerFormulaJson, variables)
            return GeneratedProblemEntity(
                generatedProblemId = UUID.randomUUID().toString(),
                sourceProblemId = template.problemId,
                studentId = studentId,
                generatedQuestionText = question,
                generatedQuestionLatex = latex,
                generatedAnswerRawJson = answers.toString(),
                generatedVariablesJson = JSONObject(variables.mapValues { it.value.toLongIfWhole() }).toString(),
                reason = reason,
                difficultyLevel = null,
                createdAt = System.currentTimeMillis()
            )
        }
        return null
    }

    fun preview(template: ProblemTemplateEntity, count: Int = 5): List<GeneratedProblemEntity> {
        return buildList {
            repeat(count) {
                generate(template, studentId = "preview", reason = "PREVIEW")?.let { add(it) }
            }
        }
    }

    private fun sampleVariables(json: String, random: Random): Map<String, Double> {
        val root = JSONObject(json)
        val variablesArray = root.optJSONArray("variables")
        val result = mutableMapOf<String, Double>()
        if (variablesArray != null) {
            for (index in 0 until variablesArray.length()) {
                val item = variablesArray.getJSONObject(index)
                val name = item.getString("name")
                val min = item.optInt("min", 0)
                val max = item.optInt("max", 9)
                result[name] = random.nextInt(min, max + 1).toDouble()
            }
        } else {
            root.keys().forEach { name ->
                val item = root.getJSONObject(name)
                val min = item.optInt("min", 0)
                val max = item.optInt("max", 9)
                result[name] = random.nextInt(min, max + 1).toDouble()
            }
        }
        return result
    }

    private fun validate(json: String?, variables: Map<String, Double>): Boolean {
        if (json.isNullOrBlank()) return true
        val root = JSONObject(json)
        val conditions = root.optJSONArray("conditions") ?: JSONArray()
        for (index in 0 until conditions.length()) {
            if (!evaluator.evaluateCondition(conditions.getString(index), variables)) return false
        }
        return true
    }

    private fun calculateAnswers(json: String, variables: Map<String, Double>): JSONObject {
        val root = JSONObject(json)
        val output = JSONObject()
        val fields = root.optJSONArray("fields")
        if (fields != null) {
            for (index in 0 until fields.length()) {
                val item = fields.getJSONObject(index)
                val key = item.optString("answerFieldId", item.optString("label", "answer_$index"))
                output.put(key, evaluator.evaluate(item.getString("formula"), variables).toLongIfWhole())
            }
        } else {
            output.put("answer", evaluator.evaluate(root.getString("answer"), variables).toLongIfWhole())
        }
        return output
    }

    private fun replaceVariables(template: String, variables: Map<String, Double>): String {
        return variables.entries.fold(template) { acc, entry ->
            acc.replace("{${entry.key}}", entry.value.toLongIfWhole().toString())
        }
    }

    private fun Double.toLongIfWhole(): Any {
        val longValue = toLong()
        return if (this == longValue.toDouble()) longValue else this
    }
}
