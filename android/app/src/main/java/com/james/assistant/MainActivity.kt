package com.james.assistant

import android.Manifest
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONObject
import java.util.UUID
import java.util.Locale

class MainActivity : android.app.Activity() {
    private lateinit var webView: WebView
    private val notificationRequest = 4102
    private val speechRequest = 4103
    private val origin = "https://systemjames01-ship-it.github.io"
    private var textToSpeech: TextToSpeech? = null
    @Volatile private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val languageStatus = textToSpeech?.setLanguage(Locale("pt", "BR")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                ttsReady = languageStatus >= TextToSpeech.LANG_AVAILABLE
            }
        }
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                val target = request.url
                return target.scheme != "https" || target.host != "systemjames01-ship-it.github.io"
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(DeviceBridge(this), "JamesAndroid")
        setContentView(webView)
        webView.loadUrl(origin + "/")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == speechRequest && resultCode == RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            webView.evaluateJavascript("window.jamesNativeSpeechResult && window.jamesNativeSpeechResult(" + JSONObject.quote(spoken) + ");", null)
        }
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("james_reminders", "Lembretes do Jâmes", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    inner class DeviceBridge(private val context: Context) {
        @JavascriptInterface fun startVoiceRecognition(): String {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), speechRequest) }
                return JSONObject().put("success", false).put("message", "Permissão de microfone solicitada. Autorize e toque em falar novamente.").toString()
            }
            if (!SpeechRecognizer.isRecognitionAvailable(context)) return JSONObject().put("success", false).put("message", "Reconhecimento de voz indisponível neste aparelho.").toString()
            runOnUiThread {
                try {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale com o Jâmes")
                    }
                    startActivityForResult(intent, speechRequest)
                } catch (e: Exception) {
                    webView.evaluateJavascript("window.jamesNativeSpeechResult && window.jamesNativeSpeechResult('');", null)
                    Toast.makeText(context, "Não foi possível iniciar o reconhecimento de voz.", Toast.LENGTH_SHORT).show()
                }
            }
            return JSONObject().put("success", true).put("message", "Reconhecimento de voz iniciado.").toString()
        }

        @JavascriptInterface fun speak(text: String): String {
            if (text.length > 15000) return result(false, "Texto longo demais para leitura em voz alta.")
            if (!ttsReady || textToSpeech == null) return result(false, "A voz em português ainda não está pronta ou não está instalada no Android.")
            runOnUiThread { textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "james-reply") }
            return result(true, "Leitura em voz alta iniciada.")
        }

        @JavascriptInterface fun stopSpeaking(): String {
            runOnUiThread { textToSpeech?.stop() }
            return result(true, "Leitura interrompida.")
        }

        @JavascriptInterface fun version(): String = "2.5.0"
        @JavascriptInterface fun platform(): String = "android"

        @JavascriptInterface fun openApp(appName: String): String {
            val requested = appName.trim()
            if (requested.isBlank() || requested.length > 80) return result(false, "Nome de aplicativo inválido.")
            return try {
                val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val matches = packageManager.queryIntentActivities(launcher, 0).filter {
                    it.loadLabel(packageManager).toString().equals(requested, ignoreCase = true)
                }
                if (matches.size != 1) return result(false, if (matches.isEmpty()) "Não encontrei um aplicativo instalado chamado $requested." else "Há mais de um aplicativo chamado $requested; não abri nenhum.")
                val packageName = matches.first().activityInfo.packageName
                val launch = packageManager.getLaunchIntentForPackage(packageName)
                    ?: return result(false, "O aplicativo foi encontrado, mas não oferece uma ação de abertura.")
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launch)
                result(true, "$requested aberto.")
            } catch (e: Exception) { result(false, "Não foi possível abrir o aplicativo: ${e.message ?: "erro do Android"}") }
        }

        @JavascriptInterface fun scheduleReminder(title: String, epochMillis: Long): String {
            if (title.isBlank() || title.length > 180) return result(false, "O lembrete precisa de um título de até 180 caracteres.")
            if (epochMillis <= System.currentTimeMillis() || epochMillis > System.currentTimeMillis() + 366L * 24 * 60 * 60 * 1000) {
                return result(false, "O horário do lembrete precisa estar no futuro e dentro de um ano.")
            }
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), notificationRequest) }
                return result(false, "Permissão de notificações solicitada. Autorize e peça para agendar novamente.")
            }
            val id = UUID.randomUUID().hashCode()
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("title", title)
                putExtra("id", id)
            }
            val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            context.getSharedPreferences("james_reminders", Context.MODE_PRIVATE).edit().putString(id.toString(), title + "|" + epochMillis).apply()
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return try {
                if (Build.VERSION.SDK_INT >= 31 && !alarm.canScheduleExactAlarms()) {
                    val settingsIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(settingsIntent)
                    result(false, "O Android solicitou autorização para alarmes exatos. Autorize e repita o agendamento.")
                } else {
                    if (Build.VERSION.SDK_INT >= 23) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMillis, pending)
                    else alarm.setExact(AlarmManager.RTC_WAKEUP, epochMillis, pending)
                    result(true, "Lembrete agendado no Android.", id)
                }
            } catch (e: Exception) { result(false, "Falha ao agendar lembrete: ${e.message ?: "erro do Android"}") }
        }

        @JavascriptInterface fun openSystemSettings(section: String): String {
            val action = when (section) {
                "wifi" -> Settings.ACTION_WIFI_SETTINGS
                "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                "display" -> Settings.ACTION_DISPLAY_SETTINGS
                "sound" -> Settings.ACTION_SOUND_SETTINGS
                "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                "notifications" -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
                else -> return result(false, "Seção de configurações não reconhecida.")
            }
            return try {
                val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (section == "notifications") intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                startActivity(intent)
                result(true, "Configurações do Android abertas.")
            } catch (e: Exception) { result(false, "Não foi possível abrir as configurações: ${e.message ?: "erro"}") }
        }

        @JavascriptInterface fun shareText(text: String): String {
            if (text.length > 10000) return result(false, "Texto muito longo para compartilhar.")
            return try {
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                startActivity(Intent.createChooser(send, "Compartilhar pelo Jâmes").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                result(true, "Compartilhamento aberto.")
            } catch (e: Exception) { result(false, "Não foi possível abrir o compartilhamento.") }
        }

        private fun result(ok: Boolean, message: String, id: Int? = null): String {
            val value = JSONObject().put("success", ok).put("message", message)
            if (id != null) value.put("id", id)
            return value.toString()
        }
    }
}
