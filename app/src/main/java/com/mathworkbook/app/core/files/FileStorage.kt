package com.mathworkbook.app.core.files

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import com.mathworkbook.app.core.domain.RelativeRect
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class FileStorage(private val context: Context) {
    private val root: File = File(context.filesDir, "math_workbook")

    fun workbookDir(workbookId: String): File = File(root, "workbooks/$workbookId").also { it.mkdirs() }

    fun pagesDir(workbookId: String): File = File(workbookDir(workbookId), "pages").also { it.mkdirs() }

    fun problemsDir(workbookId: String): File = File(workbookDir(workbookId), "problems").also { it.mkdirs() }

    fun importedAssetsDir(workbookId: String): File = File(workbookDir(workbookId), "imported").also { it.mkdirs() }

    fun deleteWorkbookFiles(workbookId: String): Boolean {
        val dir = File(root, "workbooks/$workbookId")
        return !dir.exists() || dir.deleteRecursively()
    }

    fun solutionDir(studentId: String, attemptOrSessionId: String): File {
        return File(root, "students/$studentId/$attemptOrSessionId").also { it.mkdirs() }
    }

    fun copyPageImage(workbookId: String, uri: Uri, contentResolver: ContentResolver): String {
        val output = File(pagesDir(workbookId), "${System.currentTimeMillis()}.jpg")
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open selected image" }
            FileOutputStream(output).use { outputStream -> input.copyTo(outputStream) }
        }
        return output.absolutePath
    }

    fun cropProblemImage(
        workbookId: String,
        sourceImagePath: String,
        problemId: String,
        relativeRect: RelativeRect
    ): String {
        val source = BitmapFactory.decodeFile(sourceImagePath)
            ?: error("Cannot decode image: $sourceImagePath")
        val left = (source.width * relativeRect.left).toInt().coerceIn(0, source.width - 1)
        val top = (source.height * relativeRect.top).toInt().coerceIn(0, source.height - 1)
        val width = (source.width * relativeRect.width).toInt().coerceIn(1, source.width - left)
        val height = (source.height * relativeRect.height).toInt().coerceIn(1, source.height - top)
        val cropped = Bitmap.createBitmap(source, left, top, width, height)
        val output = File(problemsDir(workbookId), "$problemId.jpg")
        FileOutputStream(output).use { stream ->
            cropped.compress(Bitmap.CompressFormat.JPEG, 94, stream)
        }
        cropped.recycle()
        source.recycle()
        return output.absolutePath
    }

    fun saveSolutionPng(
        studentId: String,
        attemptOrSessionId: String,
        problemId: String,
        bitmap: Bitmap
    ): String {
        val output = File(solutionDir(studentId, attemptOrSessionId), "$problemId-solution.png")
        FileOutputStream(output).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        return output.absolutePath
    }

    fun saveSolutionVectorJson(
        studentId: String,
        attemptOrSessionId: String,
        problemId: String,
        vectorJson: String
    ): String {
        val output = File(solutionDir(studentId, attemptOrSessionId), "$problemId-solution-vector.json")
        output.writeText(vectorJson)
        return output.absolutePath
    }

    fun saveMasterNoteVectorJson(problemId: String, vectorJson: String): String {
        val output = File(masterNotesDir(), "${problemId.safeFileName()}-master-note-vector.json")
        output.writeText(vectorJson)
        return output.absolutePath
    }

    fun readMasterNoteVectorJson(problemId: String): String? {
        val input = File(masterNotesDir(), "${problemId.safeFileName()}-master-note-vector.json")
        return input.takeIf { it.exists() }?.readText()
    }

    fun saveImportedAsset(workbookId: String, relativePath: String, bytes: ByteArray): String {
        val cleaned = relativePath.replace("\\", "/").trim('/').ifBlank { "asset-${System.currentTimeMillis()}" }
        val output = File(importedAssetsDir(workbookId), cleaned)
        output.parentFile?.mkdirs()
        output.writeBytes(bytes)
        return output.absolutePath
    }

    fun mergeProblemImageWithVector(
        workbookId: String,
        problemId: String,
        imagePath: String,
        vectorJson: String
    ): String {
        val source = BitmapFactory.decodeFile(imagePath)
            ?: error("사진 파일을 읽을 수 없습니다.")
        val outputBitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)
        val root = JSONObject(vectorJson)
        val imageBounds = root.optJSONObject("imageBounds")
            ?: error("사진 위치를 찾지 못했습니다. 문제 화면을 한 번 더 연 뒤 저장해 주세요.")
        val left = imageBounds.optDouble("left").toFloat()
        val top = imageBounds.optDouble("top").toFloat()
        val width = imageBounds.optDouble("width").toFloat().takeIf { it > 0f }
            ?: error("사진 위치가 올바르지 않습니다.")
        val height = imageBounds.optDouble("height").toFloat().takeIf { it > 0f }
            ?: error("사진 위치가 올바르지 않습니다.")
        val strokes = root.optJSONArray("strokes") ?: error("합칠 필기가 없습니다.")
        for (strokeIndex in 0 until strokes.length()) {
            val stroke = strokes.getJSONObject(strokeIndex)
            val points = stroke.optJSONArray("points") ?: continue
            if (points.length() < 2) continue
            val path = Path()
            var hasPoint = false
            for (pointIndex in 0 until points.length()) {
                val point = points.getJSONObject(pointIndex)
                val localX = point.optDouble("x").toFloat() - left
                val localY = point.optDouble("y").toFloat() - top
                if (localX < 0f || localY < 0f || localX > width || localY > height) continue
                val mappedX = localX / width * outputBitmap.width
                val mappedY = localY / height * outputBitmap.height
                if (!hasPoint) {
                    path.moveTo(mappedX, mappedY)
                    hasPoint = true
                } else {
                    path.lineTo(mappedX, mappedY)
                }
            }
            if (!hasPoint) continue
            val scale = outputBitmap.width / width
            val isHighlighter = stroke.optString("kind") == "Highlighter"
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = runCatching { Color.parseColor(stroke.optString("color")) }.getOrDefault(Color.BLACK)
                strokeWidth = (stroke.optDouble("width", 5.0).toFloat() * scale).coerceAtLeast(2f)
                strokeCap = if (isHighlighter) Paint.Cap.BUTT else Paint.Cap.ROUND
                strokeJoin = if (isHighlighter) Paint.Join.BEVEL else Paint.Join.ROUND
            }
            canvas.drawPath(path, paint)
        }
        val output = File(problemsDir(workbookId), "${problemId.safeFileName()}-edited-${System.currentTimeMillis()}.png")
        FileOutputStream(output).use { stream ->
            outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        outputBitmap.recycle()
        source.recycle()
        return output.absolutePath
    }

    private fun masterNotesDir(): File = File(root, "master_notes").also { it.mkdirs() }

    private fun String.safeFileName(): String {
        return replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "problem" }
    }
}
