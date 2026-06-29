package com.mathworkbook.app.core.gpt

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume

class WorkbookGptGateway(private val appContext: Context) {
    private var webView: WebView? = null
    @Volatile private var pageLoaded = false
    @Volatile private var autoUploadArmed = false
    @Volatile private var autoUploadDelivered = false
    @Volatile private var armedUris: List<Uri> = emptyList()

    private val _status = MutableStateFlow("ChatGPT 준비 중")
    val status: StateFlow<String> = _status.asStateFlow()

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(): WebView {
        webView?.let { return it }
        WebView.setWebContentsDebuggingEnabled(true)
        val wv = WebView(appContext)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = true
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            wv.settings.offscreenPreRaster = true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                pageLoaded = true
                _status.value = "ChatGPT 화면 준비됨"
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                Log.w(TAG, "renderer gone didCrash=${detail?.didCrash()}")
                if (view === webView) {
                    webView = null
                    pageLoaded = false
                    (view?.parent as? android.view.ViewGroup)?.removeView(view)
                    runCatching { view?.destroy() }
                    _status.value = "ChatGPT 화면을 다시 준비합니다"
                }
                return true
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (autoUploadArmed && armedUris.isNotEmpty()) {
                    filePathCallback?.onReceiveValue(armedUris.toTypedArray())
                    autoUploadDelivered = true
                    autoUploadArmed = false
                    _status.value = "문제 이미지 첨부됨"
                    return true
                }
                filePathCallback?.onReceiveValue(null)
                return true
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean = false
        }
        pageLoaded = false
        wv.loadUrl(CHATGPT_URL)
        webView = wv
        return wv
    }

    fun provideView(): WebView = ensureWebView()

    suspend fun sendProblem(prompt: String, imagePath: String?): Boolean = withContext(Dispatchers.Main.immediate) {
        ensureWebView()
        waitPageLoaded()
        if (!waitForEditor()) {
            _status.value = "ChatGPT 입력창을 찾지 못했습니다. 로그인 상태를 확인하세요."
            return@withContext false
        }
        _status.value = "Pro 모드 선택 시도 중"
        selectComposerMode("Pro", aliases = listOf("Pro 확장"))

        val imageUri = imagePath
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.exists() && it.isFile }
            ?.let { file ->
                runCatching {
                    FileProvider.getUriForFile(
                        appContext,
                        "${appContext.packageName}.fileprovider",
                        file
                    )
                }.getOrElse {
                    Log.w(TAG, "FileProvider uri failed, fallback to file uri", it)
                    Uri.fromFile(file)
                }
            }

        if (imageUri != null) {
            armedUris = listOf(imageUri)
            _status.value = "문제 이미지 첨부 중"
            val attached = attachImages()
            if (!attached) {
                _status.value = "이미지 첨부 실패, 텍스트만 전송합니다"
            }
        }

        _status.value = "질문 전송 중"
        if (!inject(prompt)) {
            _status.value = "프롬프트 입력 실패"
            return@withContext false
        }
        delay(800)
        val sent = sendWithRetry()
        _status.value = if (sent) "질문 전송됨" else "질문 전송 실패"
        sent
    }

    suspend fun captureLatestAssistantText(): String? = withContext(Dispatchers.Main.immediate) {
        ensureWebView()
        val text = evalObj(buildLatestAssistantTextJs())
            ?.optString("text")
            ?.trim()
            .orEmpty()
        text.takeIf { it.isNotBlank() }
    }

    suspend fun captureLatestAssistantHtml(): String? = withContext(Dispatchers.Main.immediate) {
        ensureWebView()
        val html = evalObj(buildLatestAssistantHtmlJs())
            ?.optString("html")
            ?.trim()
            .orEmpty()
        html.takeIf { it.isNotBlank() }
    }

    private suspend fun attachImages(): Boolean {
        if (armedUris.isEmpty()) return false
        repeat(3) { attempt ->
            autoUploadArmed = true
            autoUploadDelivered = false
            val target = evalObj(buildAttachTargetJs(attempt))
            if (target?.optBoolean("found") == true) {
                tapTarget(target)
            } else {
                eval(buildAttachClickJs("photo-menu"))
            }
            delay(700)
            if (!autoUploadDelivered) {
                val menu = evalObj(buildUploadMenuTargetJs(attempt))
                if (menu?.optBoolean("found") == true) {
                    tapTarget(menu)
                } else {
                    eval(buildAttachClickJs("file-input-menu-fallback"))
                }
                delay(700)
            }
            val deadline = now() + 4_000L
            while (now() < deadline && !autoUploadDelivered) delay(120)
            if (autoUploadDelivered) {
                autoUploadArmed = false
                delay(1_800)
                return true
            }
        }
        autoUploadArmed = false
        return false
    }

    private suspend fun selectComposerMode(label: String, aliases: List<String> = emptyList()): Boolean {
        val labels = (listOf(label) + aliases).distinct()
        repeat(4) {
            val target = evalObj(buildComposerModeTargetJs(labels))
            when (target?.optString("status")) {
                "selected" -> return true
                "option", "toggle" -> {
                    tapTarget(target)
                    delay(900)
                    val verified = evalObj(buildComposerModeTargetJs(labels))
                    if (verified?.optString("status") == "selected") return true
                }
                else -> delay(400)
            }
        }
        return false
    }

    private suspend fun waitPageLoaded(): Boolean {
        val deadline = now() + 18_000L
        while (!pageLoaded && now() < deadline) delay(250)
        return pageLoaded
    }

    private suspend fun waitForEditor(): Boolean {
        val deadline = now() + 18_000L
        while (now() < deadline) {
            val probe = evalObj(buildEditorProbeJs())
            if (probe?.optBoolean("editorExists") == true) return true
            delay(500)
        }
        return false
    }

    private suspend fun inject(prompt: String): Boolean {
        repeat(6) {
            val result = eval(buildPromptInjectionJs(prompt))
            if (result.contains("prompt-injected")) return true
            delay(350)
            val probe = evalObj(buildPromptPresenceProbeJs(prompt))
            val textLen = probe?.optInt("textLen", 0) ?: 0
            if (probe?.optBoolean("hasPrompt") == true ||
                (probe?.optBoolean("sendEnabled") == true && textLen >= (prompt.length * 0.85f).toInt())
            ) {
                return true
            }
        }
        return false
    }

    private suspend fun sendWithRetry(): Boolean {
        repeat(5) { attempt ->
            val target = evalObj(buildSendTargetJs(attempt))
            if (target?.optBoolean("found") == true) {
                tapTarget(target)
                delay(700)
                return true
            }
            val result = eval(buildSendJs())
            if (result.contains("clicked-send-button")) return true
            delay(700)
        }
        return false
    }

    private suspend fun eval(js: String): String {
        val wv = ensureWebView()
        return withTimeoutOrNull(EVAL_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                wv.evaluateJavascript(js) { result -> if (cont.isActive) cont.resume(result ?: "null") }
            }
        } ?: "eval-timeout"
    }

    private suspend fun evalObj(js: String): JSONObject? = runCatching {
        val raw = eval(js)
        if (raw.isBlank() || raw == "null") null else JSONObject(unwrap(raw))
    }.getOrNull()

    private fun unwrap(value: String): String {
        if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
            return org.json.JSONArray("[$value]").getString(0)
        }
        return value
    }

    private suspend fun tapTarget(obj: JSONObject) {
        val wv = webView ?: return
        if (wv.width <= 0 || wv.height <= 0) return
        val vw = obj.optDouble("vw", 1.0).coerceAtLeast(1.0)
        val vh = obj.optDouble("vh", 1.0).coerceAtLeast(1.0)
        val cssX = obj.optDouble("tapX", obj.optDouble("x", 0.0)).coerceIn(1.0, (vw - 1.0).coerceAtLeast(1.0))
        val cssY = obj.optDouble("tapY", obj.optDouble("y", 0.0)).coerceIn(1.0, (vh - 1.0).coerceAtLeast(1.0))
        val x = (cssX / vw * wv.width).toFloat()
        val y = (cssY / vh * wv.height).toFloat()
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)
        runCatching { wv.dispatchTouchEvent(down) }
        down.recycle()
        delay(50)
        val up = MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, x, y, 0)
        runCatching { wv.dispatchTouchEvent(up) }
        up.recycle()
    }

    private fun now(): Long = System.currentTimeMillis()

    private fun buildEditorProbeJs(): String =
        """
        (function(){
          function rectOf(el){if(!el||!el.getBoundingClientRect)return {width:0,height:0,top:0,left:0,bottom:0,right:0}; const r=el.getBoundingClientRect(); return {width:r.width,height:r.height,top:r.top,left:r.left,bottom:r.bottom,right:r.right};}
          function visible(el){if(!el){return false;}const r=rectOf(el);const s=getComputedStyle(el);return r.width>0&&r.height>0&&r.bottom>0&&r.right>0&&r.top<window.innerHeight&&r.left<window.innerWidth&&s.visibility!=="hidden"&&s.display!=="none";}
          const selectors=["#prompt-textarea","[data-testid='composer-text-input']","[data-testid='prompt-textarea']","textarea",".ProseMirror","[contenteditable]","[role='textbox']"];
          for(const q of selectors){
            const all=Array.from(document.querySelectorAll(q));
            const vis=all.filter(visible);
            const el=(vis.length?vis:all).slice(-1)[0];
            if(el){
              const text=("value" in el)?el.value:(el.innerText||el.textContent||"");
              return JSON.stringify({editorExists:true,textLen:String(text||"").length,href:location.href,title:document.title});
            }
          }
          return JSON.stringify({editorExists:false,href:location.href,title:document.title});
        })();
        """.trimIndent()

    private fun buildLatestAssistantTextJs(): String =
        """
        (function(){
          function visible(el){
            if(!el||!el.getBoundingClientRect)return false;
            const r=el.getBoundingClientRect();
            const s=getComputedStyle(el);
            return r.width>0&&r.height>0&&s.visibility!=="hidden"&&s.display!=="none";
          }
          function clean(txt){
            return String(txt||"").replace(/\n{3,}/g,"\n\n").trim();
          }
          let nodes=[...document.querySelectorAll('[data-message-author-role="assistant"]')].filter(visible);
          if(!nodes.length){
            nodes=[...document.querySelectorAll('main .markdown, main [class*="markdown"]')].filter(visible);
          }
          if(!nodes.length){
            nodes=[...document.querySelectorAll('article')].filter(visible).filter(function(el){
              const t=clean(el.innerText||el.textContent||"");
              return t.length>20&&!/^You\s/i.test(t)&&!/^(나|사용자)\s*[:：]/.test(t);
            });
          }
          const node=nodes[nodes.length-1]||null;
          const text=node?clean(node.innerText||node.textContent||""):"";
          return JSON.stringify({text:text,length:text.length,count:nodes.length});
        })();
        """.trimIndent()

    private fun buildLatestAssistantHtmlJs(): String =
        """
        (function(){
          function visible(el){
            if(!el||!el.getBoundingClientRect)return false;
            const r=el.getBoundingClientRect();
            const s=getComputedStyle(el);
            return r.width>0&&r.height>0&&s.visibility!=="hidden"&&s.display!=="none";
          }
          function clean(txt){
            return String(txt||"").replace(/\n{3,}/g,"\n\n").trim();
          }
          function answerBody(el){
            if(!el)return null;
            const body=el.querySelector('.markdown,.prose,[data-message-id] .markdown,[class*="markdown"],[class*="prose"]');
            return body||el;
          }
          let nodes=[...document.querySelectorAll('[data-message-author-role="assistant"]')].filter(visible);
          if(!nodes.length){
            nodes=[...document.querySelectorAll('main .markdown, main [class*="markdown"]')].filter(visible);
          }
          if(!nodes.length){
            nodes=[...document.querySelectorAll('article')].filter(visible).filter(function(el){
              const t=clean(el.innerText||el.textContent||"");
              return t.length>20&&!/^You\s/i.test(t)&&!/^(사용자|User)\s*[:：]/i.test(t);
            });
          }
          const node=nodes[nodes.length-1]||null;
          const body=answerBody(node);
          const html=body?String(body.innerHTML||"").trim():"";
          const text=body?clean(body.innerText||body.textContent||""):"";
          return JSON.stringify({html:html,text:text,length:html.length,count:nodes.length});
        })();
        """.trimIndent()

    private fun buildComposerModeTargetJs(labels: List<String>): String {
        val targets = labels.distinct().joinToString(prefix = "[", postfix = "]") { JSONObject.quote(it) }
        return "(function(){" +
            "const targets=$targets;" +
            "function rectOf(el){if(!el||!el.getBoundingClientRect)return {width:0,height:0,top:0,left:0,bottom:0,right:0};const r=el.getBoundingClientRect();return {width:r.width,height:r.height,top:r.top,left:r.left,bottom:r.bottom,right:r.right};}" +
            "function visible(el){const r=rectOf(el);const s=getComputedStyle(el);return r.width>0&&r.height>0&&r.bottom>0&&r.right>0&&r.top<window.innerHeight&&r.left<window.innerWidth&&s.visibility!=='hidden'&&s.display!=='none';}" +
            "function labelOf(el){return String((el.innerText||el.ariaLabel||el.title||el.getAttribute&&el.getAttribute('aria-label')||el.textContent||'')).replace(/\\s+/g,' ').trim();}" +
            "function norm(txt){return String(txt||'').replace(/\\s+/g,' ').trim();}" +
            "function hit(txt){const n=norm(txt);return targets.some(function(t){const target=norm(t);if(!target)return false;if(/^pro$/i.test(target))return /^pro$/i.test(n);return n.indexOf(target)>=0;});}" +
            "function out(status,el,extra){const r=rectOf(el);return JSON.stringify(Object.assign({status:status,x:(r.left+r.right)/2,y:(r.top+r.bottom)/2,vw:window.innerWidth,vh:window.innerHeight,label:labelOf(el)},extra||{}));}" +
            "const editor=document.querySelector('#prompt-textarea,[data-testid=\"composer-text-input\"],[data-testid=\"prompt-textarea\"],textarea,.ProseMirror,[contenteditable],[role=\"textbox\"]');" +
            "const er=rectOf(editor);" +
            "const modeWords=/즉시|중간|높음|매우\\s*높음|Pro\\s*확장|\\bPro\\b|GPT-?5\\.5|instant|medium|high/i;" +
            "const controls=[...document.querySelectorAll('button,[role=\"button\"],[role=\"menuitem\"],[role=\"option\"],[data-radix-collection-item],label')].filter(visible);" +
            "const rows=controls.map(el=>({el:el,r:rectOf(el),txt:labelOf(el)})).filter(x=>x.txt);" +
            "const current=rows.filter(x=>modeWords.test(x.txt)&&x.r.top>window.innerHeight*0.70).sort((a,b)=>b.r.bottom-a.r.bottom||b.r.right-a.r.right)[0];" +
            "if(current&&hit(current.txt))return out('selected',current.el,{current:true});" +
            "const option=rows.filter(x=>hit(x.txt)&&(!current||x.r.bottom<current.r.top-8)&&x.r.top>0&&x.r.bottom<window.innerHeight*0.96).sort((a,b)=>a.r.top-b.r.top||a.r.left-b.r.left)[0];" +
            "if(option)return out('option',option.el);" +
            "const toggle=current||rows.filter(x=>modeWords.test(x.txt)&&x.r.top>er.bottom-180).sort((a,b)=>b.r.bottom-a.r.bottom||b.r.right-a.r.right)[0];" +
            "if(toggle)return out('toggle',toggle.el);" +
            "return JSON.stringify({status:'not-found'});" +
            "})();"
    }

    private fun buildPromptInjectionJs(prompt: String): String {
        val escaped = jsTemplate(prompt)
        return "(function(){" +
            "const text=`$escaped`;" +
            "function visible(el){if(!el){return false;}const r=el.getBoundingClientRect();const s=getComputedStyle(el);return r.width>0&&r.height>0&&r.bottom>0&&r.right>0&&r.top<window.innerHeight&&r.left<window.innerWidth&&s.visibility!=='hidden'&&s.display!=='none';}" +
            "function pickEditor(){const selectors=['#prompt-textarea','[data-testid=\"composer-text-input\"]','[data-testid=\"prompt-textarea\"]','textarea','.ProseMirror','[contenteditable]','[role=\"textbox\"]'];for(const q of selectors){const all=[...document.querySelectorAll(q)];const list=all.filter(visible);if(list.length){return list[list.length-1];}if(all.length){return all[all.length-1];}}return null;}" +
            "function fire(el,type){try{el.dispatchEvent(new InputEvent(type,{bubbles:true,cancelable:true,inputType:'insertText',data:text}));}catch(e){el.dispatchEvent(new Event(type,{bubbles:true,cancelable:true}));}}" +
            "const el=pickEditor();if(!el){return JSON.stringify({status:'no-editor'});}el.focus();" +
            "if('value' in el){let proto=Object.getPrototypeOf(el);let desc=null;while(proto&&!desc){desc=Object.getOwnPropertyDescriptor(proto,'value');proto=Object.getPrototypeOf(proto);}if(desc&&desc.set){desc.set.call(el,text);}else{el.value=text;}fire(el,'beforeinput');fire(el,'input');el.dispatchEvent(new Event('change',{bubbles:true}));}" +
            "else{const sel=window.getSelection();const range=document.createRange();el.innerHTML='';range.selectNodeContents(el);range.collapse(true);sel.removeAllRanges();sel.addRange(range);fire(el,'beforeinput');let inserted=false;try{inserted=document.execCommand&&document.execCommand('insertText',false,text);}catch(e){}if(!inserted){el.textContent=text;}fire(el,'input');el.dispatchEvent(new KeyboardEvent('keyup',{key:' ',bubbles:true}));}" +
            "const now=('value' in el)?el.value:(el.innerText||el.textContent||'');" +
            "const ok=now.length>0&&(now.indexOf(text.slice(0,Math.min(30,text.length)))>=0||text.indexOf(now.slice(0,Math.min(30,now.length)))>=0);" +
            "return JSON.stringify({status:ok?'prompt-injected':'prompt-empty',textLen:text.length,editorTextLen:now.length});" +
            "})();"
    }

    private fun buildPromptPresenceProbeJs(prompt: String): String {
        val prefix = jsTemplate(prompt.take(30))
        val tail = jsTemplate(prompt.takeLast(30))
        return "(function(){" +
            "const prefix=`$prefix`;const tail=`$tail`;" +
            "function visible(el){if(!el)return false;const r=el.getBoundingClientRect();const s=getComputedStyle(el);return r.width>0&&r.height>0&&r.bottom>0&&r.right>0&&r.top<window.innerHeight&&r.left<window.innerWidth&&s.visibility!=='hidden'&&s.display!=='none';}" +
            "function pickEditor(){const selectors=['#prompt-textarea','[data-testid=\"composer-text-input\"]','[data-testid=\"prompt-textarea\"]','textarea','.ProseMirror','[contenteditable]','[role=\"textbox\"]'];for(const q of selectors){const all=[...document.querySelectorAll(q)];const list=all.filter(visible);if(list.length){return list[list.length-1];}if(all.length){return all[all.length-1];}}return null;}" +
            "function label(el){return String(el&&((el.innerText||el.ariaLabel||el.title||el.getAttribute&&el.getAttribute('aria-label')||el.textContent)||'')).replace(/\\s+/g,' ').trim();}" +
            "const el=pickEditor();" +
            "const text=el?(('value' in el)?String(el.value||''):String(el.innerText||el.textContent||'')):'';" +
            "const hasPrefix=prefix.length===0||text.indexOf(prefix)>=0;const hasTail=tail.length===0||text.indexOf(tail)>=0;" +
            "const buttons=[...document.querySelectorAll('button')].filter(visible);" +
            "const send=buttons.find(b=>!b.disabled&&(b.getAttribute('data-testid')==='send-button'||/send|보내기|전송/i.test(label(b))));" +
            "return JSON.stringify({textLen:text.length,hasPrompt:(text.length>0&&hasPrefix&&hasTail),sendEnabled:!!send});" +
            "})();"
    }

    private fun buildSendTargetJs(attempt: Int): String =
        "(function(){" +
            "const attempt=$attempt;" +
            "function rectOf(el){if(!el||!el.getBoundingClientRect)return {width:0,height:0,top:0,left:0,bottom:0,right:0};const r=el.getBoundingClientRect();return {width:r.width,height:r.height,top:r.top,left:r.left,bottom:r.bottom,right:r.right};}" +
            "function visible(el){if(!el)return false;const r=rectOf(el);const s=getComputedStyle(el);return r.width>0&&r.height>0&&r.bottom>0&&r.right>0&&r.top<window.innerHeight&&r.left<window.innerWidth&&s.visibility!=='hidden'&&s.display!=='none';}" +
            "function label(el){return String(el&&((el.innerText||el.ariaLabel||el.title||el.getAttribute&&el.getAttribute('aria-label')||el.textContent)||'')).replace(/\\s+/g,' ').trim();}" +
            "const editor=document.querySelector('#prompt-textarea,[data-testid=\"composer-text-input\"],[data-testid=\"prompt-textarea\"],textarea,.ProseMirror,[contenteditable],[role=\"textbox\"]');" +
            "const er=rectOf(editor);const buttons=[...document.querySelectorAll('button')].filter(visible).filter(b=>!b.disabled);" +
            "let rows=buttons.map(b=>({el:b,r:rectOf(b),label:label(b),testid:b.getAttribute('data-testid')||''}));" +
            "let hit=rows.find(x=>x.testid==='send-button'||/send|보내기|전송/i.test(x.label));" +
            "if(!hit&&editor){hit=rows.filter(x=>x.r.top>=er.top-120&&x.r.bottom<=er.bottom+140&&x.r.left>=er.left&&x.r.right>=er.right-180).sort((a,b)=>b.r.right-a.r.right||b.r.bottom-a.r.bottom)[0];}" +
            "if(!hit){hit=rows.filter(x=>x.r.bottom>window.innerHeight*0.55&&x.r.right>window.innerWidth*0.55).sort((a,b)=>b.r.right-a.r.right||b.r.bottom-a.r.bottom)[0];}" +
            "if(!hit){return JSON.stringify({found:false,attempt:attempt,vw:window.innerWidth,vh:window.innerHeight});}" +
            "const r=hit.r;const vt=Math.max(0,r.top),vb=Math.min(window.innerHeight,r.bottom),vl=Math.max(0,r.left),vr=Math.min(window.innerWidth,r.right);" +
            "return JSON.stringify({found:true,attempt:attempt,label:hit.label,testid:hit.testid,tapX:(vl+vr)/2,tapY:(vt+vb)/2,x:(r.left+r.right)/2,y:(r.top+r.bottom)/2,vw:window.innerWidth,vh:window.innerHeight});" +
            "})();"

    private fun buildSendJs(): String =
        "(function(){" +
            "function visible(el){const r=el.getBoundingClientRect();const s=getComputedStyle(el);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none';}" +
            "const buttons=[...document.querySelectorAll('button')].filter(visible);" +
            "let btn=buttons.find(b=>!b.disabled&&(b.getAttribute('data-testid')==='send-button'||/send|보내기|전송/i.test(b.innerText||b.ariaLabel||b.title||b.getAttribute('aria-label')||'')));" +
            "if(!btn){btn=buttons.filter(b=>!b.disabled).map(b=>({b,r:b.getBoundingClientRect()})).filter(x=>x.r.bottom>window.innerHeight*0.55&&x.r.right>window.innerWidth*0.55).sort((a,b)=>b.r.right-a.r.right)[0]?.b;}" +
            "if(btn){btn.click();return 'clicked-send-button';}" +
            "return 'no-enabled-send';" +
            "})();"

    private fun buildAttachClickJs(mode: String): String {
        val m = jsString(mode)
        return "(function(){" +
            "const mode='$m';" +
            "function label(el){return (el.innerText||el.ariaLabel||el.title||el.value||el.getAttribute('aria-label')||'').trim();}" +
            "function visible(el){const r=el.getBoundingClientRect();const s=getComputedStyle(el);return r.width>0&&r.height>0&&r.bottom>0&&r.right>0&&r.top<window.innerHeight&&r.left<window.innerWidth&&s.visibility!=='hidden'&&s.display!=='none';}" +
            "const words=/attach|upload|file|paperclip|photo|image|add|\\uCCA8\\uBD80|\\uD30C\\uC77C|\\uC5C5\\uB85C\\uB4DC|\\uC0AC\\uC9C4|\\uC774\\uBBF8\\uC9C0|\\uCD94\\uAC00/i;" +
            "const inputs=[...document.querySelectorAll('input[type=\"file\"]')];" +
            "if((mode.indexOf('direct-input')>=0||mode.indexOf('file-input')>=0)&&inputs.length){inputs[inputs.length-1].click();return 'clicked-file-input-js';}" +
            "const controls=[...document.querySelectorAll('button,[role=\"button\"],[role=\"menuitem\"],label')].filter(visible).filter(el=>!el.disabled);" +
            "let hit=controls.find(el=>words.test(label(el)));" +
            "if(hit){hit.click();return 'clicked-attach-control';}" +
            "if(inputs.length){inputs[inputs.length-1].click();return 'clicked-file-input-js-fallback';}" +
            "return 'no-attach-control';" +
            "})();"
    }

    private fun buildAttachTargetJs(attempt: Int): String =
        "(function(){" +
            "const attempt=$attempt;" +
            "function label(el){return (el.innerText||el.ariaLabel||el.title||el.value||el.getAttribute('aria-label')||'').trim();}" +
            "function visible(el){const r=el.getBoundingClientRect();const s=getComputedStyle(el);return r.width>0&&r.height>0&&r.bottom>0&&r.right>0&&r.top<window.innerHeight&&r.left<window.innerWidth&&s.visibility!=='hidden'&&s.display!=='none';}" +
            "function rectOf(el){const r=el.getBoundingClientRect();return {left:r.left,top:r.top,right:r.right,bottom:r.bottom,width:r.width,height:r.height};}" +
            "const words=/attach|upload|file|paperclip|photo|image|add|\\uCCA8\\uBD80|\\uD30C\\uC77C|\\uC5C5\\uB85C\\uB4DC|\\uC0AC\\uC9C4|\\uC774\\uBBF8\\uC9C0|\\uCD94\\uAC00/i;" +
            "const controls=[...document.querySelectorAll('button,[role=\"button\"],label')].filter(visible).filter(el=>!el.disabled);" +
            "const editor=document.querySelector('#prompt-textarea,[data-testid=\"composer-text-input\"],[data-testid=\"prompt-textarea\"],textarea,.ProseMirror,[contenteditable],[role=\"textbox\"]');" +
            "const er=editor?rectOf(editor):{left:0,top:window.innerHeight*0.7,right:window.innerWidth,bottom:window.innerHeight};" +
            "const rows=controls.map(el=>({el:el,r:rectOf(el),label:label(el)})).filter(x=>x.r.top>=0).sort((a,b)=>a.r.top-b.r.top||a.r.left-b.r.left);" +
            "const near=rows.filter(x=>x.r.top<er.bottom+110&&x.r.bottom>er.top-140&&x.r.left>=0&&x.r.left<er.left+260);" +
            "const wordRows=rows.filter(x=>words.test(x.label));" +
            "let hit=near.find(x=>words.test(x.label))||wordRows[0]||near[0];" +
            "if(!hit){return JSON.stringify({found:false,vw:window.innerWidth,vh:window.innerHeight});}" +
            "const r=hit.r;const vt=Math.max(0,r.top),vb=Math.min(window.innerHeight,r.bottom),vl=Math.max(0,r.left),vr=Math.min(window.innerWidth,r.right);" +
            "return JSON.stringify({found:true,label:hit.label,tapX:(vl+vr)/2,tapY:(vt+vb)/2,x:(r.left+r.right)/2,y:(r.top+r.bottom)/2,vw:window.innerWidth,vh:window.innerHeight});" +
            "})();"

    private fun buildUploadMenuTargetJs(attempt: Int): String =
        "(function(){" +
            "const attempt=$attempt;" +
            "function label(el){return (el.innerText||el.ariaLabel||el.title||el.value||el.getAttribute('aria-label')||'').trim();}" +
            "function visible(el){const r=el.getBoundingClientRect();const s=getComputedStyle(el);return r.width>0&&r.height>0&&r.bottom>0&&r.right>0&&r.top<window.innerHeight&&r.left<window.innerWidth&&s.visibility!=='hidden'&&s.display!=='none';}" +
            "function host(el){return el.closest('button,[role=\"button\"],[role=\"menuitem\"],label,[tabindex]')||el;}" +
            "const words=/upload|file|photo|image|browse|computer|device|\\uCCA8\\uBD80|\\uD30C\\uC77C|\\uC5C5\\uB85C\\uB4DC|\\uC0AC\\uC9C4|\\uC774\\uBBF8\\uC9C0|\\uAE30\\uAE30/i;" +
            "const raw=[...document.querySelectorAll('button,[role=\"button\"],[role=\"menuitem\"],label,[tabindex],div,li,span')];" +
            "const seen=new Set();const rows=[];" +
            "for(const rawEl of raw){const el=host(rawEl);if(seen.has(el)||!visible(el)||el.disabled)continue;seen.add(el);const l=label(rawEl)||label(el);const r=el.getBoundingClientRect();if(r.top>=0&&r.width>=16&&r.height>=16&&r.width<=420&&r.height<=120&&l.length>0&&l.length<=90&&words.test(l)){rows.push({el,r,label:l});}}" +
            "rows.sort((a,b)=>a.r.top-b.r.top||a.r.left-b.r.left);" +
            "const hit=rows[0];if(!hit){return JSON.stringify({found:false,vw:window.innerWidth,vh:window.innerHeight});}" +
            "const r=hit.r;const vt=Math.max(0,r.top),vb=Math.min(window.innerHeight,r.bottom),vl=Math.max(0,r.left),vr=Math.min(window.innerWidth,r.right);" +
            "return JSON.stringify({found:true,label:hit.label,tapX:(vl+vr)/2,tapY:(vt+vb)/2,x:(r.left+r.right)/2,y:(r.top+r.bottom)/2,vw:window.innerWidth,vh:window.innerHeight});" +
            "})();"

    private fun jsTemplate(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("$", "\\$")

    private fun jsString(value: String): String =
        value.replace("\\", "\\\\").replace("'", "\\'")

    private companion object {
        const val TAG = "WorkbookGptGateway"
        const val CHATGPT_URL = "https://chatgpt.com/"
        const val EVAL_TIMEOUT_MS = 6_000L
    }
}
