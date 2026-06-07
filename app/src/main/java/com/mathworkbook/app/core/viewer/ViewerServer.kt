package com.mathworkbook.app.core.viewer

import android.content.Context
import android.util.Log
import com.mathworkbook.app.core.database.AnswerFieldEntity
import com.mathworkbook.app.core.database.AttemptInputLogEntity
import com.mathworkbook.app.core.database.CompletedPracticeAttemptSummary
import com.mathworkbook.app.core.database.MathDao
import com.mathworkbook.app.core.database.ProblemEntity
import com.mathworkbook.app.core.domain.FinalStatus
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.ClientHandler
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class ViewerServerState(
    val running: Boolean = false,
    val url: String? = null,
    val token: String? = null,
    val message: String? = null,
    val requestCount: Int = 0
)

data class ViewerCurrentScreenSnapshot(
    val workbookTitle: String,
    val chapterTitle: String,
    val positionLabel: String,
    val problem: ProblemEntity?,
    val currentAnswer: String,
    val solutionVectorJson: String,
    val revision: Long,
    val updatedAt: Long
)

class ViewerServer(
    private val context: Context,
    private val dao: MathDao
) {
    private val random = SecureRandom()
    private val _state = MutableStateFlow(ViewerServerState())

    val state: StateFlow<ViewerServerState> = _state.asStateFlow()

    @Volatile private var server: ViewerHttpServer? = null
    @Volatile private var currentScreenSnapshot: ViewerCurrentScreenSnapshot? = null

    fun updateCurrentScreen(snapshot: ViewerCurrentScreenSnapshot) {
        currentScreenSnapshot = snapshot
    }

    fun clearCurrentScreen() {
        currentScreenSnapshot = null
    }

    @Synchronized
    fun start() {
        if (server != null) return
        try {
            val host = findLocalWifiIp()
            if (host == null) {
                _state.update { it.copy(running = false, message = "같은 Wi-Fi에서 사용할 태블릿 IP를 찾지 못했습니다.") }
                return
            }
            val token = newToken()
            val nextServer = ViewerHttpServer(host, token)
            nextServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = nextServer
            val url = "http://$host:${nextServer.listeningPort}/?token=$token"
            _state.update {
                ViewerServerState(
                    running = true,
                    url = url,
                    token = token,
                    message = "viewer 연결 대기 중",
                    requestCount = it.requestCount
                )
            }
        } catch (error: Throwable) {
            Log.e(Tag, "Viewer server start failed", error)
            runCatching { server?.stop() }
            server = null
            _state.update { it.copy(running = false, message = "viewer 서버를 시작하지 못했습니다: ${error.message}") }
        }
    }

    @Synchronized
    fun stop() {
        try {
            server?.stopSafely()
        } catch (error: Throwable) {
            Log.e(Tag, "Viewer server stop failed", error)
        } finally {
            server = null
            _state.update { ViewerServerState(message = "viewer 연결이 꺼졌습니다.", requestCount = it.requestCount) }
        }
    }

    private inner class ViewerHttpServer(
        hostname: String,
        private val token: String
    ) : NanoHTTPD(hostname, 0) {
        private val boundedRunner = BoundedAsyncRunner()

        init {
            asyncRunner = boundedRunner
        }

        override fun serve(session: IHTTPSession): Response {
            return try {
                route(session, token)
            } catch (error: Throwable) {
                Log.e(Tag, "Viewer request failed", error)
                textResponse(Status.INTERNAL_ERROR, "text/plain; charset=utf-8", error.message ?: "viewer error")
            }
        }

        fun stopSafely() {
            runCatching { stop() }
            boundedRunner.closeAll()
        }
    }

    private class BoundedAsyncRunner : NanoHTTPD.AsyncRunner {
        private val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "workbook-viewer-http").apply { isDaemon = true }
        }

        override fun exec(code: ClientHandler) {
            executor.execute(code)
        }

        override fun closed(clientHandler: ClientHandler) = Unit

        override fun closeAll() {
            executor.shutdownNow()
        }
    }

    private fun route(session: IHTTPSession, token: String): Response {
        if (session.method != Method.GET) {
            return textResponse(Status.METHOD_NOT_ALLOWED, "text/plain; charset=utf-8", "GET only")
        }
        val path = session.uri.orEmpty()
        if (path == "/favicon.ico") {
            return textResponse(Status.NOT_FOUND, "text/plain; charset=utf-8", "")
        }
        _state.update { it.copy(requestCount = it.requestCount + 1, message = "최근 viewer 요청 처리됨") }
        return when {
            path == "/" -> textResponse(Status.OK, "text/html; charset=utf-8", viewerHtml(token))
            path == "/api/status" -> jsonResponse(statusJson())
            path == "/api/current-screen" -> jsonResponse(currentScreenJson(param(session, "knownRevision")?.toLongOrNull()))
            path == "/api/current-screen-vector" -> currentScreenVectorResponse()
            path == "/api/attempts" -> jsonResponse(
                attemptsJson(
                    limit = param(session, "limit")?.toIntOrNull() ?: 40,
                    sinceRevision = param(session, "since")?.toLongOrNull()
                )
            )
            path.startsWith("/api/attempts/") && path.endsWith("/thumbnail") -> {
                val attemptId = path.removePrefix("/api/attempts/").removeSuffix("/thumbnail").trim('/')
                attemptThumbnailResponse(attemptId)
            }
            path.startsWith("/api/attempts/") && path.endsWith("/solution-vector") -> {
                val attemptId = path.removePrefix("/api/attempts/").removeSuffix("/solution-vector").trim('/')
                attemptSolutionResponse(attemptId)
            }
            path.startsWith("/api/attempts/") -> {
                val attemptId = path.removePrefix("/api/attempts/").trim('/')
                jsonResponse(attemptDetailJson(attemptId))
            }
            path.startsWith("/api/problems/") && path.endsWith("/image") -> {
                val problemId = path.removePrefix("/api/problems/").removeSuffix("/image").trim('/')
                problemImageResponse(problemId)
            }
            else -> textResponse(Status.NOT_FOUND, "text/plain; charset=utf-8", "not found")
        }
    }
    private fun statusJson(): JSONObject {
        return JSONObject()
            .put("app", context.packageName)
            .put("running", true)
            .put("mode", "completed_attempts_only")
            .put("updatedAt", System.currentTimeMillis())
    }

    private fun attemptsJson(limit: Int, sinceRevision: Long?): JSONObject = runBlocking {
        val attempts = dao.getCompletedPracticeAttemptSummaries(limit.coerceIn(1, 80))
        val latestRevision = attempts.maxOfOrNull { it.eventAt } ?: 0L
        if (sinceRevision != null && sinceRevision > 0L && latestRevision <= sinceRevision) {
            return@runBlocking JSONObject()
                .put("changed", false)
                .put("latestRevision", latestRevision)
                .put("items", JSONArray())
        }
        val attemptIds = attempts.map { it.attemptId }
        val logsByAttempt = if (attemptIds.isEmpty()) {
            emptyMap()
        } else {
            dao.getAttemptInputLogsForAttempts(attemptIds).groupBy { it.attemptId }
        }
        val problemIds = attempts.map { it.problemId }.distinct()
        val fieldsByProblem = if (problemIds.isEmpty()) {
            emptyMap()
        } else {
            dao.getAnswerFieldsForProblems(problemIds).groupBy { it.problemId }
        }
        val items = JSONArray()
        attempts.forEach { attempt ->
            items.put(
                JSONObject()
                    .put("attemptId", attempt.attemptId)
                    .put("attemptNumber", attempt.attemptNumber)
                    .put("problemId", attempt.problemId)
                    .put("problemOrder", attempt.problemOrder)
                    .put("problemTitle", problemTitle(attempt))
                    .put("workbookTitle", attempt.workbookTitle)
                    .put("chapterTitle", attempt.chapterTitle)
                    .put("status", attempt.finalStatus.name)
                    .put("isCorrect", attempt.isCorrect)
                    .put("submittedAt", attempt.eventAt)
                    .put("revision", attempt.eventAt)
                    .put("thumbnailUrl", thumbnailFileForSolution(attempt.solutionImagePath)?.takeIf { it.exists() }?.let {
                        "/api/attempts/${attempt.attemptId}/thumbnail"
                    } ?: JSONObject.NULL)
                    .put(
                        "submittedAnswer",
                        formatSubmittedAnswer(
                            logsByAttempt[attempt.attemptId].orEmpty(),
                            fieldsByProblem[attempt.problemId].orEmpty()
                        )
                    )
            )
        }
        JSONObject()
            .put("changed", true)
            .put("latestRevision", latestRevision)
            .put("items", items)
    }

    private fun currentScreenJson(knownRevision: Long?): JSONObject {
        val snapshot = currentScreenSnapshot
            ?: return JSONObject()
                .put("available", false)
                .put("changed", false)
                .put("revision", 0L)
                .put("message", "현재 풀이 중인 화면이 없습니다.")
        if (knownRevision != null && knownRevision == snapshot.revision) {
            return JSONObject()
                .put("available", true)
                .put("changed", false)
                .put("revision", snapshot.revision)
                .put("updatedAt", snapshot.updatedAt)
        }
        val problem = snapshot.problem
        return JSONObject()
            .put("available", true)
            .put("changed", true)
            .put("revision", snapshot.revision)
            .put("updatedAt", snapshot.updatedAt)
            .put("workbookTitle", snapshot.workbookTitle)
            .put("chapterTitle", snapshot.chapterTitle)
            .put("positionLabel", snapshot.positionLabel)
            .put("currentAnswer", snapshot.currentAnswer)
            .put("problem", problemJson(problem))
            .put("problemImageUrl", if (problem?.imagePath.isNullOrBlank()) JSONObject.NULL else "/api/problems/${problem?.problemId}/image")
            .put("solutionVectorUrl", "/api/current-screen-vector")
    }

    private fun attemptDetailJson(attemptId: String): JSONObject = runBlocking {
        val attempt = dao.getPracticeAttempt(attemptId) ?: error("attempt not found")
        if (attempt.finalStatus == FinalStatus.IN_PROGRESS) error("attempt is not completed")
        val problem = dao.getProblem(attempt.problemId)
        val workbook = dao.getWorkbook(attempt.workbookId)
        val chapter = dao.getChapter(attempt.chapterId)
        val fields = dao.getAnswerFields(attempt.problemId)
        val logs = dao.getAttemptInputLogs(attempt.attemptId)
        JSONObject()
            .put("attemptId", attempt.attemptId)
            .put("attemptNumber", attempt.attemptNumber)
            .put("status", attempt.finalStatus.name)
            .put("isCorrect", attempt.isCorrect)
            .put("submittedAt", attempt.submittedAt ?: attempt.startedAt)
            .put("elapsedSeconds", attempt.elapsedSeconds)
            .put("workbookTitle", workbook?.title.orEmpty())
            .put("chapterTitle", chapter?.title.orEmpty())
            .put("problem", problemJson(problem))
            .put("answers", answerLogsJson(logs, fields))
            .put("submittedAnswer", formatSubmittedAnswer(logs, fields))
            .put("reviewerComment", attempt.reviewerComment.orEmpty())
            .put("manualReviewStatus", attempt.manualReviewStatus?.name.orEmpty())
            .put("solutionVectorUrl", "/api/attempts/${attempt.attemptId}/solution-vector")
            .put("problemImageUrl", if (problem?.imagePath.isNullOrBlank()) JSONObject.NULL else "/api/problems/${attempt.problemId}/image")
    }

    private fun problemJson(problem: ProblemEntity?): JSONObject {
        return JSONObject()
            .put("problemId", problem?.problemId.orEmpty())
            .put("orderIndex", problem?.orderIndex ?: 0)
            .put("questionText", problem?.questionText.orEmpty())
            .put("questionLatex", problem?.questionLatex.orEmpty())
            .put("hasImage", !problem?.imagePath.isNullOrBlank())
    }

    private fun answerLogsJson(logs: List<AttemptInputLogEntity>, fields: List<AnswerFieldEntity>): JSONArray {
        val fieldById = fields.associateBy { it.answerFieldId }
        return JSONArray().apply {
            logs.forEach { log ->
                val field = log.answerFieldId?.let { fieldById[it] }
                put(
                    JSONObject()
                        .put("tryNumber", log.tryNumber)
                        .put("fieldLabel", field?.label.orEmpty())
                        .put("answer", log.submittedAnswerRaw)
                        .put("isCorrect", log.isCorrect)
                        .put("submittedAt", log.submittedAt)
                )
            }
        }
    }

    private fun currentScreenVectorResponse(): Response {
        val vectorJson = currentScreenSnapshot?.solutionVectorJson
            ?.takeIf { it.isNotBlank() }
            ?: """{"strokes":[]}"""
        return textResponse(Status.OK, "application/json; charset=utf-8", vectorJson)
    }

    private fun attemptSolutionResponse(attemptId: String): Response = runBlocking {
        val attempt = dao.getPracticeAttempt(attemptId) ?: error("attempt not found")
        if (attempt.finalStatus == FinalStatus.IN_PROGRESS) error("attempt is not completed")
        val file = attempt.solutionImagePath?.let(::File)?.takeIf { it.exists() }
        if (file == null) {
            textResponse(Status.NOT_FOUND, "text/plain; charset=utf-8", "solution not found")
        } else {
            fileResponse(Status.OK, "application/json; charset=utf-8", file, "private, max-age=3600")
        }
    }

    private fun attemptThumbnailResponse(attemptId: String): Response = runBlocking {
        val attempt = dao.getPracticeAttempt(attemptId) ?: error("attempt not found")
        if (attempt.finalStatus == FinalStatus.IN_PROGRESS) error("attempt is not completed")
        val file = thumbnailFileForSolution(attempt.solutionImagePath)?.takeIf { it.exists() }
        if (file == null) {
            textResponse(Status.NOT_FOUND, "text/plain; charset=utf-8", "thumbnail not found")
        } else {
            fileResponse(Status.OK, mimeFor(file), file, "private, max-age=3600")
        }
    }

    private fun problemImageResponse(problemId: String): Response = runBlocking {
        val problem = dao.getProblem(problemId) ?: error("problem not found")
        val file = problem.imagePath?.let(::File)?.takeIf { it.exists() }
        if (file == null) {
            textResponse(Status.NOT_FOUND, "text/plain; charset=utf-8", "image not found")
        } else {
            fileResponse(Status.OK, mimeFor(file), file, "public, max-age=86400")
        }
    }

    private fun jsonResponse(body: JSONObject): Response {
        return textResponse(Status.OK, "application/json; charset=utf-8", body.toString())
    }

    private fun textResponse(status: Status, contentType: String, body: String): Response {
        return NanoHTTPD.newFixedLengthResponse(status, contentType, body).withCommonHeaders("no-store")
    }

    private fun fileResponse(status: Status, contentType: String, file: File, cacheControl: String): Response {
        return NanoHTTPD.newChunkedResponse(status, contentType, file.inputStream()).withCommonHeaders(cacheControl)
    }

    private fun Response.withCommonHeaders(cacheControl: String): Response {
        addHeader("Cache-Control", cacheControl)
        addHeader("Access-Control-Allow-Origin", "*")
        setKeepAlive(false)
        return this
    }

    private fun viewerHtml(token: String): String {
        return context.assets.open("viewer/index.html").bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readText()
        }.replace("__TOKEN__", token)
    }

    private fun param(session: IHTTPSession, name: String): String? {
        return session.parameters[name]?.firstOrNull()
    }
    private fun problemTitle(problem: ProblemEntity?): String {
        return problem?.questionText
            ?.replace(Regex("\\s+"), " ")
            ?.take(42)
            ?.ifBlank { null }
            ?: "문제 ${problem?.orderIndex ?: ""}".trim()
    }

    private fun problemTitle(attempt: CompletedPracticeAttemptSummary): String {
        return attempt.problemQuestionText
            .replace(Regex("\\s+"), " ")
            .take(42)
            .ifBlank { "문제 ${attempt.problemOrder}".trim() }
    }

    private fun thumbnailFileForSolution(solutionPath: String?): File? {
        val vectorFile = solutionPath?.let(::File) ?: return null
        val thumbName = vectorFile.name
            .removeSuffix("-solution-vector.json")
            .removeSuffix(".json") + "-solution-thumb.jpg"
        return File(vectorFile.parentFile, thumbName)
    }

    private fun formatSubmittedAnswer(logs: List<AttemptInputLogEntity>, fields: List<AnswerFieldEntity>): String {
        if (logs.isEmpty()) return ""
        val fieldById = fields.associateBy { it.answerFieldId }
        return logs
            .groupBy { it.tryNumber }
            .entries
            .sortedBy { it.key }
            .joinToString(" / ") { (_, tryLogs) ->
                val includeLabels = tryLogs.count { !it.answerFieldId.isNullOrBlank() } > 1
                tryLogs.sortedBy { it.answerFieldId.orEmpty() }.joinToString(", ") { log ->
                    val label = log.answerFieldId?.let { fieldById[it] }?.label.orEmpty()
                    if (includeLabels && label.isNotBlank()) "$label ${log.submittedAnswerRaw}" else log.submittedAnswerRaw
                }
            }
    }

    private fun parseVectorJson(vectorJson: String): JSONObject {
        return runCatching { JSONObject(vectorJson) }.getOrDefault(JSONObject().put("strokes", JSONArray()))
    }

    private fun findLocalWifiIp(): String? {
        val interfaces = NetworkInterface.getNetworkInterfaces()
            .toList()
            .filter { it.isUp && !it.isLoopback }
        val wifiFirst = interfaces.sortedBy { network ->
            val name = network.name.lowercase()
            if (name.startsWith("wlan") || name.contains("wifi")) 0 else 1
        }
        return wifiFirst
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress
    }

    private fun newToken(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return UUID.nameUUIDFromBytes(bytes).toString().replace("-", "")
    }

    private fun mimeFor(file: File): String {
        return when (file.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }

    private companion object {
        const val Tag = "ViewerServer"
    }
}
