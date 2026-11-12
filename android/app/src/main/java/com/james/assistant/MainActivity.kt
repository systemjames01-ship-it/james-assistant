package com.james.assistant

import android.Manifest
import android.hardware.camera2.CameraAccessException
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : android.app.Activity() {
    companion object {
        private const val WORKER_URL = "https://james-ai.systemjames01.workers.dev"
        private const val APP_ORIGIN = "https://systemjames01-ship-it.github.io"
        private const val REQ_CAMERA = 4104
        private const val REQ_FILE = 4105
        private const val REQ_NOTIFICATIONS = 4102
        private const val REQ_MICROPHONE = 4106
        private const val REQ_CAMERA_PERMISSION = 4107
        private const val REQ_EXPORT_TEXT = 4108
        private const val COLOR_BG = 0xFF050A12.toInt()
        private const val COLOR_PANEL = 0xFF0B1A29.toInt()
        private const val COLOR_CYAN = 0xFF68E4F2.toInt()
        private const val COLOR_TEXT = 0xFFEAF5FB.toInt()
        private const val COLOR_MUTED = 0xFF8299AD.toInt()
        private const val COLOR_GREEN = 0xFF71E2B1.toInt()
        private const val COLOR_RED = 0xFFFF7D86.toInt()
    }

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayoutCompat
    private lateinit var statusText: TextView
    private lateinit var navButtons: MutableMap<String, Button>
    private lateinit var sessionId: String
    private lateinit var profileId: String
    private lateinit var chatMessages: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var prompt: EditText
    private lateinit var attachmentStatus: TextView
    private val pendingAttachments = mutableListOf<JSONObject>()
    private var pendingExportText: String? = null
    private var pendingExportPdf = false
    private var currentPage = "overview"
    private var sending = false
    private var backendSummary = "Verificando o núcleo..."
    private var selectedVoiceProfile = 0
    private var liveConversationEnabled = false
    private var liveVoiceSession: JamesLiveAudioSession? = null
    private var liveButton: Button? = null
    private var voiceProfileLabel: TextView? = null
    private var visionButton: Button? = null
    private var orbView: JamesHologramView? = null
    private var liveVisualPanel: LinearLayout? = null
    private var liveVisualImage: ImageView? = null
    private var liveVisualCredit: TextView? = null
    private var liveVisualChatCard: LinearLayout? = null
    private var liveVisualChatImage: ImageView? = null
    private var liveVisualChatCredit: TextView? = null
    private var profileInstructionsInput: EditText? = null
    private val liveVisualHandler = Handler(Looper.getMainLooper())
    private var liveVisuals = emptyList<JSONObject>()
    private var lastLiveVisuals = emptyList<JSONObject>()
    private var liveVisualIndex = 0
    private var liveVisualRequestId = 0
    private val liveUserTranscriptBuffer = StringBuilder()
    private val liveAssistantTranscriptBuffer = StringBuilder()
    private var liveUserTranscriptDisplayed = false
    private var liveVisualRunnable: Runnable? = null
    private var liveCamera: JamesLiveCamera? = null
    private var liveVisionPending = false
    private var cameraPermissionRequested = false
    private var pendingVoicePreview = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = COLOR_BG
        window.navigationBarColor = COLOR_BG
        window.decorView.systemUiVisibility = 0
        sessionId = getSharedPreferences("james_core", MODE_PRIVATE)
            .getString("session_id", null) ?: UUID.randomUUID().toString().also {
                getSharedPreferences("james_core", MODE_PRIVATE).edit().putString("session_id", it).apply()
            }
        profileId = getSharedPreferences("james_core", MODE_PRIVATE)
            .getString("profile_id", null) ?: sessionId.also {
                getSharedPreferences("james_core", MODE_PRIVATE).edit().putString("profile_id", it).apply()
            }
        registerCurrentConversation()
        createNotificationChannel()
        selectedVoiceProfile = getSharedPreferences("james_core", MODE_PRIVATE).getInt("voice_profile", 0).coerceIn(0, 2)
        buildShell()
        showPage("overview")
        refreshBackendStatus()
        // Sensores permanecem desligados até o usuário ativar o botão principal.
    }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
            setBackgroundColor(COLOR_BG)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = TextView(this).apply {
            text = "JÂMES"
            textSize = 22f
            setTextColor(COLOR_CYAN)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            letterSpacing = 0.18f
        }
        header.addView(brand, LinearLayout.LayoutParams(0, dp(42), 1f))
        statusText = TextView(this).apply {
            text = "VERIFICANDO"
            textSize = 9f
            setTextColor(COLOR_MUTED)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }
        header.addView(statusText)
        root.addView(header)

        val subtitle = TextView(this).apply {
            text = "CENTRAL DE INTELIGÊNCIA PESSOAL  //  CRIADO POR JAMERSON RAMOS"
            textSize = 8f
            setTextColor(COLOR_MUTED)
            letterSpacing = 0.06f
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(subtitle)

        val navScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        navButtons = mutableMapOf()
        val pages = listOf(
            "overview" to "JÂMES",
            "intelligence" to "CHAT",
            "control" to "CONTROLE"
        )
        pages.forEach { (key, label) ->
            val button = makeButton(label, secondary = true).apply {
                textSize = 9f
                setOnClickListener { showPage(key) }
            }
            navButtons[key] = button
            nav.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                rightMargin = dp(6)
            })
        }
        navScroll.addView(nav)
        root.addView(navScroll)

        val divider = View(this).apply { setBackgroundColor(0xFF183044.toInt()) }
        root.addView(divider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(8)
            bottomMargin = dp(12)
        })

        content = FrameLayoutCompat(this)
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun showPage(page: String) {
        currentPage = page
        navButtons.forEach { (key, button) ->
            val selected = key == page || (key == "control" && page in setOf("voices", "perception", "automation", "memory", "device"))
            button.setBackground(background(if (selected) 0xFF16445A.toInt() else COLOR_PANEL, if (selected) COLOR_CYAN else 0xFF244257.toInt()))
            button.setTextColor(if (selected) COLOR_TEXT else COLOR_MUTED)
        }
        content.removeAllViews()
        when (page) {
            "overview" -> renderOverview()
            "control" -> renderControlCenter()
            "voices" -> renderVoiceProfiles()
            "intelligence" -> renderIntelligence()
            "perception" -> renderPerception()
            "automation" -> renderTasks()
            "memory" -> renderMemories()
            "device" -> renderDevice()
            else -> renderOverview()
        }
    }

    private fun renderOverview() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(12))
        }
        val heading = TextView(this).apply {
            text = "JÂMES"
            textSize = 13f
            letterSpacing = 0.34f
            gravity = Gravity.CENTER
            setTextColor(COLOR_CYAN)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        page.addView(heading, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)))
        val orb = JamesHologramView(this)
        orbView = orb
        val orbStage = FrameLayout(this)
        orbStage.addView(orb, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val visualPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = background(0xE6071728.toInt(), COLOR_CYAN)
        }
        val visualImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Imagem ilustrativa do Jâmes"
        }
        val visualCredit = TextView(this).apply {
            textSize = 8f
            maxLines = 2
            setTextColor(COLOR_MUTED)
            gravity = Gravity.CENTER
        }
        visualPanel.addView(visualImage, LinearLayout.LayoutParams(dp(148), dp(94)))
        visualPanel.addView(visualCredit, LinearLayout.LayoutParams(dp(148), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3) })
        val visualParams = FrameLayout.LayoutParams(dp(158), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.BOTTOM).apply {
            rightMargin = dp(8); bottomMargin = dp(8)
        }
        orbStage.addView(visualPanel, visualParams)
        liveVisualPanel = visualPanel
        liveVisualImage = visualImage
        liveVisualCredit = visualCredit
        page.addView(orbStage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val visualsToRestore = if (liveVisuals.isNotEmpty()) liveVisuals else lastLiveVisuals
        if (visualsToRestore.isNotEmpty()) {
            val activeVisuals = JSONArray()
            visualsToRestore.forEach { activeVisuals.put(it) }
            showLiveVisuals(activeVisuals)
        }
        val subtitle = TextView(this).apply {
            text = "NÚCLEO HOLOGRÁFICO  //  INTERFACE JÂMES"
            textSize = 10f
            letterSpacing = 0.18f
            gravity = Gravity.CENTER
            setTextColor(COLOR_MUTED)
        }
        page.addView(subtitle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)))
        val state = TextView(this).apply {
            text = backendSummary + "\n" + if (liveConversationEnabled) "SESSÃO AO VIVO ATIVA" else "SENSORES DESLIGADOS ATÉ ATIVAÇÃO MANUAL"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(COLOR_GREEN)
            maxLines = 2
        }
        page.addView(state, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))
        liveButton = makeButton(if (liveConversationEnabled) "DESLIGAR JÂMES" else "LIGAR JÂMES", true) { toggleJamesPower() }
        page.addView(liveButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
            topMargin = dp(8); bottomMargin = dp(6)
        })
        voiceProfileLabel = TextView(this).apply {
            text = "PERFIL VOCAL  ·  " + voiceProfileNames[selectedVoiceProfile]
            textSize = 9f
            gravity = Gravity.CENTER
            setTextColor(COLOR_MUTED)
        }
        page.addView(voiceProfileLabel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)))
        visionButton = makeButton("ABRIR OS OLHOS · VISÃO AO VIVO", true) { toggleLiveVision() }
        page.addView(visionButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { bottomMargin = dp(6) })
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(makeButton("CONVERSAR POR TEXTO", true) { showPage("intelligence") },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        page.addView(controls)
        val modules = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("PERCEPÇÃO" to "perception", "AÇÕES" to "automation", "MEMÓRIA" to "memory", "APARELHO" to "device").forEach { (label, target) ->
            val b = makeButton(label, true).apply { textSize = 8f; setOnClickListener { showPage(target) } }
            modules.addView(b, LinearLayout.LayoutParams(0, dp(38), 1f).apply { leftMargin = dp(2); rightMargin = dp(2) })
        }
        page.addView(modules, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(8) })
        content.addView(page)
    }

    private val voiceProfileNames = arrayOf("JARVIS", "PATRICK JANE", "DAMON SALVATORE")
    private val voiceProfileModels = arrayOf("Charon", "Algieba", "Algenib")

    private fun renderVoiceProfiles() {
        val page = verticalPage()
        val intro = card()
        addText(intro, "IDENTIDADE VOCAL DO JÂMES", 13f, COLOR_CYAN, true)
        addText(intro, "Três perfis de voz masculina em português brasileiro, com timbre, ritmo e presença próprios.", 11f, COLOR_MUTED, top = 7)
        page.addView(intro)
        voiceProfileNames.forEachIndexed { index, name ->
            val panel = card()
            addText(panel, name, 13f, if (selectedVoiceProfile == index) COLOR_GREEN else COLOR_CYAN, true)
            val detail = when (index) {
                0 -> "JARVIS · formal, grave, sereno e preciso. Referência de dublagem: Eduardo Borgerth / Clécio Souto."
                1 -> "PATRICK JANE · suave, inteligente, persuasivo e conversacional. Referência de dublagem: Márcio Simões."
                else -> "DAMON SALVATORE · grave, aveludado, confiante e irônico. Referência de dublagem: Clécio Souto."
            }
            addText(panel, detail, 11f, COLOR_MUTED, top = 5)
            addText(panel, "Timbre do perfil: " + voiceProfileModels[index], 9f, COLOR_MUTED, top = 5)
            addButton(panel, if (selectedVoiceProfile == index) "PERFIL ATUAL" else "USAR ESTA VOZ", secondary = selectedVoiceProfile == index) {
                selectVoiceProfile(index)
            }
            addButton(panel, "OUVIR AMOSTRA", secondary = true) {
                previewVoiceProfile(index)
            }
            page.addView(panel)
        }
        val note = card()
        addText(note, "SOBRE AS REFERÊNCIAS", 10f, COLOR_CYAN, true)
        addText(note, "São interpretações vocais originais inspiradas nos personagens e na dublagem brasileira, não clonagens nem gravações dos dubladores.", 10f, COLOR_MUTED, top = 6)
        page.addView(note)
        mountScrollable(page)
    }

    private fun selectVoiceProfile(index: Int) {
        if (index !in 0..2) return
        val changed = selectedVoiceProfile != index
        applyVoiceProfile(index)
        getSharedPreferences("james_core", MODE_PRIVATE).edit().putInt("voice_profile", index).apply()
        setStatus("PERFIL " + voiceProfileNames[index] + " SELECIONADO")
        if (changed && liveConversationEnabled) restartLiveConversation()
        showPage("voices")
    }

    private fun previewVoiceProfile(index: Int) {
        selectVoiceProfile(index)
        pendingVoicePreview = true
        if (liveConversationEnabled) {
            if (liveVoiceSession?.sendText("Faça uma apresentação curta em português brasileiro para demonstrar sua voz. Diga apenas: Olá. Eu sou o Jâmes. Como posso ajudar?") == true) {
                pendingVoicePreview = false
            }
        } else {
            startLiveConversation()
        }
    }

    private fun renderControlCenter() {
        val page = verticalPage()
        val core = card()
        addText(core, "CENTRAL DE CONTROLE", 13f, COLOR_CYAN, true)
        addText(core, backendSummary, 10f, COLOR_GREEN, top = 6)
        addButton(core, if (liveConversationEnabled) "ENCERRAR LINK DE VOZ" else "INICIAR LINK DE VOZ") { toggleLiveConversation() }
        addButton(core, if (liveCamera != null || liveVisionPending) "DESLIGAR CÂMERA" else "ATIVAR OLHOS · CÂMERA AO VIVO", secondary = true) { toggleLiveVision() }
        addButton(core, "ABRIR PERFIS VOCAIS", secondary = true) { showPage("voices") }
        addButton(core, "ABRIR CONVERSA DE TEXTO", secondary = true) { showPage("intelligence") }
        page.addView(core)

        val profile = card()
        addText(profile, "PERFIL PESSOAL E SINCRONIZAÇÃO", 11f, COLOR_CYAN, true)
        addText(profile, "Código deste perfil:", 10f, COLOR_MUTED, top = 7)
        addText(profile, profileId, 11f, COLOR_TEXT, true, top = 4)
        addText(profile, "Use o mesmo código nos aparelhos que deseja vincular. Memórias autorizadas e tarefas serão compartilhadas; o histórico de cada conversa permanece separado.", 10f, COLOR_MUTED, top = 7)
        addButton(profile, "COPIAR CÓDIGO DE PERFIL") {
            try {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Jâmes profile", profileId))
                Toast.makeText(this, "Código de perfil copiado.", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this, "Não foi possível copiar o código.", Toast.LENGTH_LONG).show()
            }
        }
        addButton(profile, "VINCULAR OUTRO APARELHO", secondary = true) {
            val input = EditText(this).apply {
                hint = "Cole o código do outro aparelho"
                setSingleLine(true)
                setTextColor(COLOR_TEXT)
                setHintTextColor(COLOR_MUTED)
            }
            AlertDialog.Builder(this)
                .setTitle("VINCULAR PERFIL")
                .setMessage("Cole o código do aparelho que contém suas memórias e tarefas.")
                .setView(input)
                .setPositiveButton("VINCULAR") { _, _ ->
                    val candidate = input.text?.toString()?.trim().orEmpty()
                    if (Regex("[A-Za-z0-9_-]{8,100}").matches(candidate)) {
                        profileId = candidate
                        getSharedPreferences("james_core", MODE_PRIVATE).edit().putString("profile_id", profileId).apply()
                        showPage("control")
                        setStatus("PERFIL VINCULADO NESTE APARELHO")
                    } else {
                        Toast.makeText(this, "Código de perfil inválido.", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("CANCELAR", null)
                .show()
        }
        addText(profile, "Mantenha o código privado: quem o possuir poderá acessar as memórias e tarefas deste perfil.", 9f, COLOR_MUTED, top = 7)
        addText(profile, "INSTRUÇÕES PERSONALIZADAS", 10f, COLOR_CYAN, true, top = 12)
        addText(profile, "Defina como o Jâmes deve responder e quais prioridades deve respeitar em todas as conversas vinculadas a este perfil.", 10f, COLOR_MUTED, top = 5)
        val instructionsInput = EditText(this).apply {
            hint = "Ex.: responda em português brasileiro, explique decisões e peça confirmação antes de ações sensíveis."
            setHintTextColor(COLOR_MUTED)
            setTextColor(COLOR_TEXT)
            textSize = 12f
            minLines = 4
            maxLines = 7
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = background(0xFF020910.toInt(), 0xFF29485C.toInt())
        }
        profileInstructionsInput = instructionsInput
        profile.addView(instructionsInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(7) })
        addButton(profile, "SALVAR PERSONALIZAÇÃO") { saveProfileInstructions() }
        page.addView(profile)
        loadProfileInstructions()

        val sensors = card()
        addText(sensors, "SENSORES E PERCEPÇÃO", 11f, COLOR_CYAN, true)
        addInfoLine(sensors, "Audição", if (liveConversationEnabled) "Sessão solicitada" else "Em espera")
        addInfoLine(sensors, "Visão", if (liveCamera != null) "Câmera ao vivo ativa" else "Desativada")
        addInfoLine(sensors, "Microfone", if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) "Autorizado" else "Pendente")
        addInfoLine(sensors, "Câmera", if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) "Autorizada" else "Pendente")
        addButton(sensors, "CONFIGURAR PERMISSÕES", secondary = true) {
            try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + packageName))) } catch (_: Exception) { }
        }
        page.addView(sensors)

        val systems = card()
        addText(systems, "MÓDULOS DO SISTEMA", 11f, COLOR_CYAN, true)
        addModule(systems, "CHAT E RACIOCÍNIO", "Conversa textual, contexto e ferramentas") { showPage("intelligence") }
        addModule(systems, "PERFIS DE VOZ", "Identidade vocal e síntese de fala") { showPage("voices") }
        addModule(systems, "PERCEPÇÃO", "Câmera, imagens e documentos") { showPage("perception") }
        addModule(systems, "MEMÓRIA", "Memórias autorizadas e histórico") { showPage("memory") }
        addModule(systems, "AUTOMAÇÕES", "Tarefas e lembretes locais") { showPage("automation") }
        addModule(systems, "APARELHO", "Estado, permissões e acessibilidade") { showPage("device") }
        addButton(systems, "VERIFICAR CONEXÃO DO NÚCLEO", secondary = true) { refreshBackendStatus() }
        page.addView(systems)
        mountScrollable(page)
    }

    private fun renderIntelligence() {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = card()
        addText(head, "CONSOLE DO NÚCLEO", 11f, COLOR_CYAN, true)
        addText(head, "Conversas separadas, histórico persistente e ferramentas integradas.", 11f, COLOR_MUTED, top = 5)
        addButton(head, "NOVA CONVERSA") { createNewConversation() }
        addButton(head, "HISTÓRICO DE CONVERSAS", secondary = true) { showConversationPicker() }
        page.addView(head)

        chatScroll = ScrollView(this).apply {
            isFillViewport = true
            setBackground(background(0xFF020910.toInt(), 0xFF183044.toInt()))
        }
        chatMessages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        chatScroll.addView(chatMessages)
        val visualsToRestore = if (liveVisuals.isNotEmpty()) liveVisuals else lastLiveVisuals
        if (visualsToRestore.isNotEmpty()) {
            val activeVisuals = JSONArray()
            visualsToRestore.forEach { activeVisuals.put(it) }
            showLiveVisuals(activeVisuals)
        }
        page.addView(chatScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val composer = card()
        prompt = EditText(this).apply {
            hint = "Jâmes, analise, planeje ou execute uma tarefa autorizada..."
            setHintTextColor(COLOR_MUTED)
            setTextColor(COLOR_TEXT)
            textSize = 14f
            minLines = 2
            maxLines = 5
            gravity = Gravity.TOP
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = background(0xFF020910.toInt(), 0xFF29485C.toInt())
            imeOptions = EditorInfo.IME_ACTION_SEND
        }
        composer.addView(prompt, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(makeButton("ENVIAR") { sendMessage() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        composer.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        page.addView(composer)
        content.addView(page)
        loadHistory()
    }

    private fun renderPerception() {
        val page = verticalPage()
        val intro = card()
        addText(intro, "PERCEPÇÃO MULTIMODAL", 11f, COLOR_CYAN, true)
        addText(intro, "A câmera só é ativada quando você pede. Você também pode selecionar imagens e PDFs para o núcleo analisar.", 12f, COLOR_MUTED, top = 7)
        addButton(intro, "CAPTURAR COM A CÂMERA") { openCamera() }
        addButton(intro, "SELECIONAR IMAGEM OU PDF", secondary = true) { openFilePicker() }
        attachmentStatus = addText(intro, if (pendingAttachments.isEmpty()) "Nenhum anexo selecionado." else "ANEXOS: " + pendingAttachments.joinToString(", ") { it.optString("name") }, 10f, COLOR_MUTED, top = 10)
        addButton(intro, "ENVIAR PARA ANÁLISE") {
            if (pendingAttachments.isEmpty()) {
                setStatus("Selecione ou capture um arquivo primeiro.")
            } else {
                showPage("intelligence")
                prompt.setText("Analise os anexos e explique o que encontrou.")
                sendMessage()
            }
        }
        page.addView(intro)

        val note = card()
        addText(note, "LIMITES ATUAIS", 10f, COLOR_CYAN, true)
        addText(note, "Quando você liga o Jâmes, uma notificação persistente mantém a sessão autorizada de microfone e, quando ativada, câmera em primeiro plano mesmo ao alternar de aplicativo ou apagar a tela. O Android, o fabricante, a bateria e as permissões ainda podem interromper a sessão; isso não é garantia de monitoramento ininterrupto por 24 horas.", 11f, COLOR_MUTED, top = 7)
        page.addView(note)
        mountScrollable(page)
    }

    private fun renderTasks() {
        val page = verticalPage()
        val panel = card()
        addText(panel, "AUTOMAÇÕES E TAREFAS", 11f, COLOR_CYAN, true)
        addText(panel, "Tarefas registradas e lembretes locais do Android. Peça ao núcleo para criar ou concluir tarefas.", 11f, COLOR_MUTED, top = 6)
        addButton(panel, "ATUALIZAR TAREFAS", secondary = true) { loadTasksInto(panel) }
        page.addView(panel)
        mountScrollable(page)
        loadTasksInto(panel)
    }

    private fun renderMemories() {
        val page = verticalPage()
        val panel = card()
        addText(panel, "MEMÓRIA AUTORIZADA", 11f, COLOR_CYAN, true)
        addText(panel, "Memórias salvas explicitamente nesta sessão do aplicativo. Não há sincronização de conta entre dispositivos.", 11f, COLOR_MUTED, top = 6)
        addButton(panel, "ATUALIZAR MEMÓRIAS", secondary = true) { loadMemoriesInto(panel) }
        page.addView(panel)
        mountScrollable(page)
        loadMemoriesInto(panel)
    }

    private fun renderDevice() {
        val page = verticalPage()
        val panel = card()
        addText(panel, "DIAGNÓSTICO DO APARELHO", 11f, COLOR_CYAN, true)
        val info = deviceInfo()
        addInfoLine(panel, "Plataforma", "Android")
        addInfoLine(panel, "Fabricante", Build.MANUFACTURER)
        addInfoLine(panel, "Modelo", Build.MODEL)
        addInfoLine(panel, "Versão", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
        addInfoLine(panel, "Bateria", if (info.optInt("batteryPercent", -1) in 0..100) info.optInt("batteryPercent").toString() + "%" else "Indisponível")
        addInfoLine(panel, "Carregando", if (info.optBoolean("charging")) "Sim" else "Não")
        addInfoLine(panel, "Internet", if (info.optBoolean("networkConnected")) "Conectada" else "Sem validação")
        addButton(panel, "ATUALIZAR DIAGNÓSTICO", secondary = true) { showPage("device") }
        page.addView(panel)

        val service = card()
        addText(service, "CONEXÃO COM O NÚCLEO", 10f, COLOR_CYAN, true)
        addText(service, backendSummary, 11f, COLOR_MUTED, top = 7)
        addText(service, "AUTOMAÇÃO DA INTERFACE", 10f, COLOR_CYAN, true, top = 14)
        addText(service, "Permite tocar em elementos visíveis, preencher o campo já focado e navegar quando você solicitar. O serviço não transmite o conteúdo da tela. A ativação é manual e pode ser revogada nas Configurações do Android.", 10f, COLOR_MUTED, top = 6)
        addButton(service, "CONFIGURAR ACESSIBILIDADE", secondary = true) {
            try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            catch (_: Exception) { Toast.makeText(this, "Não foi possível abrir as configurações de acessibilidade.", Toast.LENGTH_LONG).show() }
        }
        addButton(service, "TESTAR CONEXÃO", secondary = true) { refreshBackendStatus() }
        page.addView(service)
        mountScrollable(page)
    }

    private fun conversationList(): JSONArray {
        val raw = getSharedPreferences("james_core", MODE_PRIVATE).getString("conversations_json", "[]")
        return try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
    }

    private fun saveConversationList(list: JSONArray) {
        getSharedPreferences("james_core", MODE_PRIVATE).edit().putString("conversations_json", list.toString()).apply()
    }

    private fun registerCurrentConversation() {
        val list = conversationList()
        val exists = (0 until list.length()).any { list.optJSONObject(it)?.optString("id") == sessionId }
        if (!exists) list.put(JSONObject().put("id", sessionId).put("title", "Conversa atual").put("updatedAt", System.currentTimeMillis()))
        saveConversationList(list)
    }

    private fun createNewConversation() {
        if (liveConversationEnabled) stopLiveConversation()
        lastLiveVisuals = emptyList()
        sessionId = UUID.randomUUID().toString()
        getSharedPreferences("james_core", MODE_PRIVATE).edit().putString("session_id", sessionId).apply()
        val list = conversationList()
        list.put(JSONObject().put("id", sessionId).put("title", "Nova conversa").put("updatedAt", System.currentTimeMillis()))
        while (list.length() > 40) list.remove(0)
        saveConversationList(list)
        showPage("intelligence")
        setStatus("NOVA CONVERSA CRIADA")
    }

    private fun showConversationPicker() {
        setStatus("SINCRONIZANDO HISTÓRICO DO PERFIL")
        executor.execute {
            try {
                val encoded = java.net.URLEncoder.encode(profileId, "UTF-8")
                val connection = URL("$WORKER_URL/?action=conversations&profileId=$encoded&limit=50").openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("Origin", APP_ORIGIN)
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val remote = JSONObject(responseText).optJSONArray("conversations") ?: JSONArray()
                val list = conversationList()
                for (index in 0 until remote.length()) {
                    val item = remote.optJSONObject(index) ?: continue
                    val id = item.optString("session_id")
                    if (!Regex("[A-Za-z0-9_-]{8,100}").matches(id)) continue
                    val existing = (0 until list.length()).mapNotNull { list.optJSONObject(it) }.firstOrNull { it.optString("id") == id }
                    if (existing == null) {
                        list.put(JSONObject().put("id", id).put("title", item.optString("title", "Conversa"))
                            .put("updatedAt", System.currentTimeMillis() - index))
                    } else {
                        existing.put("title", item.optString("title", existing.optString("title", "Conversa")))
                        existing.put("updatedAt", System.currentTimeMillis() - index)
                    }
                }
                saveConversationList(list)
            } catch (error: Exception) {
                android.util.Log.w("JamesConversations", "Remote conversation list unavailable", error)
            }
            runOnUiThread { displayConversationPicker() }
        }
    }

    private fun displayConversationPicker() {
        val list = conversationList()
        if (list.length() == 0) {
            Toast.makeText(this, "Nenhuma conversa salva neste perfil.", Toast.LENGTH_SHORT).show()
            return
        }
        val entries = (0 until list.length()).mapNotNull { list.optJSONObject(it) }
            .sortedByDescending { it.optLong("updatedAt") }
        val labels = entries.map { item ->
            val title = item.optString("title", "Conversa")
            val date = item.optLong("updatedAt")
            title + if (date > 0) " · " + java.text.SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(java.util.Date(date)) else ""
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("HISTÓRICO DE CONVERSAS")
            .setItems(labels) { _, index ->
                val selected = entries.getOrNull(index)?.optString("id").orEmpty()
                if (selected.isNotBlank() && selected != sessionId) {
                    if (liveConversationEnabled) stopLiveConversation()
                    stopLiveVisuals()
                    lastLiveVisuals = emptyList()
                    sessionId = selected
                    getSharedPreferences("james_core", MODE_PRIVATE).edit().putString("session_id", sessionId).apply()
                    showPage("intelligence")
                    setStatus("CONVERSA RECUPERADA")
                }
            }
            .setNegativeButton("FECHAR", null)
            .show()
    }

    private fun updateCurrentConversationTitle(firstMessage: String) {
        val list = conversationList()
        for (index in 0 until list.length()) {
            val item = list.optJSONObject(index) ?: continue
            if (item.optString("id") != sessionId) continue
            val currentTitle = item.optString("title")
            if (currentTitle.isBlank() || currentTitle == "Nova conversa" || currentTitle == "Conversa atual") {
                item.put("title", firstMessage.replace(Regex("\\s+"), " ").trim().take(46).ifBlank { "Conversa" })
            }
            item.put("updatedAt", System.currentTimeMillis())
            break
        }
        saveConversationList(list)
    }

    private fun loadHistory() {
        val url = WORKER_URL + "/?action=history&sessionId=" + sessionId + "&limit=30"
        executor.execute {
            try {
                val result = request(url)
                val messages = result.optJSONArray("messages") ?: JSONArray()
                runOnUiThread {
                    chatMessages.removeAllViews()
                    for (i in 0 until messages.length()) {
                        val item = messages.optJSONObject(i) ?: continue
                        addChatMessage(item.optString("content"), item.optString("role") != "user")
                    }
                    if (messages.length() == 0) addChatMessage("Olá. Sou o Jâmes. O núcleo está pronto para receber sua solicitação.", true)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (chatMessages.childCount == 0) addChatMessage("O histórico não está disponível. A conversa pode continuar normalmente.", true)
                }
            }
        }
    }

    private fun sendMessage() {
        if (sending) return
        val text = prompt.text?.toString()?.trim().orEmpty()
        if (text.isNotEmpty() && handleVoiceCommand(text)) { prompt.setText(""); return }
        if (liveConversationEnabled && currentPage != "intelligence" && text.isNotEmpty() && pendingAttachments.isEmpty()) {
            if (liveVoiceSession?.sendText(text) == true) {
                addChatMessage(text, false)
                mergeLiveTranscript(liveUserTranscriptBuffer, text)
                liveUserTranscriptDisplayed = true
            } else {
                setStatus("SESSÃO DE VOZ AINDA NÃO ESTÁ PRONTA")
            }
            prompt.setText("")
            return
        }
        if (text.isEmpty() && pendingAttachments.isEmpty()) return
        stopLiveVisuals()
        lastLiveVisuals = emptyList()
        val outgoingAttachments = JSONArray()
        pendingAttachments.forEach { outgoingAttachments.put(it) }
        pendingAttachments.clear()
        if (::attachmentStatus.isInitialized) attachmentStatus.text = "Nenhum anexo selecionado."
        addChatMessage(if (text.isEmpty()) "Analise os anexos enviados." else text, false)
        prompt.setText("")
        sending = true
        setStatus("ANALISANDO SOLICITAÇÃO...")
        val payload = JSONObject()
            .put("message", if (text.isEmpty()) "Analise os anexos enviados." else text)
            .put("sessionId", sessionId)
            .put("profileId", profileId)
            .put("mode", "assistant")
            .put("deviceContext", deviceContext())
            .put("attachments", outgoingAttachments)

        executor.execute {
            try {
                val response = request(WORKER_URL + "/", "POST", payload)
                val reply = response.optString("reply")
                if (reply.isBlank()) throw IOException("O núcleo não retornou uma resposta.")
                val actions = response.optJSONArray("clientActions") ?: JSONArray()
                val visuals = response.optJSONArray("visuals")
                runOnUiThread {
                    if (visuals != null) showLiveVisuals(visuals)
                    addChatMessage(reply, true)
                    setStatus("RESPOSTA RECEBIDA · " + response.optString("model", "IA"))
                    executeClientActions(actions)
                }
                executor.execute {
                    try { request(WORKER_URL + "/?action=tasks&profileId=" + profileId) } catch (_: Exception) {}
                }
            } catch (error: Exception) {
                runOnUiThread {
                    addChatMessage("Não consegui concluir a solicitação. " + (error.message ?: "Falha de conexão."), true, true)
                    setStatus("SOLICITAÇÃO NÃO CONCLUÍDA")
                }
            } finally {
                runOnUiThread { sending = false }
            }
        }
    }

    private fun executeClientAction(action: JSONObject): Pair<Boolean, String> {
        return when (action.optString("type")) {
            "open_app" -> openApp(action.optString("appName"))
            "schedule_reminder" -> scheduleReminder(action.optString("title"), action.optString("at"))
            "open_settings" -> openSystemSettings(action.optString("section"))
            "ui_action" -> JamesAccessibilityService.execute(action)
            else -> false to "Ação Android não reconhecida."
        }
    }

    private fun executeClientActions(actions: JSONArray) {
        for (i in 0 until actions.length()) {
            val action = actions.optJSONObject(i) ?: continue
            val result = executeClientAction(action)
            addChatMessage((if (result.first) "ANDROID CONFIRMOU: " else "ANDROID NÃO CONCLUIU: ") + result.second, result.first, !result.first)
        }
    }

    private fun mergeLiveTranscript(buffer: StringBuilder, incomingText: String) {
        val incoming = incomingText.trim()
        if (incoming.isBlank()) return
        val current = buffer.toString()
        when {
            current.isBlank() -> buffer.append(incoming)
            incoming.startsWith(current) -> { buffer.clear(); buffer.append(incoming) }
            current.endsWith(incoming) -> Unit
            else -> buffer.append(" ").append(incoming)
        }
    }

    private fun finishLiveTurn() {
        val userText = liveUserTranscriptBuffer.toString().trim()
        val modelText = liveAssistantTranscriptBuffer.toString().trim()
        liveUserTranscriptBuffer.clear()
        liveAssistantTranscriptBuffer.clear()
        if (userText.isBlank() && modelText.isBlank()) return
        if (userText.isNotBlank() && !liveUserTranscriptDisplayed) addChatMessage(userText, false)
        if (modelText.isNotBlank()) addChatMessage(modelText, true)
        liveUserTranscriptDisplayed = false
        persistLiveTurn(userText, modelText)
        if (userText.isNotBlank()) handleVoiceCommand(userText)
    }

    private fun persistLiveTurn(userText: String, modelText: String) {
        val activeSession = sessionId
        val activeProfile = profileId
        executor.execute {
            try {
                val connection = URL("$WORKER_URL/?action=live-turn").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                connection.doOutput = true
                connection.setRequestProperty("Origin", APP_ORIGIN)
                connection.setRequestProperty("Content-Type", "application/json")
                val payload = JSONObject()
                    .put("sessionId", activeSession)
                    .put("profileId", activeProfile)
                    .put("userText", userText)
                    .put("modelText", modelText)
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                if (code !in 200..299) {
                    val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    android.util.Log.w("JamesLiveHistory", "Live history sync returned HTTP $code: $detail")
                }
                connection.disconnect()
            } catch (error: Exception) {
                android.util.Log.w("JamesLiveHistory", "Live history sync unavailable", error)
            }
        }
    }

    private fun showLiveVisuals(items: JSONArray) {
        val safe = (0 until minOf(items.length(), 6)).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val imageUrl = item.optString("imageUrl")
            val sourceUrl = item.optString("sourceUrl")
            try {
                val image = URL(imageUrl)
                val source = URL(sourceUrl)
                if (image.protocol != "https" || image.host != "upload.wikimedia.org" ||
                    source.protocol != "https" || source.host != "commons.wikimedia.org") return@mapNotNull null
            } catch (_: Exception) { return@mapNotNull null }
            item
        }
        if (safe.isEmpty()) return
        liveVisualRequestId++
        val requestId = liveVisualRequestId
        liveVisualRunnable?.let { liveVisualHandler.removeCallbacks(it) }
        liveVisuals = safe
        lastLiveVisuals = safe
        liveVisualIndex = 0
        if (currentPage == "intelligence" && ::chatMessages.isInitialized) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = background(0xFF071827.toInt(), COLOR_CYAN)
            }
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = "Imagem ilustrativa do Jâmes"
            }
            val credit = TextView(this).apply {
                textSize = 9f
                setTextColor(COLOR_MUTED)
                gravity = Gravity.CENTER
            }
            card.addView(image, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(160)))
            card.addView(credit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(5) })
            chatMessages.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8); bottomMargin = dp(8)
            })
            liveVisualChatCard = card
            liveVisualChatImage = image
            liveVisualChatCredit = credit
            chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
        } else {
            liveVisualChatCard = null
            liveVisualChatImage = null
            liveVisualChatCredit = null
        }
        renderNextLiveVisual(requestId)
    }

    private fun renderNextLiveVisual(requestId: Int) {
        if (requestId != liveVisualRequestId || liveVisualIndex >= liveVisuals.size) {
            if (requestId == liveVisualRequestId) stopLiveVisuals()
            return
        }
        val item = liveVisuals[liveVisualIndex]
        val imageUrl = item.optString("imageUrl")
        Thread {
            var bitmap: Bitmap? = null
            try {
                val connection = URL(imageUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.instanceFollowRedirects = false
                connection.inputStream.use { bitmap = BitmapFactory.decodeStream(it) }
                connection.disconnect()
            } catch (_: Exception) { }
            runOnUiThread {
                if (requestId != liveVisualRequestId) {
                    bitmap?.recycle()
                    return@runOnUiThread
                }
                val decoded = bitmap
                if (decoded != null) {
                    val creditText = (item.optString("credit", "Wikimedia Commons") + " · " + item.optString("license", "Consulte a licença")).take(150)
                    if (currentPage == "intelligence" && liveVisualChatImage != null) {
                        liveVisualChatImage?.setImageBitmap(decoded)
                        liveVisualChatCredit?.text = creditText
                        liveVisualChatCard?.setOnClickListener {
                            try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(item.optString("sourceUrl")))) } catch (_: Exception) { }
                        }
                    } else {
                        liveVisualImage?.setImageBitmap(decoded)
                        liveVisualCredit?.text = creditText
                        liveVisualPanel?.visibility = View.VISIBLE
                        liveVisualPanel?.setOnClickListener {
                            try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(item.optString("sourceUrl")))) } catch (_: Exception) { }
                        }
                    }
                }
                liveVisualIndex++
                val next = Runnable { renderNextLiveVisual(requestId) }
                liveVisualRunnable = next
                liveVisualHandler.postDelayed(next, 7000)
            }
        }.start()
    }

    private fun stopLiveVisuals() {
        liveVisualRequestId++
        liveVisualRunnable?.let { liveVisualHandler.removeCallbacks(it) }
        liveVisualRunnable = null
        liveVisuals = emptyList()
        liveVisualIndex = 0
        liveVisualPanel?.visibility = View.GONE
        liveVisualImage?.setImageDrawable(null)
        liveVisualCredit?.text = ""
        liveVisualChatCard = null
        liveVisualChatImage = null
        liveVisualChatCredit = null
    }

    private fun handleLiveToolCall(callId: String, name: String, args: JSONObject) {
        executor.execute {
            try {
                val payload = JSONObject().put("sessionId", sessionId).put("profileId", profileId).put("name", name).put("args", args)
                val response = request(WORKER_URL + "/?action=live-tool", "POST", payload)
                val result = response.optJSONObject("result") ?: JSONObject().put("error", "A ferramenta não retornou resultado.")
                val visuals = result.optJSONArray("visuals")
                if (visuals != null) runOnUiThread { showLiveVisuals(visuals) }
                val clientAction = result.optJSONObject("clientAction")
                if (clientAction != null) {
                    runOnUiThread {
                        val outcome = executeClientAction(clientAction)
                        addChatMessage((if (outcome.first) "ANDROID CONFIRMOU: " else "ANDROID NÃO CONCLUIU: ") + outcome.second, outcome.first, !outcome.first)
                        liveVoiceSession?.sendToolResponse(callId, name, JSONObject().put("success", outcome.first).put("message", outcome.second))
                    }
                } else {
                    liveVoiceSession?.sendToolResponse(callId, name, result)
                }
            } catch (error: Exception) {
                liveVoiceSession?.sendToolResponse(callId, name, JSONObject().put("error", error.message ?: "Falha ao executar ferramenta."))
            }
        }
    }

    private fun loadTasksInto(panel: LinearLayout) {
        panel.removeViews(3, (panel.childCount - 3).coerceAtLeast(0))
        executor.execute {
            try {
                val result = request(WORKER_URL + "/?action=tasks&sessionId=" + sessionId)
                val tasks = result.optJSONArray("tasks") ?: JSONArray()
                runOnUiThread {
                    if (tasks.length() == 0) addText(panel, "Nenhuma tarefa registrada.", 11f, COLOR_MUTED, top = 10)
                    for (i in 0 until tasks.length()) {
                        val task = tasks.optJSONObject(i) ?: continue
                        val title = task.optString("title")
                        val done = task.optString("status") == "completed"
                        addInfoLine(panel, if (done) "✓ Concluída" else "Em aberto", title)
                    }
                }
            } catch (error: Exception) {
                runOnUiThread { addText(panel, "Não foi possível consultar tarefas: " + (error.message ?: "erro"), 10f, COLOR_RED, top = 10) }
            }
        }
    }

    private fun loadProfileInstructions() {
        val targetProfile = profileId
        executor.execute {
            try {
                val result = request("$WORKER_URL/?action=memories&profileId=$targetProfile")
                val memories = result.optJSONArray("memories") ?: JSONArray()
                var instructions = ""
                for (index in 0 until memories.length()) {
                    val item = memories.optJSONObject(index) ?: continue
                    if (item.optString("key") == "instrucoes_personalizadas") {
                        instructions = item.optString("value")
                        break
                    }
                }
                runOnUiThread { profileInstructionsInput?.setText(instructions) }
            } catch (error: Exception) {
                android.util.Log.w("JamesProfile", "Personalization could not be loaded", error)
            }
        }
    }

    private fun saveProfileInstructions() {
        val value = profileInstructionsInput?.text?.toString()?.trim()?.take(1200).orEmpty()
        val targetSession = sessionId
        val targetProfile = profileId
        executor.execute {
            try {
                var name = "save_memory"
                var args = JSONObject().put("key", "instrucoes_personalizadas").put("value", value)
                if (value.isBlank()) {
                    val existing = request("$WORKER_URL/?action=memories&profileId=$targetProfile")
                        .optJSONArray("memories") ?: JSONArray()
                    val memory = (0 until existing.length()).mapNotNull { existing.optJSONObject(it) }
                        .firstOrNull { it.optString("key") == "instrucoes_personalizadas" }
                    if (memory == null) {
                        runOnUiThread { setStatus("Não há personalização salva para remover.") }
                        return@execute
                    }
                    name = "delete_memory"
                    args = JSONObject().put("memory_id", memory.optLong("id"))
                }
                val payload = JSONObject()
                    .put("sessionId", targetSession)
                    .put("profileId", targetProfile)
                    .put("name", name)
                    .put("args", args)
                val response = request("$WORKER_URL/?action=live-tool", "POST", payload)
                val result = response.optJSONObject("result") ?: JSONObject()
                if (result.has("error") || result.optBoolean("success") == false) {
                    throw IOException(result.optString("error").ifBlank { "O perfil não confirmou a alteração." })
                }
                runOnUiThread {
                    setStatus(if (value.isBlank()) "PERSONALIZAÇÃO REMOVIDA" else "PERSONALIZAÇÃO SALVA NO PERFIL")
                    loadProfileInstructions()
                }
            } catch (error: Exception) {
                runOnUiThread { setStatus("FALHA AO SALVAR PERSONALIZAÇÃO: " + (error.message ?: "erro")) }
            }
        }
    }

    private fun loadMemoriesInto(panel: LinearLayout) {
        panel.removeViews(3, (panel.childCount - 3).coerceAtLeast(0))
        executor.execute {
            try {
                val result = request(WORKER_URL + "/?action=memories&profileId=" + profileId)
                val memories = result.optJSONArray("memories") ?: JSONArray()
                runOnUiThread {
                    if (memories.length() == 0) addText(panel, "Nenhuma memória foi guardada explicitamente neste perfil.", 11f, COLOR_MUTED, top = 10)
                    for (i in 0 until memories.length()) {
                        val memory = memories.optJSONObject(i) ?: continue
                        addInfoLine(panel, memory.optString("key"), memory.optString("value"))
                    }
                }
            } catch (error: Exception) {
                runOnUiThread { addText(panel, "Memória indisponível: " + (error.message ?: "erro"), 10f, COLOR_RED, top = 10) }
            }
        }
    }

    private fun refreshBackendStatus() {
        executor.execute {
            try {
                val result = request(WORKER_URL)
                val online = result.optString("status") == "online"
                val ai = result.optBoolean("aiConfigured", false)
                val db = result.optBoolean("databaseConfigured", false)
                val summary = when {
                    !online -> "WORKER OFFLINE"
                    !ai -> "NÚCLEO ONLINE · SERVIÇO NÃO VALIDADO"
                    else -> "NÚCLEO ONLINE · MEMÓRIA " + if (db) "CONECTADA" else "INDISPONÍVEL"
                }
                runOnUiThread {
                    backendSummary = summary
                    statusText.text = if (ai && online) "NÚCLEO ONLINE" else "NÚCLEO PARCIAL"
                    statusText.setTextColor(if (ai && online) COLOR_GREEN else COLOR_RED)
                    if (currentPage == "overview" || currentPage == "device") showPage(currentPage)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    backendSummary = "NÚCLEO OFFLINE · " + (error.message ?: "sem conexão")
                    statusText.text = "NÚCLEO OFFLINE"
                    statusText.setTextColor(COLOR_RED)
                    if (currentPage == "overview" || currentPage == "device") showPage(currentPage)
                }
            }
        }
    }

    private fun request(url: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 20000
        connection.readTimeout = 30000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Origin", APP_ORIGIN)
        connection.setRequestProperty("User-Agent", "JamesAndroid/3.2.0")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        val response = try { JSONObject(responseText) } catch (_: Exception) { JSONObject() }
        if (status !in 200..299) {
            throw IOException(response.optString("error").ifBlank { "O serviço respondeu HTTP " + status + "." })
        }
        return response
    }

    private fun deviceContext(): JSONObject {
        return JSONObject()
            .put("platform", "android")
            .put("timezone", TimeZone.getDefault().id)
            .put("localNow", OffsetDateTime.now().withNano(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            .put("device", deviceInfo())
    }

    private fun deviceInfo(): JSONObject {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryPercent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val batteryIntent = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryStatus = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING || batteryStatus == BatteryManager.BATTERY_STATUS_FULL
        val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivity.activeNetwork
        val capabilities = network?.let { connectivity.getNetworkCapabilities(it) }
        val connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("androidVersion", Build.VERSION.RELEASE)
            .put("apiLevel", Build.VERSION.SDK_INT)
            .put("batteryPercent", if (batteryPercent in 0..100) batteryPercent else JSONObject.NULL)
            .put("charging", charging)
            .put("networkConnected", connected)
    }

    private fun startVoiceInput() {
        if (!liveConversationEnabled) startLiveConversation()
        else setStatus("JÂMES JÁ ESTÁ OUVINDO")
    }

    private fun toggleLiveConversation() {
        if (liveConversationEnabled) stopLiveConversation() else startLiveConversation()
    }

    /** Botão mestre: inicia voz e solicita visão separadamente, respeitando as permissões Android. */
    private fun toggleJamesPower() {
        if (liveConversationEnabled) {
            stopLiveConversation()
            return
        }
        liveVisionPending = true
        setStatus("ATIVANDO JÂMES · AGUARDANDO PERMISSÕES DO APARELHO")
        startLiveConversation()
    }

    private fun startLiveConversation() {
        if (liveConversationEnabled) return
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MICROPHONE)
            return
        }
        stopLiveVisuals()
        try {
            JamesSessionService.start(this)
        } catch (error: Exception) {
            liveVisionPending = false
            liveButton?.text = "LIGAR JÂMES"
            setStatus("NÃO FOI POSSÍVEL MANTER A SESSÃO EM PRIMEIRO PLANO")
            addChatMessage(error.message ?: "O Android não permitiu iniciar o serviço de sessão.", true, true)
            return
        }
        liveConversationEnabled = true
        if (liveVisionPending) {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                try { JamesSessionService.setCameraEnabled(this, true) } catch (_: Exception) { }
            } else if (!cameraPermissionRequested) {
                cameraPermissionRequested = true
                requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA_PERMISSION)
            }
        }
        liveButton?.text = "INICIANDO JÂMES..."
        setStatus("SOLICITANDO SESSÃO SEGURA DE VOZ")
        executor.execute {
            try {
                val tokenData = request(WORKER_URL + "/?action=live-token", "POST", JSONObject().put("sessionId", sessionId).put("profileId", profileId).put("voiceProfile", selectedVoiceProfile).put("responseModality", "AUDIO"))
                val token = tokenData.optString("token")
                if (token.isBlank()) throw IOException("O núcleo não forneceu um token de sessão.")
                runOnUiThread {
                    if (!liveConversationEnabled) return@runOnUiThread
                    val voice = voiceProfileModels[selectedVoiceProfile.coerceIn(0, 2)]
                    liveVoiceSession = JamesLiveAudioSession(this, token, voice, object : JamesLiveAudioSession.Listener {
                        override fun onStatus(message: String) {
                            runOnUiThread {
                                if (liveConversationEnabled) {
                                    setStatus(message)
                                    if (message == "JÂMES AO VIVO · OUVINDO") finishLiveTurn()
                                    if (message.contains("JÂMES AO VIVO")) {
                                        liveButton?.text = "DESLIGAR JÂMES"
                                        if (pendingVoicePreview) {
                                            pendingVoicePreview = false
                                            liveVoiceSession?.sendText("Faça uma apresentação curta em português brasileiro para demonstrar sua voz. Diga apenas: Olá. Eu sou o Jâmes. Como posso ajudar?")
                                        }
                                    }
                                    if (liveVisionPending && message.contains("JÂMES AO VIVO")) startLiveVision()
                                }
                            }
                        }
                        override fun onUserTranscript(text: String) {
                            runOnUiThread {
                                if (liveConversationEnabled) {
                                    if (liveUserTranscriptBuffer.isEmpty()) {
                                        stopLiveVisuals()
                                        lastLiveVisuals = emptyList()
                                    }
                                    mergeLiveTranscript(liveUserTranscriptBuffer, text)
                                }
                            }
                        }
                        override fun onAssistantTranscript(text: String) {
                            runOnUiThread {
                                if (liveConversationEnabled) mergeLiveTranscript(liveAssistantTranscriptBuffer, text)
                            }
                        }
                        override fun onAudioLevel(level: Float, speaking: Boolean) {
                            runOnUiThread { orbView?.setAudioLevel(level); orbView?.setMode(if (speaking) 2 else if (liveConversationEnabled) 1 else 0) }
                        }
                        override fun onToolCall(id: String, name: String, args: JSONObject) {
                            handleLiveToolCall(id, name, args)
                        }
                        override fun onFailure(message: String) {
                            runOnUiThread {
                                if (liveConversationEnabled) {
                                    setStatus("VOZ AO VIVO INDISPONÍVEL")
                                    addChatMessage(message, true, true)
                                    stopLiveConversation()
                                }
                            }
                        }
                    }).also { it.start() }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    liveConversationEnabled = false
                    liveVisionPending = false
                    cameraPermissionRequested = false
                    try { JamesSessionService.stop(this) } catch (_: Exception) { }
                    liveButton?.text = "LIGAR JÂMES"
                    setStatus("NÃO FOI POSSÍVEL INICIAR A VOZ")
                    addChatMessage(error.message ?: "Falha ao iniciar a sessão de voz.", true, true)
                }
            }
        }
    }

    private fun restartLiveConversation() {
        val restoreVision = liveCamera != null || liveVisionPending
        stopLiveConversation()
        liveVisionPending = restoreVision
        Thread({
            try { Thread.sleep(2200) } catch (_: InterruptedException) { return@Thread }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) startLiveConversation()
            }
        }, "JamesVoiceProfileSwitch").apply { isDaemon = true; start() }
    }

    private fun stopLiveVision() {
        liveCamera?.close()
        liveCamera = null
        if (liveConversationEnabled) {
            try { JamesSessionService.setCameraEnabled(this, false) } catch (_: Exception) { }
        }
        liveVisionPending = false
        cameraPermissionRequested = false
        visionButton?.text = "ATIVAR VISÃO AO VIVO"
        setStatus("VISÃO AO VIVO DESATIVADA")
    }

    private fun toggleLiveVision() {
        if (liveCamera != null || liveVisionPending) {
            stopLiveVision()
            return
        }
        liveVisionPending = true
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionRequested = true
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA_PERMISSION)
            return
        }
        if (liveConversationEnabled) {
            try { JamesSessionService.setCameraEnabled(this, true) } catch (_: Exception) { }
        }
        if (liveVoiceSession == null) {
            if (!liveConversationEnabled) startLiveConversation()
            else setStatus("AGUARDANDO A SESSÃO DE VOZ...")
            return
        }
        startLiveVision()
    }

    private fun startLiveVision() {
        if (!liveVisionPending || liveVoiceSession == null || liveCamera != null) return
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA_PERMISSION)
            return
        }
        try {
            JamesSessionService.setCameraEnabled(this, true)
            visionButton?.text = "DESATIVAR VISÃO AO VIVO"
            liveCamera = JamesLiveCamera(
                this,
                onFrame = { bytes -> liveVoiceSession?.sendVideoFrame(bytes) },
                onReady = { runOnUiThread { if (liveVisionPending) setStatus("VISÃO E VOZ AO VIVO ATIVAS") } },
                onFailure = { message ->
                    runOnUiThread {
                        addChatMessage(message, true, true)
                        stopLiveVision()
                    }
                }
            ).also { it.start() }
        } catch (error: Exception) {
            stopLiveVision()
            addChatMessage(error.message ?: "Não foi possível ativar a câmera.", true, true)
        }
    }

    private fun stopLiveConversation() {
        stopLiveVision()
        liveConversationEnabled = false
        liveVoiceSession?.close()
        liveVoiceSession = null
        liveUserTranscriptBuffer.clear()
        liveAssistantTranscriptBuffer.clear()
        liveUserTranscriptDisplayed = false
        stopLiveVisuals()
        try { JamesSessionService.stop(this) } catch (_: Exception) { }
        orbView?.setAudioLevel(0f)
        liveButton?.text = "LIGAR JÂMES"
        setStatus("CONVERSA AO VIVO ENCERRADA")
    }

    private fun applyVoiceProfile(profile: Int) {
        selectedVoiceProfile = profile.coerceIn(0, 2)
        voiceProfileLabel?.text = "PERFIL VOCAL  ·  " + voiceProfileNames[selectedVoiceProfile]
    }

    private fun handleVoiceCommand(text: String): Boolean {
        val normalized = java.text.Normalizer.normalize(text.lowercase(Locale("pt", "BR")), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        val asksToStopVision = normalized.contains("desligar visao") || normalized.contains("desativar visao") || normalized.contains("fechar os olhos")
        val asksToStartVision = normalized.contains("ligar visao") || normalized.contains("ativar visao") || normalized.contains("abra os olhos")
        if (asksToStopVision) { stopLiveVision(); return true }
        if (asksToStartVision) { if (liveCamera == null && !liveVisionPending) toggleLiveVision(); return true }
        val namedVoiceOnly = normalized.trim() in listOf("jarvis", "patrick jane", "patrick james", "damon", "damon salvatore")
        val asksForVoice = namedVoiceOnly || normalized.contains("mudar voz") || normalized.contains("mudar para voz") ||
            normalized.contains("mudar para jarvis") || normalized.contains("mudar para patrick") || normalized.contains("mudar para damon") ||
            normalized.contains("mudar a voz") || normalized.contains("trocar voz") || normalized.contains("trocar a voz") ||
            normalized.contains("alterar voz") || normalized.contains("escolher voz") || normalized.contains("usar voz") ||
            normalized.contains("voz do james") || normalized.contains("voz jarvis") || normalized.contains("voz damon") || normalized.contains("voz patrick")
        if (!asksForVoice) return false
        val index = when {
            normalized.contains("voz 3") || normalized.contains("voz tres") || normalized.contains("terceira voz") ||
                normalized.contains("clecio") || normalized.contains("damon") || normalized.contains("salvatore") -> 2
            normalized.contains("voz 2") || normalized.contains("voz dois") || normalized.contains("segunda voz") ||
                normalized.contains("marcio") || normalized.contains("patrick") || normalized.contains("jane") -> 1
            normalized.contains("voz 1") || normalized.contains("voz um") || normalized.contains("primeira voz") ||
                normalized.contains("eduardo") || normalized.contains("jarvis") -> 0
            else -> -1
        }
        if (index >= 0) {
            applyVoiceProfile(index)
            getSharedPreferences("james_core", MODE_PRIVATE).edit().putInt("voice_profile", index).apply()
            setStatus("PERFIL VOCAL " + (index + 1) + " SELECIONADO")
            if (liveConversationEnabled) restartLiveConversation()
        } else {
            val next = (selectedVoiceProfile + 1) % 3
            applyVoiceProfile(next)
            getSharedPreferences("james_core", MODE_PRIVATE).edit().putInt("voice_profile", next).apply()
            setStatus("PERFIL VOCAL " + (next + 1) + " SELECIONADO")
            if (liveConversationEnabled) restartLiveConversation()
        }
        return true
    }

    private fun openCamera() {
        try {
            startActivityForResult(Intent("android.media.action.IMAGE_CAPTURE"), REQ_CAMERA)
        } catch (_: Exception) {
            setStatus("CÂMERA INDISPONÍVEL")
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png", "image/webp", "image/gif", "application/pdf"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, REQ_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) {
            if (requestCode == REQ_EXPORT_TEXT) { pendingExportText = null; pendingExportPdf = false }
            return
        }
        when (requestCode) {
            REQ_EXPORT_TEXT -> {
                val content = pendingExportText ?: return
                pendingExportText = null
                val uri = data?.data ?: return
                try {
                    val output = contentResolver.openOutputStream(uri) ?: throw IOException("Destino de arquivo indisponível.")
                    output.use {
                        if (pendingExportPdf) writeTextPdf(content, it)
                        else it.write(content.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(this, if (pendingExportPdf) "PDF criado." else "Arquivo TXT criado.", Toast.LENGTH_LONG).show()
                } catch (error: Exception) {
                    Toast.makeText(this, "Falha ao salvar o anexo: " + (error.message ?: "erro"), Toast.LENGTH_LONG).show()
                } finally {
                    pendingExportPdf = false
                }
            }
            REQ_CAMERA -> {
                val bitmap = data?.extras?.get("data") as? Bitmap ?: return
                val output = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
                addAttachment("captura-camera.jpg", "image/jpeg", output.toByteArray())
            }
            REQ_FILE -> {
                val uri = data?.data ?: return
                try {
                    val mime = contentResolver.getType(uri) ?: "application/octet-stream"
                    if (!(mime.startsWith("image/") || mime == "application/pdf")) {
                        setStatus("SELECIONE UMA IMAGEM OU PDF")
                        return
                    }
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
                    addAttachment(queryDisplayName(uri), mime, bytes)
                } catch (error: Exception) {
                    setStatus("NÃO FOI POSSÍVEL LER O ARQUIVO")
                }
            }
        }
    }

    private fun addAttachment(name: String, mime: String, bytes: ByteArray) {
        if (bytes.size > 8 * 1024 * 1024 || pendingAttachments.sumOf { encodedSize(it.optString("dataUrl")) } + bytes.size > 8 * 1024 * 1024) {
            setStatus("LIMITE TOTAL DE 8 MB POR ENVIO")
            return
        }
        if (pendingAttachments.size >= 5) {
            setStatus("MÁXIMO DE 5 ANEXOS")
            return
        }
        val dataUrl = "data:" + mime + ";base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        pendingAttachments.add(JSONObject().put("name", name).put("dataUrl", dataUrl))
        val label = pendingAttachments.joinToString(", ") { it.optString("name") }
        if (::attachmentStatus.isInitialized) attachmentStatus.text = "ANEXOS: " + label
        setStatus("ANEXO PRONTO PARA ANÁLISE")
    }

    private fun encodedSize(dataUrl: String): Int {
        val comma = dataUrl.indexOf(',')
        if (comma < 0) return 0
        val encoded = dataUrl.length - comma - 1
        val padding = if (dataUrl.endsWith("==")) 2 else if (dataUrl.endsWith("=")) 1 else 0
        return (encoded * 3 / 4) - padding
    }

    private fun queryDisplayName(uri: android.net.Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null) ?: return "anexo"
        cursor.use {
            val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && it.moveToFirst()) return it.getString(index) ?: "anexo"
        }
        return "anexo"
    }

    private fun openApp(appName: String): Pair<Boolean, String> {
        val requested = appName.trim()
        if (requested.isBlank()) return false to "Nome do aplicativo vazio."
        return try {
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val matches = packageManager.queryIntentActivities(launcher, 0).filter {
                it.loadLabel(packageManager).toString().equals(requested, ignoreCase = true)
            }
            if (matches.size != 1) {
                return false to if (matches.isEmpty()) "Não encontrei " + requested + " instalado." else "Há mais de um aplicativo chamado " + requested + "."
            }
            val packageName = matches.first().activityInfo.packageName
            val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return false to "O aplicativo não oferece uma ação de abertura."
            startActivity(launch)
            true to requested + " aberto."
        } catch (error: Exception) {
            false to "Falha ao abrir " + requested + "."
        }
    }

    private fun scheduleReminder(title: String, at: String): Pair<Boolean, String> {
        if (title.isBlank()) return false to "O lembrete não tem título."
        val epochMillis = try { OffsetDateTime.parse(at).toInstant().toEpochMilli() } catch (_: Exception) { return false to "Data/hora inválida." }
        if (epochMillis <= System.currentTimeMillis() || epochMillis > System.currentTimeMillis() + 366L * 24 * 60 * 60 * 1000) {
            return false to "O horário precisa estar no futuro e dentro de um ano."
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
            return false to "Permissão de notificações solicitada. Autorize e peça o lembrete novamente."
        }
        val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= 31 && !alarm.canScheduleExactAlarms()) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + packageName)))
            return false to "O Android solicitou autorização para alarmes exatos. Autorize e peça o lembrete novamente."
        }
        val id = UUID.randomUUID().hashCode()
        val intent = Intent(this, ReminderReceiver::class.java).putExtra("title", title).putExtra("id", id)
        val pending = PendingIntent.getBroadcast(this, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        getSharedPreferences("james_reminders", MODE_PRIVATE).edit().putString(id.toString(), title + "|" + epochMillis).apply()
        return try {
            if (Build.VERSION.SDK_INT >= 23) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMillis, pending)
            else alarm.setExact(AlarmManager.RTC_WAKEUP, epochMillis, pending)
            true to "Lembrete agendado no Android."
        } catch (_: Exception) {
            false to "O Android não permitiu agendar este lembrete."
        }
    }

    private fun openSystemSettings(section: String): Pair<Boolean, String> {
        val action = when (section) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "notifications" -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
            else -> return false to "Seção de configurações não reconhecida."
        }
        return try {
            val intent = Intent(action)
            if (section == "notifications") intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(intent)
            true to "Configurações do Android abertas."
        } catch (_: Exception) {
            false to "Não foi possível abrir as configurações."
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("james_reminders", "Lembretes do Jâmes", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun verticalPage(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun mountScrollable(page: LinearLayout) {
        val scroller = ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        }
        content.addView(scroller, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = background(COLOR_PANEL, 0xFF1B3A50.toInt())
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.bottomMargin = dp(12)
            layoutParams = params
        }
    }

    private fun addText(parent: LinearLayout, text: String, size: Float, color: Int, bold: Boolean = false, top: Int = 0): TextView {
        val view = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setPadding(0, dp(top), 0, dp(3))
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        parent.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return view
    }

    private fun addInfoLine(parent: LinearLayout, label: String, value: String) {
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(9), 0, dp(9))
        }
        addText(line, label.uppercase(Locale("pt", "BR")), 9f, COLOR_CYAN, true)
        addText(line, value, 11f, COLOR_TEXT, top = 3)
        parent.addView(line)
        val divider = View(this).apply { setBackgroundColor(0xFF1B3548.toInt()) }
        parent.addView(divider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
    }

    private fun addModule(parent: LinearLayout, title: String, detail: String, action: () -> Unit) {
        val button = makeButton(title + "  ›", true) { action() }
        button.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        parent.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(8) })
        addText(parent, detail, 10f, COLOR_MUTED, top = 2)
    }

    private fun addButton(parent: LinearLayout, label: String, secondary: Boolean = false, action: () -> Unit) {
        val button = makeButton(label, secondary, action)
        parent.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(8) })
    }

    private fun makeButton(label: String, secondary: Boolean = false, action: (() -> Unit)? = null): Button {
        return Button(this).apply {
            text = label
            textSize = 10f
            isAllCaps = false
            setTextColor(if (secondary) COLOR_TEXT else 0xFF04121A.toInt())
            background = background(if (secondary) 0xFF13273A.toInt() else COLOR_CYAN, if (secondary) 0xFF29465A.toInt() else COLOR_CYAN)
            minimumHeight = dp(40)
            setPadding(dp(12), 0, dp(12), 0)
            if (action != null) setOnClickListener { action() }
        }
    }

    private fun background(fill: Int, stroke: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), stroke)
        }
    }

    private fun addChatMessage(text: String, fromJames: Boolean, isError: Boolean = false) {
        if (!::chatMessages.isInitialized) return
        if (!fromJames && !isError) updateCurrentConversationTitle(text)
        val bubble = TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(if (isError) COLOR_RED else COLOR_TEXT)
            setPadding(dp(13), dp(11), dp(13), dp(11))
            background = background(if (fromJames) 0xFF0F2537.toInt() else 0xFF10415A.toInt(), if (isError) COLOR_RED else 0xFF23445A.toInt())
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.bottomMargin = dp(9)
        if (fromJames && !isError) {
            bubble.setOnLongClickListener {
                exportChatText(text)
                true
            }
        }
        chatMessages.addView(bubble, params)
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun exportChatText(text: String) {
        if (text.isBlank()) return
        AlertDialog.Builder(this)
            .setTitle("CRIAR ANEXO")
            .setItems(arrayOf("Documento PDF", "Arquivo TXT")) { _, choice ->
                pendingExportText = text
                pendingExportPdf = choice == 0
                val pdf = pendingExportPdf
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = if (pdf) "application/pdf" else "text/plain"
                    putExtra(Intent.EXTRA_TITLE, if (pdf) "james-resposta.pdf" else "james-resposta.txt")
                }
                try { startActivityForResult(intent, REQ_EXPORT_TEXT) }
                catch (_: Exception) {
                    pendingExportText = null
                    pendingExportPdf = false
                    Toast.makeText(this, "Não foi possível criar o anexo.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun writeTextPdf(text: String, output: java.io.OutputStream) {
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 12f }
        val pageWidth = 595
        val pageHeight = 842
        val maxWidth = 511f
        val lines = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            var current = ""
            paragraph.split(Regex("\\s+")).forEach { word ->
                val candidate = if (current.isEmpty()) word else current + " " + word
                if (current.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                    lines.add(current)
                    current = word
                } else current = candidate
            }
            lines.add(current)
            lines.add("")
        }
        var lineIndex = 0
        var pageNumber = 1
        do {
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = document.startPage(info)
            page.canvas.drawColor(Color.WHITE)
            var y = 52f
            while (lineIndex < lines.size && y < pageHeight - 42f) {
                page.canvas.drawText(lines[lineIndex].take(220), 42f, y, paint)
                y += 18f
                lineIndex++
            }
            document.finishPage(page)
            pageNumber++
        } while (lineIndex < lines.size)
        try { document.writeTo(output) } finally { document.close() }
    }

    private fun setStatus(message: String) {
        statusText.text = message.uppercase(Locale("pt", "BR")).take(42)
        statusText.setTextColor(if (message.contains("FALHA") || message.contains("NÃO") || message.contains("OFFLINE")) COLOR_RED else COLOR_GREEN)
        val mode = when {
            message.contains("VISÃO AO VIVO DESATIVADA") || message.contains("VISAO AO VIVO DESATIVADA") -> 0
            message.contains("VISÃO E VOZ AO VIVO ATIVAS") || message.contains("VISAO E VOZ AO VIVO ATIVAS") -> 3
            message.contains("FALANDO") || message.contains("RESPONDENDO") -> 2
            message.contains("OUVINDO") || message.contains("ESCUTANDO") -> 1
            else -> 0
        }
        orbView?.setMode(mode)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFICATIONS) {
            setStatus(if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) "NOTIFICAÇÕES AUTORIZADAS" else "NOTIFICAÇÕES NÃO AUTORIZADAS")
        } else if (requestCode == REQ_MICROPHONE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLiveConversation()
            } else {
                liveConversationEnabled = false
                liveButton?.text = "LIGAR JÂMES"
                liveVisionPending = false
                setStatus("MICROFONE NÃO AUTORIZADO")
            }
        } else if (requestCode == REQ_CAMERA_PERMISSION) {
            cameraPermissionRequested = false
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (liveVisionPending && liveConversationEnabled) {
                    try { JamesSessionService.setCameraEnabled(this, true) } catch (_: Exception) { }
                }
                if (liveVisionPending) {
                    if (liveVoiceSession != null) startLiveVision() else if (!liveConversationEnabled) startLiveConversation()
                }
            } else {
                liveVisionPending = false
                visionButton?.text = "ATIVAR VISÃO AO VIVO"
                setStatus("CÂMERA NÃO AUTORIZADA")
            }
        }
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (currentPage != "overview") showPage("overview") else super.onBackPressed()
    }

    override fun onStop() {
        // A sessão autorizada continua pelo serviço em primeiro plano.
        // O botão OFF ou o encerramento da Activity encerra explicitamente os sensores.
        super.onStop()
    }

    override fun onDestroy() {
        stopLiveConversation()
        executor.shutdownNow()
        super.onDestroy()
    }

    private class FrameLayoutCompat(context: Context) : android.widget.FrameLayout(context)
}
