package com.mathworkbook.app.core.files

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.mathworkbook.app.core.domain.RelativeRect
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

    fun saveImportedAsset(workbookId: String, relativePath: String, bytes: ByteArray): String {
        val cleaned = relativePath.replace("\\", "/").trim('/').ifBlank { "asset-${System.currentTimeMillis()}" }
        val output = File(importedAssetsDir(workbookId), cleaned)
        output.parentFile?.mkdirs()
        output.writeBytes(bytes)
        return output.absolutePath
    }
}
