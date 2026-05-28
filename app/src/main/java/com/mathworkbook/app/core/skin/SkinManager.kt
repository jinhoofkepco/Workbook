package com.mathworkbook.app.core.skin

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipInputStream

class SkinManager(private val context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "math_workbook_preferences",
        Context.MODE_PRIVATE
    )
    private val rootDir = File(context.filesDir, "math_workbook/skins")
    private val _state = MutableStateFlow(SkinManagerState())
    val state: StateFlow<SkinManagerState> = _state.asStateFlow()

    init {
        refresh()
    }

    suspend fun importZip(uri: Uri): WorkbookSkin = withContext(Dispatchers.IO) {
        val entries = readZipEntries(uri)
        if (findEntryBytes(entries, "workbook.json") != null) {
            throw NotSkinZipException("문제집 ZIP입니다.")
        }

        val displayName = queryDisplayName(uri)?.replace(Regex("(?i)\\.zip$"), "")
            ?: "imported_skin_${System.currentTimeMillis()}"
        val manifestJson = findEntryBytes(entries, "skin.json")
            ?.toString(Charsets.UTF_8)
            ?.let(::JSONObject)
            ?: buildManifestFromAssets(entries, displayName)
            ?: throw NotSkinZipException("skin.json 또는 스킨 PNG 에셋을 찾지 못했습니다.")

        val skinId = manifestJson.optString("skinId").ifBlank {
            displayName.replace(Regex("(?i)^workbook_skin_"), "")
        }.safeId()
        val targetDir = File(rootDir, skinId)
        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()

        val manifestAssets = manifestJson.optJSONObject("assets") ?: JSONObject()
        val copiedAssets = mutableMapOf<String, String>()
        for (key in manifestAssets.keys()) {
            val relativePath = manifestAssets.optString(key)
            val bytes = findEntryBytes(entries, relativePath)
                ?: findEntryBytes(entries, File(relativePath).name)
                ?: continue
            val cleanedPath = relativePath.replace("\\", "/").trim('/').ifBlank { "${key}.png" }
            val output = File(targetDir, cleanedPath)
            output.parentFile?.mkdirs()
            output.writeBytes(bytes)
            copiedAssets[key] = cleanedPath
        }
        if (copiedAssets.isEmpty()) {
            throw NotSkinZipException("스킨 에셋 PNG를 찾지 못했습니다.")
        }

        val storedManifest = JSONObject().apply {
            put("skinId", skinId)
            put("displayName", manifestJson.optString("displayName").ifBlank { displayName })
            put("version", manifestJson.optInt("version", 1))
            put(
                "assets",
                JSONObject().apply {
                    copiedAssets.forEach { (key, path) -> put(key, path) }
                }
            )
        }
        File(targetDir, "skin.json").writeText(storedManifest.toString(2), Charsets.UTF_8)
        preferences.edit().putString(PREF_ACTIVE_SKIN_ID, skinId).apply()
        val skin = parseSkin(targetDir, storedManifest)
            ?: throw NotSkinZipException("스킨 정보를 저장하지 못했습니다.")
        refresh("스킨 '${skin.displayName}'을 적용했습니다.")
        skin
    }

    fun clearActiveSkin() {
        preferences.edit().remove(PREF_ACTIVE_SKIN_ID).apply()
        refresh("기본 화면 스킨으로 돌아왔습니다.")
    }

    fun setActiveSkin(skinId: String) {
        val exists = rootDir.listFiles()?.any { dir ->
            dir.isDirectory && dir.name == skinId && File(dir, "skin.json").exists()
        } == true
        if (!exists) {
            refresh("스킨을 찾지 못했습니다.")
            return
        }
        preferences.edit().putString(PREF_ACTIVE_SKIN_ID, skinId).apply()
        refresh("스킨을 적용했습니다.")
    }

    fun refresh(message: String? = null) {
        val skins = rootDir
            .takeIf { it.exists() }
            ?.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val manifest = File(dir, "skin.json")
                if (!manifest.exists()) null else runCatching {
                    parseSkin(dir, JSONObject(manifest.readText(Charsets.UTF_8)))
                }.getOrNull()
            }
            ?.sortedBy { it.displayName.lowercase(Locale.ROOT) }
            ?: emptyList()
        val activeSkinId = preferences.getString(PREF_ACTIVE_SKIN_ID, null)
        _state.update {
            SkinManagerState(
                installedSkins = skins,
                activeSkin = skins.firstOrNull { skin -> skin.skinId == activeSkinId },
                message = message
            )
        }
    }

    private fun parseSkin(dir: File, root: JSONObject): WorkbookSkin? {
        val assets = root.optJSONObject("assets") ?: return null
        val resolvedAssets = mutableMapOf<String, String>()
        for (key in assets.keys()) {
            val relativePath = assets.optString(key).replace("\\", "/").trim('/')
            val file = File(dir, relativePath)
            if (file.exists()) resolvedAssets[key] = file.absolutePath
        }
        if (resolvedAssets.isEmpty()) return null
        return WorkbookSkin(
            skinId = root.optString("skinId").ifBlank { dir.name },
            displayName = root.optString("displayName").ifBlank { dir.name },
            version = root.optInt("version", 1),
            assets = resolvedAssets
        )
    }

    private fun buildManifestFromAssets(
        entries: Map<String, ByteArray>,
        displayName: String
    ): JSONObject? {
        val assets = JSONObject()
        for ((key, fileName) in KnownAssetFiles) {
            val entryName = entries.keys.firstOrNull { entry ->
                entry.substringAfterLast("/") == fileName
            } ?: continue
            assets.put(key, "assets/$fileName")
        }
        if (assets.length() == 0) return null
        val friendlyName = displayName
            .replace(Regex("(?i)^workbook_skin_"), "")
            .replace('_', ' ')
            .trim()
            .ifBlank { "Workbook Skin" }
        return JSONObject().apply {
            put("skinId", displayName.replace(Regex("(?i)^workbook_skin_"), "").safeId())
            put("displayName", friendlyName)
            put("version", 1)
            put("assets", assets)
        }
    }

    private fun readZipEntries(uri: Uri): MutableMap<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "ZIP 파일을 열 수 없습니다." }
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) {
                        val output = ByteArrayOutputStream()
                        zip.copyTo(output)
                        result[entry.name.replace("\\", "/").trim('/')] = output.toByteArray()
                    }
                    zip.closeEntry()
                }
            }
        }
        return result
    }

    private fun findEntryBytes(entries: Map<String, ByteArray>, path: String): ByteArray? {
        val cleaned = path.replace("\\", "/").trim('/')
        if (cleaned.isBlank()) return null
        return entries[cleaned]
            ?: entries.entries.firstOrNull { it.key.endsWith("/$cleaned") }?.value
            ?: entries.entries.firstOrNull { it.key.substringAfterLast("/") == cleaned.substringAfterLast("/") }?.value
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }

    private fun String.safeId(): String {
        return lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9._-]+"), "_")
            .trim('_', '.', '-')
            .ifBlank { "skin_${System.currentTimeMillis()}" }
    }

    private companion object {
        private const val PREF_ACTIVE_SKIN_ID = "active_skin_id"

        private val KnownAssetFiles = mapOf(
            "dashboardBackground" to "dashboard_bg_1600x2560.png",
            "bookCoverBase" to "book_cover_base_512x700.png",
            "bookCoverSpine" to "book_cover_spine_96x700.png",
            "dashboardPageFrame" to "dashboard_page_frame_1600x2400.png",
            "dashboardTitleBanner" to "dashboard_title_banner_1200x220.png",
            "dashboardTitleFrame" to "dashboard_title_frame_1000x180.png",
            "chapterRowTab" to "chapter_row_tab_1200x180.png",
            "problemPaperBackground" to "problem_paper_bg_1600x2560.png",
            "toolbarStrip" to "toolbar_strip_1600x96.png",
            "problemHeaderPill" to "problem_header_pill_900x96.png",
            "masterButtonIdle" to "master_button_idle_128x128.png",
            "masterButtonActive" to "master_button_active_128x128.png",
            "navArrowPrevious" to "nav_arrow_previous_160x128.png",
            "navArrowNext" to "nav_arrow_next_160x128.png",
            "hintButton" to "hint_button_128x128.png",
            "submitButton" to "submit_button_320x128.png",
            "gradingButton" to "grading_button_320x128.png",
            "answerStampBlue" to "answer_stamp_blue_512x180.png",
            "answerWrongSlash" to "answer_wrong_slash_512x180.png"
        )
    }
}
