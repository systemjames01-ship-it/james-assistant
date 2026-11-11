package com.james.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.MediaPlayer
import android.media.AudioManager
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class JamesLiveAudioSession(
    context: Context,
    private val token: String,
    private val voiceName: String,
    private val listener: Listener
) {
    interface Listener {
        fun onStatus(message: String)
        fun onUserTranscript(text: String)
        fun onAssistantTranscript(text: String)
        fun onAudioLevel(level: Float, speaking: Boolean)
        fun onToolCall(id: String, name: String, args: JSONObject)
        fun onFailure(message: String)
    }

    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val running = AtomicBoolean(false)
    @Volatile private var setupReady = false
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var micThread: Thread? = null
    @Volatile private var socket: WebSocket? = null
    @Volatile private var audioTrackStarted = false
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousAudioMode = AudioManager.MODE_NORMAL
    private var previousSpeakerState = false

    fun start() {
        if (!running.compareAndSet(false, true)) return
        previousAudioMode = audioManager.mode
        previousSpeakerState = audioManager.isSpeakerphoneOn
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true

        val request = Request.Builder()
            .url("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent")
            .header("Authorization", "Token $token")
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val setup = JSONObject()
                    .put("model", "models/gemini-3.8-live")
                    .put("generationConfig", JSONObject()
                        .put("responseModalities", org.json.JSONArray().put("AUDIO"))
                        .put("speechConfig", JSONObject().put("voiceConfig", JSONObject()
                            .put("prebuiltVoiceConfig", JSONObject().put("voiceName", voiceName)))))
                    .put("inputAudioTranscription", JSONObject())
                    .put("outputAudioTranscription", JSONObject())
                    .put("realtimeInputConfig", JSONObject().put("automaticActivityDetection", JSONObject().put("disabled", false)))
                    .put("sessionResumption", JSONObject())
                webSocket.send(JSONObject().put("setup", setup).toString())
                listener.onStatus("CONECTANDO À INTELIGÊNCIA DE VOZ")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = JSONObject(text)
                    val toolCall = message.optJSONObject("toolCall")
                    if (toolCall != null) {
                        val calls = toolCall.optJSONArray("functionCalls") ?: org.json.JSONArray()
                        for (index in 0 until calls.length()) {
                            val call = calls.optJSONObject(index) ?: continue
                            listener.onToolCall(call.optString("id"), call.optString("name"), call.optJSONObject("args") ?: JSONObject())
                        }
                        return
                    }
                    if (message.has("setupComplete")) {
                        setupReady = true
                        initializeAudio()
                        startMicrophone()
                        listener.onStatus("JÂMES AO VIVO · OUVINDO E RESPONDENDO")
                        return
                    }
                    val content = message.optJSONObject("serverContent") ?: return
                    val inputText = content.optJSONObject("inputTranscription")?.optString("text").orEmpty()
                    if (inputText.isNotBlank()) listener.onUserTranscript(inputText)
                    val outputText = content.optJSONObject("outputTranscription")?.optString("text").orEmpty()
                    if (outputText.isNotBlank()) listener.onAssistantTranscript(outputText)
                    if (content.optBoolean("interrupted")) {
                        audioTrack?.pause()
                        audioTrack?.flush()
                        audioTrackStarted = false
                    }
                    val parts = content.optJSONObject("modelTurn")?.optJSONArray("parts")
                    if (parts != null) {
                        for (index in 0 until parts.length()) {
                            val inline = parts.optJSONObject(index)?.optJSONObject("inlineData") ?: continue
                            val encoded = inline.optString("data")
                            if (encoded.isBlank()) continue
                            listener.onStatus("JÂMES FALANDO")
                            val pcm = Base64.decode(encoded, Base64.DEFAULT)
                            listener.onAudioLevel(levelOfPcm(pcm, pcm.size), true)
                            val track = audioTrack ?: continue
                            if (!audioTrackStarted) {
                                track.play()
                                audioTrackStarted = true
                            }
                            var offset = 0
                            while (offset < pcm.size && running.get()) {
                                val written = track.write(pcm, offset, pcm.size - offset)
                                if (written <= 0) break
                                offset += written
                            }
                        }
                    }
                    if (content.optBoolean("turnComplete")) listener.onStatus("JÂMES AO VIVO · OUVINDO")
                } catch (error: Exception) {
                    listener.onFailure("Erro ao processar áudio recebido: " + (error.message ?: "falha desconhecida"))
                    close()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (running.get()) listener.onFailure("Conexão de voz perdida: " + (t.message ?: "falha de rede"))
                close()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (running.get()) listener.onStatus("SESSÃO DE VOZ ENCERRADA")
                close()
            }
        })
    }

    fun sendToolResponse(callId: String, name: String, response: JSONObject): Boolean {
        if (!running.get() || !setupReady || callId.isBlank() || name.isBlank()) return false
        val functionResponse = JSONObject().put("id", callId).put("name", name).put("response", response)
        return socket?.send(JSONObject().put("toolResponse", JSONObject()
            .put("functionResponses", org.json.JSONArray().put(functionResponse))).toString()) == true
    }

    fun sendVideoFrame(jpeg: ByteArray): Boolean {
        if (!running.get() || !setupReady || jpeg.isEmpty()) return false
        val payload = JSONObject().put("realtimeInput", JSONObject().put("video", JSONObject()
            .put("mimeType", "image/jpeg")
            .put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))))
        return socket?.send(payload.toString()) == true
    }

    fun sendText(text: String): Boolean {
        if (!running.get() || !setupReady || text.isBlank()) return false
        return socket?.send(JSONObject().put("realtimeInput", JSONObject().put("text", text)).toString()) == true
    }

    private fun initializeAudio() {
        val inputMin = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (inputMin <= 0) throw IllegalStateException("Formato de microfone de 16 kHz não suportado.")
        val recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(16000)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build())
            .setBufferSizeInBytes(maxOf(inputMin * 2, 6400))
            .build()
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("O microfone não pôde ser inicializado.")
        }

        val outputMin = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (outputMin <= 0) {
            recorder.release()
            throw IllegalStateException("Formato de áudio de 24 kHz não suportado.")
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(24000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build())
            .setBufferSizeInBytes(maxOf(outputMin * 2, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            recorder.release()
            track.release()
            throw IllegalStateException("A saída de áudio não pôde ser inicializada.")
        }
        audioRecord = recorder
        audioTrack = track
    }

    private fun levelOfPcm(bytes: ByteArray, size: Int): Float {
        val samples = size / 2
        if (samples <= 0) return 0f
        var sum = 0.0
        var index = 0
        while (index + 1 < size) {
            val sample = (((bytes[index + 1].toInt() shl 8) or (bytes[index].toInt() and 0xFF)).toShort()).toInt() / 32768.0
            sum += sample * sample
            index += 2
        }
        return (kotlin.math.sqrt(sum / samples) * 2.4).toFloat().coerceIn(0f, 1f)
    }

    private fun startMicrophone() {
        val recorder = audioRecord ?: return
        recorder.startRecording()
        micThread = Thread({
            val buffer = ByteArray(3200)
            while (running.get() && setupReady) {
                val count = recorder.read(buffer, 0, buffer.size)
                if (count > 0) {
                    val chunk = if (count == buffer.size) buffer.copyOf() else buffer.copyOf(count)
                    listener.onAudioLevel(levelOfPcm(chunk, chunk.size), false)
                    val payload = JSONObject().put("realtimeInput", JSONObject().put("audio", JSONObject()
                        .put("mimeType", "audio/pcm;rate=16000")
                        .put("data", Base64.encodeToString(chunk, Base64.NO_WRAP))))
                    if (socket?.send(payload.toString()) != true) break
                } else if (count < 0) {
                    listener.onFailure("Falha ao capturar áudio do microfone.")
                    break
                }
            }
        }, "JamesLiveMicrophone").apply { isDaemon = true; start() }
    }

    fun close() {
        if (!running.getAndSet(false)) return
        setupReady = false
        try { socket?.send(JSONObject().put("realtimeInput", JSONObject().put("audioStreamEnd", true)).toString()) } catch (_: Exception) { }
        try { socket?.close(1000, "Jâmes session ended") } catch (_: Exception) { }
        try { audioRecord?.stop() } catch (_: Exception) { }
        try { audioRecord?.release() } catch (_: Exception) { }
        audioRecord = null
        try { audioTrack?.pause(); audioTrack?.flush(); audioTrack?.release() } catch (_: Exception) { }
        audioTrack = null
        try { audioManager.isSpeakerphoneOn = previousSpeakerState; audioManager.mode = previousAudioMode } catch (_: Exception) { }
        client.dispatcher.executorService.shutdown()
    }
}
