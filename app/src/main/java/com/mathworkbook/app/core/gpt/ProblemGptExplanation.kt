package com.mathworkbook.app.core.gpt

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SavedGptExplanation(
    val id: String,
    val title: String,
    val prompt: String,
    val explanationText: String,
    val explanationHtml: String,
    val savedAt: Long,
    val updatedAt: Long
)

fun parseGptExplanations(imageCropRectJson: String?): List<SavedGptExplanation> {
    val array = rootOrEmpty(imageCropRectJson)
        .optJSONObject(WORKBOOK_APP_NAMESPACE)
        ?.optJSONArray(GPT_EXPLANATIONS_KEY)
        ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id")
            if (id.isBlank()) continue
            val explanationText = item.optString("explanationText")
            if (explanationText.isBlank()) continue
            add(
                SavedGptExplanation(
                    id = id,
                    title = item.optString("title").ifBlank { explanationTitle(explanationText) },
                    prompt = item.optString("prompt"),
                    explanationText = explanationText,
                    explanationHtml = item.optString("explanationHtml"),
                    savedAt = item.optLong("savedAt", 0L),
                    updatedAt = item.optLong("updatedAt", item.optLong("savedAt", 0L))
                )
            )
        }
    }.sortedByDescending { it.savedAt }
}

fun addGptExplanationToImageCropJson(
    imageCropRectJson: String?,
    prompt: String,
    explanationText: String,
    explanationHtml: String = "",
    now: Long = System.currentTimeMillis()
): Pair<String, SavedGptExplanation> {
    val root = rootOrEmpty(imageCropRectJson)
    val namespace = root.optJSONObject(WORKBOOK_APP_NAMESPACE)
        ?: JSONObject().also { root.put(WORKBOOK_APP_NAMESPACE, it) }
    val array = namespace.optJSONArray(GPT_EXPLANATIONS_KEY)
        ?: JSONArray().also { namespace.put(GPT_EXPLANATIONS_KEY, it) }
    val explanation = SavedGptExplanation(
        id = "gpt-$now-${UUID.randomUUID().toString().take(8)}",
        title = explanationTitle(explanationText),
        prompt = prompt,
        explanationText = explanationText.trim(),
        explanationHtml = explanationHtml.trim(),
        savedAt = now,
        updatedAt = now
    )
    array.put(explanation.toJson())
    return root.toString() to explanation
}

fun deleteGptExplanationFromImageCropJson(
    imageCropRectJson: String?,
    explanationId: String
): String {
    val root = rootOrEmpty(imageCropRectJson)
    val namespace = root.optJSONObject(WORKBOOK_APP_NAMESPACE)
        ?: JSONObject().also { root.put(WORKBOOK_APP_NAMESPACE, it) }
    val source = namespace.optJSONArray(GPT_EXPLANATIONS_KEY) ?: JSONArray()
    val kept = JSONArray()
    for (index in 0 until source.length()) {
        val item = source.optJSONObject(index) ?: continue
        if (item.optString("id") != explanationId) {
            kept.put(item)
        }
    }
    namespace.put(GPT_EXPLANATIONS_KEY, kept)
    return root.toString()
}

fun mergeWorkbookAppNamespace(
    newImageCropRectJson: String?,
    existingImageCropRectJson: String?
): String? {
    val newRoot = rootOrNull(newImageCropRectJson) ?: JSONObject()
    val importedNamespace = newRoot.optJSONObject(WORKBOOK_APP_NAMESPACE)
    val existingNamespace = rootOrNull(existingImageCropRectJson)
        ?.optJSONObject(WORKBOOK_APP_NAMESPACE)
    val mergedNamespace = mergeWorkbookAppNamespaces(
        importedNamespace = importedNamespace,
        existingNamespace = existingNamespace
    )
    if (mergedNamespace.length() > 0) {
        newRoot.put(WORKBOOK_APP_NAMESPACE, mergedNamespace)
    }
    return if (newRoot.length() == 0) null else newRoot.toString()
}

fun mergeWorkbookAppIntoLayoutJson(
    imageCropRectJson: String?,
    workbookAppJson: JSONObject?,
    gptExplanationsJson: JSONArray?
): String? {
    if (workbookAppJson == null && gptExplanationsJson == null) return imageCropRectJson
    val root = rootOrNull(imageCropRectJson) ?: JSONObject()
    val importedNamespace = root.optJSONObject(WORKBOOK_APP_NAMESPACE)
    val directNamespace = workbookAppJson?.let { JSONObject(it.toString()) }
    if (gptExplanationsJson != null) {
        val namespace = directNamespace ?: JSONObject()
        namespace.put(GPT_EXPLANATIONS_KEY, JSONArray(gptExplanationsJson.toString()))
        val merged = mergeWorkbookAppNamespaces(
            importedNamespace = namespace,
            existingNamespace = importedNamespace
        )
        root.put(WORKBOOK_APP_NAMESPACE, merged)
    } else {
        root.put(
            WORKBOOK_APP_NAMESPACE,
            mergeWorkbookAppNamespaces(
                importedNamespace = directNamespace,
                existingNamespace = importedNamespace
            )
        )
    }
    return root.toString()
}

private fun SavedGptExplanation.toJson(): JSONObject {
    return JSONObject()
        .put("id", id)
        .put("title", title)
        .put("prompt", prompt)
        .put("explanationText", explanationText)
        .put("explanationHtml", explanationHtml)
        .put("savedAt", savedAt)
        .put("updatedAt", updatedAt)
}

private fun mergeWorkbookAppNamespaces(
    importedNamespace: JSONObject?,
    existingNamespace: JSONObject?
): JSONObject {
    val merged = JSONObject()
    existingNamespace?.copyInto(merged)
    importedNamespace?.copyInto(merged)
    val importedExplanations = importedNamespace?.optJSONArray(GPT_EXPLANATIONS_KEY)
    val existingExplanations = existingNamespace?.optJSONArray(GPT_EXPLANATIONS_KEY)
    val mergedExplanations = mergeGptExplanationArrays(
        preferred = importedExplanations,
        fallback = existingExplanations
    )
    if (mergedExplanations.length() > 0) {
        merged.put(GPT_EXPLANATIONS_KEY, mergedExplanations)
    }
    return merged
}

private fun mergeGptExplanationArrays(
    preferred: JSONArray?,
    fallback: JSONArray?
): JSONArray {
    val result = JSONArray()
    val seen = mutableSetOf<String>()
    fun appendAll(source: JSONArray?) {
        if (source == null) return
        for (index in 0 until source.length()) {
            val item = source.optJSONObject(index) ?: continue
            val id = item.optString("id").ifBlank { "index-$index-${item.optString("title")}-${item.optString("explanationText").take(24)}" }
            if (seen.add(id)) {
                result.put(JSONObject(item.toString()))
            }
        }
    }
    appendAll(preferred)
    appendAll(fallback)
    return result
}

private fun JSONObject.copyInto(target: JSONObject) {
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        target.put(key, get(key))
    }
}

private fun explanationTitle(text: String): String {
    val firstLine = text
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        .orEmpty()
    return firstLine.take(32).ifBlank { "GPT 설명" }
}

private fun rootOrEmpty(json: String?): JSONObject = rootOrNull(json) ?: JSONObject()

private fun rootOrNull(json: String?): JSONObject? {
    if (json.isNullOrBlank()) return null
    return runCatching { JSONObject(json) }.getOrNull()
}

private const val WORKBOOK_APP_NAMESPACE = "workbookApp"
private const val GPT_EXPLANATIONS_KEY = "gptExplanations"
