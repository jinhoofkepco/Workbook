package com.mathworkbook.app.core.viewer

import android.content.Context
import com.mathworkbook.app.core.database.AnswerFieldEntity
import com.mathworkbook.app.core.database.AttemptInputLogEntity
import com.mathworkbook.app.core.database.MathDao
import com.mathworkbook.app.core.database.PracticeAttemptEntity
import com.mathworkbook.app.core.database.ProblemEntity
import com.mathworkbook.app.core.domain.FinalStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.Executors

data class ViewerServerState(
    val running: Boolean = false,
    val url: String? = null,
    val token: String? = null,
    val message: String? = null,
    val requestCount: Int = 0
)

class ViewerServer(
    private val context: Context,
    private val dao: MathDao
) {
    private val dispatcher = Executors
        .newSingleThreadExecutor { runnable -> Thread(runnable, "workbook-viewer-server") }
        .asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val random = SecureRandom()
    private val _state = MutableStateFlow(ViewerServerState())

    val state: StateFlow<ViewerServerState> = _state.asStateFlow()

    @Volatile private var serverSocket: ServerSocket? = null

    fun start() {
        if (serverSocket != null) return
        val host = findLocalWifiIp()
        if (host == null) {
            _state.update { it.copy(running = false, message = "같은 Wi-Fi에서 사용할 태블릿 IP를 찾지 못했습니다.") }
            return
        }
        val token = newToken()
        val server = runCatching {
            ServerSocket(0, 8, InetAddress.getByName(host))
        }.getOrElse { error ->
            _state.update { it.copy(running = false, message = "viewer 서버를 시작하지 못했습니다: ${error.message}") }
            return
        }
        serverSocket = server
        val url = "http://$host:${server.localPort}/?token=$token"
        _state.update {
            ViewerServerState(
                running = true,
                url = url,
                token = token,
                message = "viewer 연결 대기 중",
                requestCount = it.requestCount
            )
        }
        scope.launch {
            acceptLoop(server, token)
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        _state.update { ViewerServerState(message = "viewer 연결이 꺼졌습니다.", requestCount = it.requestCount) }
    }

    private suspend fun acceptLoop(server: ServerSocket, token: String) {
        while (!server.isClosed) {
            val client = runCatching { server.accept() }.getOrNull() ?: break
            handleClient(client, token)
        }
        if (serverSocket === server) {
            serverSocket = null
            _state.update { ViewerServerState(message = "viewer 서버가 종료되었습니다.", requestCount = it.requestCount) }
        }
    }

    private fun handleClient(client: Socket, token: String) {
        client.use { socket ->
            socket.soTimeout = 5_000
            val input = socket.getInputStream().bufferedReader(Charsets.UTF_8)
            val requestLine = input.readLine().orEmpty()
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
            }
            val parts = requestLine.split(" ")
            if (parts.size < 2 || parts[0] != "GET") {
                respondText(socket, 405, "text/plain; charset=utf-8", "GET only")
                return
            }
            val target = parts[1]
            val path = target.substringBefore("?")
            val query = parseQuery(target.substringAfter("?", ""))
            if (path == "/favicon.ico") {
                respondText(socket, 404, "text/plain; charset=utf-8", "")
                return
            }
            if (query["token"] != token) {
                respondText(socket, 403, "text/plain; charset=utf-8", "viewer token required")
                return
            }
            _state.update { it.copy(requestCount = it.requestCount + 1, message = "최근 viewer 요청 처리됨") }
            runCatching {
                when {
                    path == "/" -> respondText(socket, 200, "text/html; charset=utf-8", viewerHtml(token))
                    path == "/api/status" -> respondJson(socket, statusJson())
                    path == "/api/attempts" -> respondJson(socket, attemptsJson(query["limit"]?.toIntOrNull() ?: 40))
                    path.startsWith("/api/attempts/") && path.endsWith("/solution-vector") -> {
                        val attemptId = path.removePrefix("/api/attempts/").removeSuffix("/solution-vector").trim('/')
                        respondAttemptSolution(socket, attemptId)
                    }
                    path.startsWith("/api/attempts/") -> {
                        val attemptId = path.removePrefix("/api/attempts/").trim('/')
                        respondJson(socket, attemptDetailJson(attemptId))
                    }
                    path.startsWith("/api/problems/") && path.endsWith("/image") -> {
                        val problemId = path.removePrefix("/api/problems/").removeSuffix("/image").trim('/')
                        respondProblemImage(socket, problemId)
                    }
                    else -> respondText(socket, 404, "text/plain; charset=utf-8", "not found")
                }
            }.onFailure { error ->
                respondText(socket, 500, "text/plain; charset=utf-8", error.message ?: "viewer error")
            }
        }
    }

    private fun statusJson(): JSONObject {
        return JSONObject()
            .put("app", context.packageName)
            .put("running", true)
            .put("mode", "completed_attempts_only")
            .put("updatedAt", System.currentTimeMillis())
    }

    private fun attemptsJson(limit: Int): JSONObject = runBlocking {
        val attempts = dao.getCompletedPracticeAttempts(limit.coerceIn(1, 80))
        val items = JSONArray()
        attempts.forEach { attempt ->
            val problem = dao.getProblem(attempt.problemId)
            val workbook = dao.getWorkbook(attempt.workbookId)
            val chapter = dao.getChapter(attempt.chapterId)
            val logs = dao.getAttemptInputLogs(attempt.attemptId)
            items.put(
                JSONObject()
                    .put("attemptId", attempt.attemptId)
                    .put("attemptNumber", attempt.attemptNumber)
                    .put("problemId", attempt.problemId)
                    .put("problemOrder", problem?.orderIndex ?: 0)
                    .put("problemTitle", problemTitle(problem))
                    .put("workbookTitle", workbook?.title.orEmpty())
                    .put("chapterTitle", chapter?.title.orEmpty())
                    .put("status", attempt.finalStatus.name)
                    .put("isCorrect", attempt.isCorrect)
                    .put("submittedAt", attempt.submittedAt ?: attempt.startedAt)
                    .put("submittedAnswer", formatSubmittedAnswer(logs, dao.getAnswerFields(attempt.problemId)))
            )
        }
        JSONObject().put("items", items)
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

    private fun respondAttemptSolution(socket: Socket, attemptId: String) = runBlocking {
        val attempt = dao.getPracticeAttempt(attemptId) ?: error("attempt not found")
        if (attempt.finalStatus == FinalStatus.IN_PROGRESS) error("attempt is not completed")
        val file = attempt.solutionImagePath?.let(::File)?.takeIf { it.exists() }
        if (file == null) {
            respondText(socket, 404, "text/plain; charset=utf-8", "solution not found")
        } else {
            respondBytes(socket, 200, "application/json; charset=utf-8", file.readBytes())
        }
    }

    private fun respondProblemImage(socket: Socket, problemId: String) = runBlocking {
        val problem = dao.getProblem(problemId) ?: error("problem not found")
        val file = problem.imagePath?.let(::File)?.takeIf { it.exists() }
        if (file == null) {
            respondText(socket, 404, "text/plain; charset=utf-8", "image not found")
        } else {
            respondBytes(socket, 200, mimeFor(file), file.readBytes())
        }
    }

    private fun respondJson(socket: Socket, body: JSONObject) {
        respondText(socket, 200, "application/json; charset=utf-8", body.toString())
    }

    private fun respondText(socket: Socket, status: Int, contentType: String, body: String) {
        respondBytes(socket, status, contentType, body.toByteArray(Charsets.UTF_8))
    }

    private fun respondBytes(socket: Socket, status: Int, contentType: String, body: ByteArray) {
        val reason = when (status) {
            200 -> "OK"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            else -> "Error"
        }
        val header = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(Charsets.UTF_8)
        socket.getOutputStream().use { output ->
            output.write(header)
            output.write(body)
            output.flush()
        }
    }

    private fun viewerHtml(token: String): String {
        return """
            <!doctype html>
            <html lang="ko">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Workbook Viewer</title>
              <style>
                body{margin:0;font-family:system-ui,sans-serif;background:#f7f8fa;color:#111827}
                header{position:sticky;top:0;background:#ffffffee;border-bottom:1px solid #e5e7eb;padding:12px 14px;display:flex;align-items:center;justify-content:space-between;gap:10px}
                h1{font-size:18px;margin:0} small{color:#6b7280}
                main{padding:12px;display:grid;gap:10px}
                button,.card{border:1px solid #e5e7eb;border-radius:10px;background:white}
                button{padding:10px;text-align:left}
                .refresh{width:auto;padding:8px 12px;text-align:center;color:#2563eb;font-weight:700}
                .meta{font-size:12px;color:#6b7280}.ok{color:#15803d}.bad{color:#b91c1c}
                #detail{white-space:pre-wrap}.answer{display:inline-block;border:2px solid #2563eb;color:#2563eb;padding:6px 10px;border-radius:6px;font-weight:700}
                img{max-width:100%;border-radius:8px;border:1px solid #e5e7eb;background:white}
                canvas{width:100%;min-height:260px;background:white;border:1px solid #e5e7eb;border-radius:8px}
              </style>
            </head>
            <body>
              <header>
                <div><h1>Workbook Viewer</h1><small>완료된 풀이만 표시합니다.</small></div>
                <button class="refresh" onclick="loadList()">새로고침</button>
              </header>
              <main>
                <section id="list"></section>
                <section id="detail" class="card" style="padding:12px;display:none"></section>
              </main>
              <script>
                const token = new URLSearchParams(location.search).get('token') || '$token';
                const withToken = url => url + (url.includes('?') ? '&' : '?') + 'token=' + encodeURIComponent(token);
                async function loadList(){
                  const res = await fetch(withToken('/api/attempts?limit=50'));
                  const data = await res.json();
                  const list = document.getElementById('list');
                  list.innerHTML = '';
                  data.items.forEach(item => {
                    const b = document.createElement('button');
                    b.style.width = '100%';
                    b.innerHTML = '<b>' + esc(item.workbookTitle) + '</b><br>' +
                      esc(item.chapterTitle) + ' · ' + esc(item.problemTitle) +
                      '<div class="meta">' + new Date(item.submittedAt).toLocaleString() + ' · ' +
                      '<span class="' + (item.isCorrect ? 'ok' : 'bad') + '">' + esc(item.status) + '</span> · ' +
                      esc(item.submittedAnswer || '') + '</div>';
                    b.onclick = () => loadDetail(item.attemptId);
                    list.appendChild(b);
                  });
                }
                async function loadDetail(id){
                  const res = await fetch(withToken('/api/attempts/' + encodeURIComponent(id)));
                  const d = await res.json();
                  const box = document.getElementById('detail');
                  box.style.display = 'block';
                  box.innerHTML = '<h2 style="font-size:17px;margin-top:0">' + esc(d.workbookTitle) + '</h2>' +
                    '<div class="meta">' + esc(d.chapterTitle) + ' · ' + new Date(d.submittedAt).toLocaleString() + '</div>' +
                    '<p>' + esc(d.problem.questionText || '(이미지 문제)') + '</p>' +
                    (d.problemImageUrl ? '<img src="' + withToken(d.problemImageUrl) + '">' : '') +
                    '<p><span class="answer">' + esc(d.submittedAnswer || '') + '</span></p>' +
                    (d.reviewerComment ? '<p><b>마스터 노트</b><br>' + esc(d.reviewerComment) + '</p>' : '') +
                    '<canvas id="solutionCanvas"></canvas>';
                  drawSolution(withToken(d.solutionVectorUrl));
                }
                async function drawSolution(url){
                  const canvas = document.getElementById('solutionCanvas');
                  if(!canvas) return;
                  const ctx = canvas.getContext('2d');
                  try{
                    const data = await (await fetch(url)).json();
                    const strokes = data.strokes || [];
                    let maxX=1,maxY=1;
                    strokes.forEach(s => (s.points||[]).forEach(p => { maxX=Math.max(maxX,p.x); maxY=Math.max(maxY,p.y); }));
                    canvas.width = Math.max(320, maxX); canvas.height = Math.max(260, maxY);
                    ctx.clearRect(0,0,canvas.width,canvas.height);
                    strokes.forEach(s => {
                      const pts = s.points || []; if(pts.length < 2) return;
                      ctx.strokeStyle = s.color || '#111827'; ctx.lineWidth = s.width || 5;
                      ctx.lineCap = (s.kind === 'Highlighter') ? 'butt' : 'round';
                      ctx.lineJoin = (s.kind === 'Highlighter') ? 'bevel' : 'round';
                      ctx.beginPath(); ctx.moveTo(pts[0].x, pts[0].y);
                      pts.slice(1).forEach(p => ctx.lineTo(p.x, p.y)); ctx.stroke();
                    });
                  }catch(e){ canvas.style.display='none'; }
                }
                function esc(v){return String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
                loadList();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun problemTitle(problem: ProblemEntity?): String {
        return problem?.questionText
            ?.replace(Regex("\\s+"), " ")
            ?.take(42)
            ?.ifBlank { null }
            ?: "문제 ${problem?.orderIndex ?: ""}".trim()
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

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split("&").mapNotNull { part ->
            val key = part.substringBefore("=", "")
            if (key.isBlank()) return@mapNotNull null
            val value = part.substringAfter("=", "")
            decode(key) to decode(value)
        }.toMap()
    }

    private fun decode(value: String): String {
        return URLDecoder.decode(value, "UTF-8")
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
}
