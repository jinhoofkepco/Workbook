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

data class ViewerCurrentScreenSnapshot(
    val workbookTitle: String,
    val chapterTitle: String,
    val positionLabel: String,
    val problem: ProblemEntity?,
    val currentAnswer: String,
    val solutionVectorJson: String,
    val updatedAt: Long
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
    @Volatile private var currentScreenSnapshot: ViewerCurrentScreenSnapshot? = null

    fun updateCurrentScreen(snapshot: ViewerCurrentScreenSnapshot) {
        currentScreenSnapshot = snapshot
    }

    fun clearCurrentScreen() {
        currentScreenSnapshot = null
    }

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
        runCatching {
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
                    path == "/api/current-screen" -> respondJson(socket, currentScreenJson())
                    path == "/api/current-screen-vector" -> respondCurrentScreenVector(socket)
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
        }.onFailure { error ->
            _state.update { it.copy(message = "viewer 요청 처리 중 오류: ${error.message}") }
            runCatching { client.close() }
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

    private fun currentScreenJson(): JSONObject {
        val snapshot = currentScreenSnapshot
            ?: return JSONObject()
                .put("available", false)
                .put("message", "현재 풀이 중인 화면이 없습니다.")
        val problem = snapshot.problem
        return JSONObject()
            .put("available", true)
            .put("updatedAt", snapshot.updatedAt)
            .put("workbookTitle", snapshot.workbookTitle)
            .put("chapterTitle", snapshot.chapterTitle)
            .put("positionLabel", snapshot.positionLabel)
            .put("currentAnswer", snapshot.currentAnswer)
            .put("problem", problemJson(problem))
            .put("problemImageUrl", if (problem?.imagePath.isNullOrBlank()) JSONObject.NULL else "/api/problems/${problem?.problemId}/image")
            .put("solutionVectorUrl", "/api/current-screen-vector")
    }

    private fun respondCurrentScreenVector(socket: Socket) {
        val vectorJson = currentScreenSnapshot?.solutionVectorJson
            ?.takeIf { it.isNotBlank() }
            ?: """{"strokes":[]}"""
        respondText(socket, 200, "application/json; charset=utf-8", vectorJson)
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
        val stable = viewerGuardedHtml(token)
        if (stable.isNotBlank()) return stable
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

    private fun viewerGuardedHtml(token: String): String {
        return """
            <!doctype html>
            <html lang="ko">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Workbook Viewer</title>
              <style>
                *{box-sizing:border-box}
                body{margin:0;font-family:system-ui,sans-serif;background:#f7f8fa;color:#111827}
                header{position:sticky;top:0;background:#ffffffee;border-bottom:1px solid #e5e7eb;padding:12px 14px;display:flex;align-items:center;justify-content:space-between;gap:10px}
                h1{font-size:18px;margin:0} h2{font-size:17px;margin:0 0 8px} small{color:#6b7280}
                main{padding:12px;display:grid;gap:10px}
                button,.card{border:1px solid #e5e7eb;border-radius:10px;background:white}
                button{padding:10px;text-align:left}
                .refresh{width:auto;padding:8px 12px;text-align:center;color:#2563eb;font-weight:700}
                #list{display:grid;gap:10px}.attempt{overflow:hidden}.attempt.open{border-color:#93c5fd;box-shadow:0 2px 10px #93c5fd33}
                .attempt-button{width:100%;border:0;border-radius:0;background:transparent}.detail{border-top:1px solid #e5e7eb;padding:12px;background:#fff}
                .meta{font-size:12px;color:#6b7280}.ok{color:#15803d}.bad{color:#b91c1c}
                .detail,.question{white-space:pre-wrap}.answer{display:inline-block;border:2px solid #2563eb;color:#2563eb;padding:6px 10px;border-radius:6px;font-weight:700;background:#eff6ff}
                .stage{position:relative;width:100%;overflow:hidden;background:white;border:1px solid #e5e7eb;border-radius:8px;margin-top:8px}
                .stage img,.stage canvas{position:absolute;display:block}.stage img{border:0;background:white}.stage canvas{left:0;top:0;width:100%;height:100%;pointer-events:none}
                .empty{padding:18px;text-align:center;color:#6b7280}
              </style>
            </head>
            <body>
              <header>
                <div><h1>Workbook Viewer</h1><small>Refresh shows the tablet screen snapshot.</small></div>
                <button id="refreshButton" class="refresh" onclick="refreshAll()">Refresh</button>
              </header>
              <main>
                <section class="card" style="padding:12px">
                  <h2>Current screen</h2>
                  <div id="current" class="empty">Loading...</div>
                </section>
                <section id="list"></section>
              </main>
              <script>
                const token = new URLSearchParams(location.search).get('token') || '$token';
                const withToken = url => url + (url.includes('?') ? '&' : '?') + 'token=' + encodeURIComponent(token);
                let openAttemptId = null;
                const detailCache = new Map();
                let refreshInFlight = false;
                let lastRefreshAt = 0;
                const refreshCooldownMillis = 10000;

                async function refreshAll(){
                  const now = Date.now();
                  if(refreshInFlight) return;
                  if(lastRefreshAt > 0 && now - lastRefreshAt < refreshCooldownMillis){
                    updateRefreshButton();
                    return;
                  }
                  refreshInFlight = true;
                  lastRefreshAt = now;
                  updateRefreshButton();
                  try{
                    await loadCurrent();
                    await loadList();
                  }finally{
                    refreshInFlight = false;
                    updateRefreshButton();
                  }
                }

                function updateRefreshButton(){
                  const button = document.getElementById('refreshButton');
                  if(!button) return;
                  const remaining = Math.ceil((refreshCooldownMillis - (Date.now() - lastRefreshAt)) / 1000);
                  if(refreshInFlight){
                    button.disabled = true;
                    button.textContent = 'Loading';
                  }else if(lastRefreshAt > 0 && remaining > 0){
                    button.disabled = true;
                    button.textContent = remaining + 's';
                    setTimeout(updateRefreshButton, 250);
                  }else{
                    button.disabled = false;
                    button.textContent = 'Refresh';
                  }
                }

                async function loadCurrent(){
                  const box = document.getElementById('current');
                  try{
                    const data = await (await fetch(withToken('/api/current-screen'))).json();
                    if(!data.available){
                      box.className = 'empty';
                      box.innerHTML = esc(data.message || 'No current screen.');
                      return;
                    }
                    box.className = '';
                    box.innerHTML = '<div><b>' + esc(data.workbookTitle) + '</b></div>' +
                      '<div class="meta">' + esc(data.chapterTitle) + ' · ' + esc(data.positionLabel) + ' · ' +
                      new Date(data.updatedAt).toLocaleTimeString() + '</div>' +
                      '<div class="question">' + esc(data.problem.questionText || '(image problem)') + '</div>' +
                      '<div id="currentStage" class="stage"></div>' +
                      (data.currentAnswer ? '<p><span class="answer">' + esc(data.currentAnswer) + '</span></p>' : '');
                    const vector = await (await fetch(withToken(data.solutionVectorUrl))).json();
                    drawWorksheet('currentStage', data.problemImageUrl ? withToken(data.problemImageUrl) : null, vector);
                  }catch(e){
                    box.className = 'empty';
                    box.innerHTML = 'Could not load current screen.';
                  }
                }

                async function loadList(){
                  const res = await fetch(withToken('/api/attempts?limit=50'));
                  const data = await res.json();
                  const list = document.getElementById('list');
                  list.innerHTML = '';
                  (data.items || []).forEach(item => {
                    const id = String(item.attemptId || '');
                    const card = document.createElement('article');
                    card.className = 'card attempt' + (openAttemptId === id ? ' open' : '');
                    card.id = 'attempt-' + safeId(id);
                    const b = document.createElement('button');
                    b.className = 'attempt-button';
                    b.innerHTML = '<b>' + esc(item.workbookTitle) + '</b><br>' +
                      esc(item.chapterTitle) + ' · ' + esc(item.problemTitle) +
                      '<div class="meta">' + new Date(item.submittedAt).toLocaleString() + ' · ' +
                      '<span class="' + (item.isCorrect ? 'ok' : 'bad') + '">' + esc(item.status) + '</span> · ' +
                      esc(item.submittedAnswer || '') + '</div>';
                    const detail = document.createElement('div');
                    detail.className = 'detail';
                    detail.id = 'detail-' + safeId(id);
                    detail.style.display = openAttemptId === id ? 'block' : 'none';
                    if(openAttemptId === id) detail.innerHTML = '<div class="meta">Loading...</div>';
                    b.onclick = () => toggleDetail(id);
                    card.appendChild(b);
                    card.appendChild(detail);
                    list.appendChild(card);
                    if(openAttemptId === id) loadDetail(id);
                  });
                }

                async function toggleDetail(id){
                  const previousId = openAttemptId;
                  if(previousId && previousId !== id){
                    const previousCard = document.getElementById('attempt-' + safeId(previousId));
                    const previousDetail = document.getElementById('detail-' + safeId(previousId));
                    if(previousCard) previousCard.classList.remove('open');
                    if(previousDetail) previousDetail.style.display = 'none';
                  }
                  const card = document.getElementById('attempt-' + safeId(id));
                  const detail = document.getElementById('detail-' + safeId(id));
                  if(openAttemptId === id){
                    if(card) card.classList.remove('open');
                    if(detail) detail.style.display = 'none';
                    openAttemptId = null;
                    return;
                  }
                  openAttemptId = id;
                  if(card) card.classList.add('open');
                  if(detail){
                    detail.style.display = 'block';
                    if(detail.dataset.loaded !== '1'){
                      detail.innerHTML = '<div class="meta">Loading...</div>';
                    }
                  }
                  if(!detail || detail.dataset.loaded !== '1'){
                    await loadDetail(id);
                  }
                  if(card){
                    card.scrollIntoView({block:'nearest'});
                  }
                }

                async function loadDetail(id){
                  const box = document.getElementById('detail-' + safeId(id));
                  if(!box) return;
                  let cached = detailCache.get(id);
                  if(!cached){
                    const res = await fetch(withToken('/api/attempts/' + encodeURIComponent(id)));
                    const detail = await res.json();
                    let vector = null;
                    try{
                      vector = await (await fetch(withToken(detail.solutionVectorUrl))).json();
                    }catch(e){
                      vector = null;
                    }
                    cached = { detail, vector };
                    detailCache.set(id, cached);
                  }
                  const d = cached.detail;
                  const stageId = 'detailStage-' + safeId(id);
                  box.style.display = 'block';
                  box.innerHTML = '<h2>' + esc(d.workbookTitle) + '</h2>' +
                    '<div class="meta">' + esc(d.chapterTitle) + ' · ' + new Date(d.submittedAt).toLocaleString() + '</div>' +
                    '<div class="question">' + esc(d.problem.questionText || '(image problem)') + '</div>' +
                    '<div id="' + stageId + '" class="stage"></div>' +
                    '<p><span class="answer">' + esc(d.submittedAnswer || '') + '</span></p>' +
                    (d.reviewerComment ? '<p><b>Master note</b><br>' + esc(d.reviewerComment) + '</p>' : '');
                  if(cached.vector){
                    drawWorksheet(stageId, d.problemImageUrl ? withToken(d.problemImageUrl) : null, cached.vector);
                    box.dataset.loaded = '1';
                  }else{
                    const stage = document.getElementById(stageId);
                    if(stage) stage.innerHTML = '<div class="empty">Could not load solution.</div>';
                  }
                }

                function drawWorksheet(stageId, imageUrl, data){
                  const stage = document.getElementById(stageId);
                  if(!stage) return;
                  const strokes = data.strokes || [];
                  const bounds = data.imageBounds || null;
                  const box = worksheetCrop(data, strokes, bounds);
                  stage.style.aspectRatio = box.width + ' / ' + box.height;
                  stage.innerHTML = '';
                  if(imageUrl){
                    const img = document.createElement('img');
                    img.src = imageUrl;
                    if(bounds){
                      img.style.left = pct(bounds.left - box.left, box.width);
                      img.style.top = pct(bounds.top - box.top, box.height);
                      img.style.width = pct(bounds.width, box.width);
                      img.style.height = pct(bounds.height, box.height);
                    }else{
                      img.style.left = '0'; img.style.top = '0'; img.style.width = '100%'; img.style.height = '100%';
                    }
                    stage.appendChild(img);
                  }
                  const canvas = document.createElement('canvas');
                  canvas.width = Math.max(1, Math.round(box.width));
                  canvas.height = Math.max(1, Math.round(box.height));
                  stage.appendChild(canvas);
                  drawStrokes(canvas, strokes, box.left, box.top);
                }

                function worksheetCrop(data, strokes, bounds){
                  let minX = bounds ? bounds.left : 0;
                  let minY = bounds ? bounds.top : 0;
                  let maxX = bounds ? bounds.left + bounds.width : Number(data.contentWidth || 320);
                  let maxY = bounds ? bounds.top + bounds.height : Number(data.contentHeight || 260);
                  strokes.forEach(s => (s.points || []).forEach(p => {
                    minX = Math.min(minX, Number(p.x || 0)); minY = Math.min(minY, Number(p.y || 0));
                    maxX = Math.max(maxX, Number(p.x || 0)); maxY = Math.max(maxY, Number(p.y || 0));
                  }));
                  const margin = 24;
                  minX = Math.max(0, minX - margin);
                  minY = Math.max(0, minY - margin);
                  maxX += margin; maxY += margin;
                  return {left:minX, top:minY, width:Math.max(240, maxX - minX), height:Math.max(160, maxY - minY)};
                }

                function drawStrokes(canvas, strokes, offsetX, offsetY){
                  const ctx = canvas.getContext('2d');
                  ctx.clearRect(0,0,canvas.width,canvas.height);
                  strokes.forEach(s => {
                    const pts = s.points || []; if(pts.length < 1) return;
                    ctx.strokeStyle = cssColor(s.color || '#111827');
                    ctx.fillStyle = ctx.strokeStyle;
                    ctx.lineWidth = Number(s.width || 5);
                    ctx.lineCap = (s.kind === 'Highlighter') ? 'butt' : 'round';
                    ctx.lineJoin = (s.kind === 'Highlighter') ? 'bevel' : 'round';
                    if(pts.length === 1){
                      ctx.beginPath(); ctx.arc(pts[0].x - offsetX, pts[0].y - offsetY, ctx.lineWidth / 2, 0, Math.PI * 2); ctx.fill();
                      return;
                    }
                    ctx.beginPath(); ctx.moveTo(pts[0].x - offsetX, pts[0].y - offsetY);
                    pts.slice(1).forEach(p => ctx.lineTo(p.x - offsetX, p.y - offsetY)); ctx.stroke();
                  });
                }

                function pct(value, total){return (value / Math.max(1, total) * 100) + '%';}
                function cssColor(value){
                  const raw = String(value || '');
                  if(/^#[0-9a-fA-F]{8}${'$'}/.test(raw)){
                    const a = parseInt(raw.slice(1,3), 16) / 255;
                    const r = parseInt(raw.slice(3,5), 16);
                    const g = parseInt(raw.slice(5,7), 16);
                    const b = parseInt(raw.slice(7,9), 16);
                    return 'rgba(' + r + ',' + g + ',' + b + ',' + a.toFixed(3) + ')';
                  }
                  return raw;
                }
                function esc(v){return String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
                function safeId(v){return String(v ?? '').replace(/[^a-zA-Z0-9_-]/g, '_');}
                refreshAll();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun viewerAccordionHtml(token: String): String {
        return """
            <!doctype html>
            <html lang="ko">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Workbook Viewer</title>
              <style>
                *{box-sizing:border-box}
                body{margin:0;font-family:system-ui,sans-serif;background:#f7f8fa;color:#111827}
                header{position:sticky;top:0;background:#ffffffee;border-bottom:1px solid #e5e7eb;padding:12px 14px;display:flex;align-items:center;justify-content:space-between;gap:10px}
                h1{font-size:18px;margin:0} h2{font-size:17px;margin:0 0 8px} small{color:#6b7280}
                main{padding:12px;display:grid;gap:10px}
                button,.card{border:1px solid #e5e7eb;border-radius:10px;background:white}
                button{padding:10px;text-align:left}
                .refresh{width:auto;padding:8px 12px;text-align:center;color:#2563eb;font-weight:700}
                #list{display:grid;gap:10px}.attempt{overflow:hidden}.attempt.open{border-color:#93c5fd;box-shadow:0 2px 10px #93c5fd33}
                .attempt-button{width:100%;border:0;border-radius:0;background:transparent}.detail{border-top:1px solid #e5e7eb;padding:12px;background:#fff}
                .meta{font-size:12px;color:#6b7280}.ok{color:#15803d}.bad{color:#b91c1c}
                .detail,.question{white-space:pre-wrap}.answer{display:inline-block;border:2px solid #2563eb;color:#2563eb;padding:6px 10px;border-radius:6px;font-weight:700;background:#eff6ff}
                .stage{position:relative;width:100%;overflow:hidden;background:white;border:1px solid #e5e7eb;border-radius:8px;margin-top:8px}
                .stage img,.stage canvas{position:absolute;display:block}.stage img{border:0;background:white}.stage canvas{left:0;top:0;width:100%;height:100%;pointer-events:none}
                .empty{padding:18px;text-align:center;color:#6b7280}
              </style>
            </head>
            <body>
              <header>
                <div><h1>Workbook Viewer</h1><small>새로고침 시점의 화면을 표시합니다.</small></div>
                <button id="refreshButton" class="refresh" onclick="refreshAll()">새로고침</button>
              </header>
              <main>
                <section class="card" style="padding:12px">
                  <h2>현재 풀이 화면</h2>
                  <div id="current" class="empty">불러오는 중...</div>
                </section>
                <section id="list"></section>
              </main>
              <script>
                const token = new URLSearchParams(location.search).get('token') || '$token';
                const withToken = url => url + (url.includes('?') ? '&' : '?') + 'token=' + encodeURIComponent(token);
                let openAttemptId = null;
                let refreshInFlight = false;
                let lastRefreshAt = 0;
                const refreshCooldownMillis = 10000;

                async function refreshAll(){
                  const now = Date.now();
                  if(refreshInFlight) return;
                  if(lastRefreshAt > 0 && now - lastRefreshAt < refreshCooldownMillis){
                    updateRefreshButton();
                    return;
                  }
                  refreshInFlight = true;
                  lastRefreshAt = now;
                  updateRefreshButton();
                  try{
                    await loadCurrent();
                    await loadList();
                  }finally{
                    refreshInFlight = false;
                    updateRefreshButton();
                  }
                }

                function updateRefreshButton(){
                  const button = document.getElementById('refreshButton');
                  if(!button) return;
                  const elapsed = Date.now() - lastRefreshAt;
                  const remaining = Math.ceil((refreshCooldownMillis - elapsed) / 1000);
                  if(refreshInFlight){
                    button.disabled = true;
                    button.textContent = '불러오는 중';
                  }else if(lastRefreshAt > 0 && remaining > 0){
                    button.disabled = true;
                    button.textContent = remaining + '초 후';
                    setTimeout(updateRefreshButton, 250);
                  }else{
                    button.disabled = false;
                    button.textContent = '새로고침';
                  }
                }

                async function loadCurrent(){
                  const box = document.getElementById('current');
                  try{
                    const data = await (await fetch(withToken('/api/current-screen'))).json();
                    if(!data.available){
                      box.className = 'empty';
                      box.innerHTML = esc(data.message || '현재 풀이 중인 화면이 없습니다.');
                      return;
                    }
                    box.className = '';
                    box.innerHTML = '<div><b>' + esc(data.workbookTitle) + '</b></div>' +
                      '<div class="meta">' + esc(data.chapterTitle) + ' · ' + esc(data.positionLabel) + ' · ' +
                      new Date(data.updatedAt).toLocaleTimeString() + '</div>' +
                      '<div class="question">' + esc(data.problem.questionText || '(이미지 문제)') + '</div>' +
                      '<div id="currentStage" class="stage"></div>' +
                      (data.currentAnswer ? '<p><span class="answer">' + esc(data.currentAnswer) + '</span></p>' : '');
                    const vector = await (await fetch(withToken(data.solutionVectorUrl))).json();
                    drawWorksheet('currentStage', data.problemImageUrl ? withToken(data.problemImageUrl) : null, vector);
                  }catch(e){
                    box.className = 'empty';
                    box.innerHTML = '현재 화면을 불러오지 못했습니다.';
                  }
                }

                async function loadList(){
                  const res = await fetch(withToken('/api/attempts?limit=50'));
                  const data = await res.json();
                  const list = document.getElementById('list');
                  list.innerHTML = '';
                  (data.items || []).forEach(item => {
                    const id = String(item.attemptId || '');
                    const card = document.createElement('article');
                    card.className = 'card attempt' + (openAttemptId === id ? ' open' : '');
                    card.id = 'attempt-' + safeId(id);
                    const b = document.createElement('button');
                    b.className = 'attempt-button';
                    b.innerHTML = '<b>' + esc(item.workbookTitle) + '</b><br>' +
                      esc(item.chapterTitle) + ' · ' + esc(item.problemTitle) +
                      '<div class="meta">' + new Date(item.submittedAt).toLocaleString() + ' · ' +
                      '<span class="' + (item.isCorrect ? 'ok' : 'bad') + '">' + esc(item.status) + '</span> · ' +
                      esc(item.submittedAnswer || '') + '</div>';
                    const detail = document.createElement('div');
                    detail.className = 'detail';
                    detail.id = 'detail-' + safeId(id);
                    detail.style.display = openAttemptId === id ? 'block' : 'none';
                    if(openAttemptId === id) detail.innerHTML = '<div class="meta">불러오는 중...</div>';
                    b.onclick = () => toggleDetail(id);
                    card.appendChild(b);
                    card.appendChild(detail);
                    list.appendChild(card);
                    if(openAttemptId === id) loadDetail(id);
                  });
                }

                async function toggleDetail(id){
                  openAttemptId = openAttemptId === id ? null : id;
                  await loadList();
                  if(openAttemptId){
                    const card = document.getElementById('attempt-' + safeId(id));
                    if(card) card.scrollIntoView({block:'nearest'});
                  }
                }

                async function loadDetail(id){
                  const res = await fetch(withToken('/api/attempts/' + encodeURIComponent(id)));
                  const d = await res.json();
                  const box = document.getElementById('detail-' + safeId(id));
                  if(!box) return;
                  const stageId = 'detailStage-' + safeId(id);
                  box.style.display = 'block';
                  box.innerHTML = '<h2>' + esc(d.workbookTitle) + '</h2>' +
                    '<div class="meta">' + esc(d.chapterTitle) + ' · ' + new Date(d.submittedAt).toLocaleString() + '</div>' +
                    '<div class="question">' + esc(d.problem.questionText || '(이미지 문제)') + '</div>' +
                    '<div id="' + stageId + '" class="stage"></div>' +
                    '<p><span class="answer">' + esc(d.submittedAnswer || '') + '</span></p>' +
                    (d.reviewerComment ? '<p><b>마스터 노트</b><br>' + esc(d.reviewerComment) + '</p>' : '');
                  try{
                    const vector = await (await fetch(withToken(d.solutionVectorUrl))).json();
                    drawWorksheet(stageId, d.problemImageUrl ? withToken(d.problemImageUrl) : null, vector);
                  }catch(e){
                    const stage = document.getElementById(stageId);
                    if(stage) stage.innerHTML = '<div class="empty">풀이를 불러오지 못했습니다.</div>';
                  }
                }

                function drawWorksheet(stageId, imageUrl, data){
                  const stage = document.getElementById(stageId);
                  if(!stage) return;
                  const strokes = data.strokes || [];
                  const bounds = data.imageBounds || null;
                  const box = worksheetCrop(data, strokes, bounds);
                  stage.style.aspectRatio = box.width + ' / ' + box.height;
                  stage.innerHTML = '';
                  if(imageUrl){
                    const img = document.createElement('img');
                    img.src = imageUrl;
                    if(bounds){
                      img.style.left = pct(bounds.left - box.left, box.width);
                      img.style.top = pct(bounds.top - box.top, box.height);
                      img.style.width = pct(bounds.width, box.width);
                      img.style.height = pct(bounds.height, box.height);
                    }else{
                      img.style.left = '0'; img.style.top = '0'; img.style.width = '100%'; img.style.height = '100%';
                    }
                    stage.appendChild(img);
                  }
                  const canvas = document.createElement('canvas');
                  canvas.width = Math.max(1, Math.round(box.width));
                  canvas.height = Math.max(1, Math.round(box.height));
                  stage.appendChild(canvas);
                  drawStrokes(canvas, strokes, box.left, box.top);
                }

                function worksheetCrop(data, strokes, bounds){
                  let minX = bounds ? bounds.left : 0;
                  let minY = bounds ? bounds.top : 0;
                  let maxX = bounds ? bounds.left + bounds.width : Number(data.contentWidth || 320);
                  let maxY = bounds ? bounds.top + bounds.height : Number(data.contentHeight || 260);
                  strokes.forEach(s => (s.points || []).forEach(p => {
                    minX = Math.min(minX, Number(p.x || 0)); minY = Math.min(minY, Number(p.y || 0));
                    maxX = Math.max(maxX, Number(p.x || 0)); maxY = Math.max(maxY, Number(p.y || 0));
                  }));
                  const margin = 24;
                  minX = Math.max(0, minX - margin);
                  minY = Math.max(0, minY - margin);
                  maxX += margin; maxY += margin;
                  return {left:minX, top:minY, width:Math.max(240, maxX - minX), height:Math.max(160, maxY - minY)};
                }

                function drawStrokes(canvas, strokes, offsetX, offsetY){
                  const ctx = canvas.getContext('2d');
                  ctx.clearRect(0,0,canvas.width,canvas.height);
                  strokes.forEach(s => {
                    const pts = s.points || []; if(pts.length < 1) return;
                    ctx.strokeStyle = cssColor(s.color || '#111827');
                    ctx.fillStyle = ctx.strokeStyle;
                    ctx.lineWidth = Number(s.width || 5);
                    ctx.lineCap = (s.kind === 'Highlighter') ? 'butt' : 'round';
                    ctx.lineJoin = (s.kind === 'Highlighter') ? 'bevel' : 'round';
                    if(pts.length === 1){
                      ctx.beginPath(); ctx.arc(pts[0].x - offsetX, pts[0].y - offsetY, ctx.lineWidth / 2, 0, Math.PI * 2); ctx.fill();
                      return;
                    }
                    ctx.beginPath(); ctx.moveTo(pts[0].x - offsetX, pts[0].y - offsetY);
                    pts.slice(1).forEach(p => ctx.lineTo(p.x - offsetX, p.y - offsetY)); ctx.stroke();
                  });
                }

                function pct(value, total){return (value / Math.max(1, total) * 100) + '%';}
                function cssColor(value){
                  const raw = String(value || '');
                  if(/^#[0-9a-fA-F]{8}${'$'}/.test(raw)){
                    const a = parseInt(raw.slice(1,3), 16) / 255;
                    const r = parseInt(raw.slice(3,5), 16);
                    const g = parseInt(raw.slice(5,7), 16);
                    const b = parseInt(raw.slice(7,9), 16);
                    return 'rgba(' + r + ',' + g + ',' + b + ',' + a.toFixed(3) + ')';
                  }
                  return raw;
                }
                function esc(v){return String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
                function safeId(v){return String(v ?? '').replace(/[^a-zA-Z0-9_-]/g, '_');}
                refreshAll();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun viewerStableHtml(token: String): String {
        return """
            <!doctype html>
            <html lang="ko">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Workbook Viewer</title>
              <style>
                *{box-sizing:border-box}
                body{margin:0;font-family:system-ui,sans-serif;background:#f7f8fa;color:#111827}
                header{position:sticky;top:0;background:#ffffffee;border-bottom:1px solid #e5e7eb;padding:12px 14px;display:flex;align-items:center;justify-content:space-between;gap:10px}
                h1{font-size:18px;margin:0} h2{font-size:17px;margin:0 0 8px} small{color:#6b7280}
                main{padding:12px;display:grid;gap:10px}
                button,.card{border:1px solid #e5e7eb;border-radius:10px;background:white}
                button{padding:10px;text-align:left}
                .refresh{width:auto;padding:8px 12px;text-align:center;color:#2563eb;font-weight:700}
                .meta{font-size:12px;color:#6b7280}.ok{color:#15803d}.bad{color:#b91c1c}
                #detail,.question{white-space:pre-wrap}.answer{display:inline-block;border:2px solid #2563eb;color:#2563eb;padding:6px 10px;border-radius:6px;font-weight:700;background:#eff6ff}
                .stage{position:relative;width:100%;overflow:hidden;background:white;border:1px solid #e5e7eb;border-radius:8px;margin-top:8px}
                .stage img,.stage canvas{position:absolute;display:block}.stage img{border:0;background:white}.stage canvas{left:0;top:0;width:100%;height:100%;pointer-events:none}
                .empty{padding:18px;text-align:center;color:#6b7280}
              </style>
            </head>
            <body>
              <header>
                <div><h1>Workbook Viewer</h1><small>새로고침 시점의 화면을 표시합니다.</small></div>
                <button class="refresh" onclick="refreshAll()">새로고침</button>
              </header>
              <main>
                <section class="card" style="padding:12px">
                  <h2>현재 풀이 화면</h2>
                  <div id="current" class="empty">불러오는 중...</div>
                </section>
                <section id="list"></section>
                <section id="detail" class="card" style="padding:12px;display:none"></section>
              </main>
              <script>
                const token = new URLSearchParams(location.search).get('token') || '$token';
                const withToken = url => url + (url.includes('?') ? '&' : '?') + 'token=' + encodeURIComponent(token);

                async function refreshAll(){
                  await loadCurrent();
                  await loadList();
                }

                async function loadCurrent(){
                  const box = document.getElementById('current');
                  try{
                    const data = await (await fetch(withToken('/api/current-screen'))).json();
                    if(!data.available){
                      box.className = 'empty';
                      box.innerHTML = esc(data.message || '현재 풀이 중인 화면이 없습니다.');
                      return;
                    }
                    box.className = '';
                    box.innerHTML = '<div><b>' + esc(data.workbookTitle) + '</b></div>' +
                      '<div class="meta">' + esc(data.chapterTitle) + ' · ' + esc(data.positionLabel) + ' · ' +
                      new Date(data.updatedAt).toLocaleTimeString() + '</div>' +
                      '<div class="question">' + esc(data.problem.questionText || '(이미지 문제)') + '</div>' +
                      '<div id="currentStage" class="stage"></div>' +
                      (data.currentAnswer ? '<p><span class="answer">' + esc(data.currentAnswer) + '</span></p>' : '');
                    drawWorksheet('currentStage', data.problemImageUrl ? withToken(data.problemImageUrl) : null, data.solutionVector || {});
                  }catch(e){
                    box.className = 'empty';
                    box.innerHTML = '현재 화면을 불러오지 못했습니다.';
                  }
                }

                async function loadList(){
                  const res = await fetch(withToken('/api/attempts?limit=50'));
                  const data = await res.json();
                  const list = document.getElementById('list');
                  list.innerHTML = '';
                  (data.items || []).forEach(item => {
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
                  box.innerHTML = '<h2>' + esc(d.workbookTitle) + '</h2>' +
                    '<div class="meta">' + esc(d.chapterTitle) + ' · ' + new Date(d.submittedAt).toLocaleString() + '</div>' +
                    '<div class="question">' + esc(d.problem.questionText || '(이미지 문제)') + '</div>' +
                    '<div id="detailStage" class="stage"></div>' +
                    '<p><span class="answer">' + esc(d.submittedAnswer || '') + '</span></p>' +
                    (d.reviewerComment ? '<p><b>마스터 노트</b><br>' + esc(d.reviewerComment) + '</p>' : '');
                  try{
                    const vector = await (await fetch(withToken(d.solutionVectorUrl))).json();
                    drawWorksheet('detailStage', d.problemImageUrl ? withToken(d.problemImageUrl) : null, vector);
                  }catch(e){
                    document.getElementById('detailStage').innerHTML = '<div class="empty">풀이를 불러오지 못했습니다.</div>';
                  }
                }

                function drawWorksheet(stageId, imageUrl, data){
                  const stage = document.getElementById(stageId);
                  if(!stage) return;
                  const strokes = data.strokes || [];
                  const bounds = data.imageBounds || null;
                  const box = worksheetCrop(data, strokes, bounds);
                  stage.style.aspectRatio = box.width + ' / ' + box.height;
                  stage.innerHTML = '';
                  if(imageUrl){
                    const img = document.createElement('img');
                    img.src = imageUrl;
                    if(bounds){
                      img.style.left = pct(bounds.left - box.left, box.width);
                      img.style.top = pct(bounds.top - box.top, box.height);
                      img.style.width = pct(bounds.width, box.width);
                      img.style.height = pct(bounds.height, box.height);
                    }else{
                      img.style.left = '0'; img.style.top = '0'; img.style.width = '100%'; img.style.height = '100%';
                    }
                    stage.appendChild(img);
                  }
                  const canvas = document.createElement('canvas');
                  canvas.width = Math.max(1, Math.round(box.width));
                  canvas.height = Math.max(1, Math.round(box.height));
                  stage.appendChild(canvas);
                  drawStrokes(canvas, strokes, box.left, box.top);
                }

                function worksheetCrop(data, strokes, bounds){
                  let minX = bounds ? bounds.left : 0;
                  let minY = bounds ? bounds.top : 0;
                  let maxX = bounds ? bounds.left + bounds.width : Number(data.contentWidth || 320);
                  let maxY = bounds ? bounds.top + bounds.height : Number(data.contentHeight || 260);
                  strokes.forEach(s => (s.points || []).forEach(p => {
                    minX = Math.min(minX, Number(p.x || 0)); minY = Math.min(minY, Number(p.y || 0));
                    maxX = Math.max(maxX, Number(p.x || 0)); maxY = Math.max(maxY, Number(p.y || 0));
                  }));
                  const margin = 24;
                  minX = Math.max(0, minX - margin);
                  minY = Math.max(0, minY - margin);
                  maxX += margin; maxY += margin;
                  return {left:minX, top:minY, width:Math.max(240, maxX - minX), height:Math.max(160, maxY - minY)};
                }

                function drawStrokes(canvas, strokes, offsetX, offsetY){
                  const ctx = canvas.getContext('2d');
                  ctx.clearRect(0,0,canvas.width,canvas.height);
                  strokes.forEach(s => {
                    const pts = s.points || []; if(pts.length < 1) return;
                    ctx.strokeStyle = cssColor(s.color || '#111827');
                    ctx.fillStyle = ctx.strokeStyle;
                    ctx.lineWidth = Number(s.width || 5);
                    ctx.lineCap = (s.kind === 'Highlighter') ? 'butt' : 'round';
                    ctx.lineJoin = (s.kind === 'Highlighter') ? 'bevel' : 'round';
                    if(pts.length === 1){
                      ctx.beginPath(); ctx.arc(pts[0].x - offsetX, pts[0].y - offsetY, ctx.lineWidth / 2, 0, Math.PI * 2); ctx.fill();
                      return;
                    }
                    ctx.beginPath(); ctx.moveTo(pts[0].x - offsetX, pts[0].y - offsetY);
                    pts.slice(1).forEach(p => ctx.lineTo(p.x - offsetX, p.y - offsetY)); ctx.stroke();
                  });
                }

                function pct(value, total){return (value / Math.max(1, total) * 100) + '%';}
                function cssColor(value){
                  const raw = String(value || '');
                  if(/^#[0-9a-fA-F]{8}${'$'}/.test(raw)){
                    const a = parseInt(raw.slice(1,3), 16) / 255;
                    const r = parseInt(raw.slice(3,5), 16);
                    const g = parseInt(raw.slice(5,7), 16);
                    const b = parseInt(raw.slice(7,9), 16);
                    return 'rgba(' + r + ',' + g + ',' + b + ',' + a.toFixed(3) + ')';
                  }
                  return raw;
                }
                function esc(v){return String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
                refreshAll();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun viewerHtmlV2(token: String): String {
        return """
            <!doctype html>
            <html lang="ko">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Workbook Viewer</title>
              <style>
                *{box-sizing:border-box}
                body{margin:0;font-family:system-ui,sans-serif;background:#f7f8fa;color:#111827}
                header{position:sticky;top:0;z-index:10;background:#ffffffee;border-bottom:1px solid #e5e7eb;padding:12px 14px;display:flex;align-items:center;justify-content:space-between;gap:10px}
                h1{font-size:18px;margin:0} h2{font-size:16px;margin:0 0 8px} small{color:#6b7280}
                main{padding:12px;display:grid;gap:10px}
                button,.card{border:1px solid #e5e7eb;border-radius:10px;background:white}
                button{padding:10px;text-align:left}
                .header-actions{display:flex;gap:8px;align-items:center}
                .refresh{width:auto;padding:8px 12px;text-align:center;color:#2563eb;font-weight:700}
                .attempt{overflow:hidden}
                .attempt.unread{background:#fff8cf;border-color:#f2da79}
                .attempt.open{border-color:#93c5fd;box-shadow:0 2px 10px #93c5fd33}
                .attempt-button{width:100%;border:0;border-radius:0;background:transparent}
                .meta{font-size:12px;color:#6b7280}.ok{color:#15803d}.bad{color:#b91c1c}
                .detail{border-top:1px solid #e5e7eb;padding:12px;background:#fff}
                .question{white-space:pre-wrap;margin:8px 0 10px}
                .answer{display:inline-block;border:2px solid #2563eb;color:#2563eb;padding:5px 9px;border-radius:6px;font-weight:700;background:#eff6ff}
                .worksheet-stage{position:relative;width:100%;overflow:hidden;background:white;border:1px solid #e5e7eb;border-radius:8px;margin-top:8px}
                .worksheet-stage img,.worksheet-stage canvas{position:absolute;display:block}
                .worksheet-stage img{border:0;border-radius:0;background:white}
                .worksheet-stage canvas{left:0;top:0;width:100%;height:100%;pointer-events:none}
                .empty{padding:20px;text-align:center;color:#6b7280}
              </style>
            </head>
            <body>
              <header>
                <div><h1>Workbook Viewer</h1><small>완료된 풀이만 표시합니다.</small></div>
                <div class="header-actions">
                  <button class="refresh" onclick="enableNotifications()">알림</button>
                  <button class="refresh" onclick="loadList()">새로고침</button>
                </div>
              </header>
              <main>
                <section id="list"></section>
              </main>
              <script>
                const token = new URLSearchParams(location.search).get('token') || '$token';
                const withToken = url => url + (url.includes('?') ? '&' : '?') + 'token=' + encodeURIComponent(token);
                let openAttemptId = null;
                let knownLatestSubmittedAt = 0;
                let notificationsEnabled = false;

                async function loadList(options = {}){
                  const res = await fetch(withToken('/api/attempts?limit=50'));
                  const data = await res.json();
                  const items = data.items || [];
                  const latest = items.reduce((max, item) => Math.max(max, Number(item.submittedAt || 0)), 0);
                  if (options.poll && notificationsEnabled && knownLatestSubmittedAt > 0) {
                    const fresh = items.filter(item => Number(item.submittedAt || 0) > knownLatestSubmittedAt);
                    if (fresh.length > 0) notifyFreshAttempts(fresh);
                  }
                  knownLatestSubmittedAt = Math.max(knownLatestSubmittedAt, latest);
                  renderList(items);
                }

                function renderList(items){
                  const list = document.getElementById('list');
                  list.innerHTML = '';
                  if (!items.length) {
                    list.innerHTML = '<div class="card empty">아직 완료된 풀이가 없습니다.</div>';
                    return;
                  }
                  items.forEach(item => {
                    const card = document.createElement('article');
                    card.className = 'card attempt' + (isSeen(item.attemptId) ? '' : ' unread') + (openAttemptId === item.attemptId ? ' open' : '');
                    card.id = 'attempt-' + item.attemptId;
                    const b = document.createElement('button');
                    b.className = 'attempt-button';
                    b.innerHTML = '<b>' + esc(item.workbookTitle) + '</b><br>' +
                      esc(item.chapterTitle) + ' · ' + esc(item.problemTitle) +
                      '<div class="meta">' + new Date(item.submittedAt).toLocaleString() + ' · ' +
                      '<span class="' + (item.isCorrect ? 'ok' : 'bad') + '">' + esc(item.status) + '</span> · ' +
                      esc(item.submittedAnswer || '') + '</div>';
                    const detail = document.createElement('div');
                    detail.className = 'detail';
                    detail.style.display = openAttemptId === item.attemptId ? 'block' : 'none';
                    if (openAttemptId === item.attemptId) detail.innerHTML = '<div class="meta">불러오는 중...</div>';
                    b.onclick = () => toggleDetail(item.attemptId);
                    card.appendChild(b);
                    card.appendChild(detail);
                    list.appendChild(card);
                    if (openAttemptId === item.attemptId) loadDetail(item.attemptId, false);
                  });
                }

                async function toggleDetail(id){
                  openAttemptId = openAttemptId === id ? null : id;
                  markSeen(id);
                  await loadList();
                  if (openAttemptId) {
                    const el = document.getElementById('attempt-' + id);
                    if (el) el.scrollIntoView({block:'nearest'});
                  }
                }

                async function loadDetail(id, mark = true){
                  if (mark) markSeen(id);
                  const res = await fetch(withToken('/api/attempts/' + encodeURIComponent(id)));
                  const d = await res.json();
                  const card = document.getElementById('attempt-' + id);
                  const box = card ? card.querySelector('.detail') : null;
                  if (!box) return;
                  box.style.display = 'block';
                  const stageId = 'stage-' + id.replace(/[^a-zA-Z0-9_-]/g, '');
                  box.innerHTML = '<h2>' + esc(d.workbookTitle) + '</h2>' +
                    '<div class="meta">' + esc(d.chapterTitle) + ' · ' + new Date(d.submittedAt).toLocaleString() + '</div>' +
                    '<div class="question">' + esc(d.problem.questionText || '(이미지 문제)') + '</div>' +
                    '<div id="' + stageId + '" class="worksheet-stage"></div>' +
                    '<p><span class="answer">' + esc(d.submittedAnswer || '') + '</span></p>' +
                    (d.reviewerComment ? '<p><b>마스터 노트</b><br>' + esc(d.reviewerComment) + '</p>' : '');
                  drawWorksheet(stageId, d.problemImageUrl ? withToken(d.problemImageUrl) : null, withToken(d.solutionVectorUrl));
                }

                async function drawWorksheet(stageId, imageUrl, vectorUrl){
                  const stage = document.getElementById(stageId);
                  if(!stage) return;
                  try{
                    const data = await (await fetch(vectorUrl)).json();
                    const strokes = data.strokes || [];
                    const bounds = data.imageBounds || null;
                    const box = worksheetCrop(data, strokes, bounds);
                    stage.style.aspectRatio = box.width + ' / ' + box.height;
                    stage.innerHTML = '';
                    if(imageUrl){
                      const img = document.createElement('img');
                      img.src = imageUrl;
                      if(bounds){
                        img.style.left = pct(bounds.left - box.left, box.width);
                        img.style.top = pct(bounds.top - box.top, box.height);
                        img.style.width = pct(bounds.width, box.width);
                        img.style.height = pct(bounds.height, box.height);
                      }else{
                        img.style.left = '0'; img.style.top = '0'; img.style.width = '100%'; img.style.height = '100%';
                      }
                      stage.appendChild(img);
                    }
                    const canvas = document.createElement('canvas');
                    canvas.width = Math.max(1, Math.round(box.width));
                    canvas.height = Math.max(1, Math.round(box.height));
                    stage.appendChild(canvas);
                    drawStrokes(canvas, strokes, box.left, box.top);
                  }catch(e){
                    stage.innerHTML = imageUrl ? '<img src="' + imageUrl + '" style="position:static;width:100%;height:auto">' : '<div class="empty">풀이를 불러오지 못했습니다.</div>';
                  }
                }

                function worksheetCrop(data, strokes, bounds){
                  let minX = bounds ? bounds.left : 0;
                  let minY = bounds ? bounds.top : 0;
                  let maxX = bounds ? bounds.left + bounds.width : Number(data.contentWidth || 320);
                  let maxY = bounds ? bounds.top + bounds.height : Number(data.contentHeight || 260);
                  strokes.forEach(s => (s.points || []).forEach(p => {
                    minX = Math.min(minX, Number(p.x || 0)); minY = Math.min(minY, Number(p.y || 0));
                    maxX = Math.max(maxX, Number(p.x || 0)); maxY = Math.max(maxY, Number(p.y || 0));
                  }));
                  const margin = 24;
                  minX = Math.max(0, minX - margin);
                  minY = Math.max(0, minY - margin);
                  maxX += margin;
                  maxY += margin;
                  return {left:minX, top:minY, width:Math.max(240, maxX - minX), height:Math.max(160, maxY - minY)};
                }

                function drawStrokes(canvas, strokes, offsetX, offsetY){
                  const ctx = canvas.getContext('2d');
                  ctx.clearRect(0,0,canvas.width,canvas.height);
                  strokes.forEach(s => {
                    const pts = s.points || []; if(pts.length < 1) return;
                    ctx.strokeStyle = cssColor(s.color || '#111827');
                    ctx.fillStyle = ctx.strokeStyle;
                    ctx.lineWidth = Number(s.width || 5);
                    ctx.lineCap = (s.kind === 'Highlighter') ? 'butt' : 'round';
                    ctx.lineJoin = (s.kind === 'Highlighter') ? 'bevel' : 'round';
                    if(pts.length === 1){
                      ctx.beginPath(); ctx.arc(pts[0].x - offsetX, pts[0].y - offsetY, ctx.lineWidth / 2, 0, Math.PI * 2); ctx.fill();
                      return;
                    }
                    ctx.beginPath(); ctx.moveTo(pts[0].x - offsetX, pts[0].y - offsetY);
                    pts.slice(1).forEach(p => ctx.lineTo(p.x - offsetX, p.y - offsetY)); ctx.stroke();
                  });
                }

                function enableNotifications(){
                  if(!('Notification' in window)){
                    alert('이 브라우저는 알림을 지원하지 않습니다.');
                    return;
                  }
                  Notification.requestPermission().then(permission => {
                    notificationsEnabled = permission === 'granted';
                    if(notificationsEnabled) new Notification('Workbook Viewer', {body:'viewer가 열려 있는 동안 새 풀이를 알려드립니다.'});
                  });
                }

                function notifyFreshAttempts(items){
                  const title = items.length === 1 ? '새 풀이가 도착했습니다' : '새 풀이 ' + items.length + '개가 도착했습니다';
                  const body = items[0] ? (items[0].problemTitle + ' · ' + (items[0].submittedAnswer || '')) : '';
                  if('Notification' in window && Notification.permission === 'granted') {
                    new Notification(title, {body});
                  }
                  if(navigator.vibrate) navigator.vibrate([80, 40, 80]);
                }

                function isSeen(id){return localStorage.getItem('viewerSeen:' + id) === '1';}
                function markSeen(id){localStorage.setItem('viewerSeen:' + id, '1');}
                function pct(value, total){return (value / Math.max(1, total) * 100) + '%';}
                function cssColor(value){
                  const raw = String(value || '');
                  if(/^#[0-9a-fA-F]{8}${'$'}/.test(raw)){
                    const a = parseInt(raw.slice(1,3), 16) / 255;
                    const r = parseInt(raw.slice(3,5), 16);
                    const g = parseInt(raw.slice(5,7), 16);
                    const b = parseInt(raw.slice(7,9), 16);
                    return 'rgba(' + r + ',' + g + ',' + b + ',' + a.toFixed(3) + ')';
                  }
                  return raw;
                }
                function esc(v){return String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
                loadList();
                setInterval(() => loadList({poll:true}), 15000);
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
}
