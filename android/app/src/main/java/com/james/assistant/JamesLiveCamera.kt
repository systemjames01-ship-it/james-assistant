package com.james.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Surface
import androidx.core.content.ContextCompat

class JamesLiveCamera(
    context: Context,
    private val onFrame: (ByteArray) -> Unit,
    private val onReady: () -> Unit,
    private val onFailure: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val thread = HandlerThread("JamesLiveCamera").apply { start() }
    private val handler = Handler(thread.looper)
    @Volatile private var device: CameraDevice? = null
    @Volatile private var session: CameraCaptureSession? = null
    @Volatile private var reader: ImageReader? = null
    @Volatile private var running = false
    @Volatile private var lastFrameAt = 0L

    fun start() {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("A permissão da câmera é necessária.")
        }
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: manager.cameraIdList.firstOrNull() ?: throw IllegalStateException("Nenhuma câmera disponível.")
        val characteristics = manager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: throw IllegalStateException("A câmera não oferece fluxos de imagem.")
        val sizes = map.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
        val size = sizes.filter { it.width <= 640 && it.height <= 480 }
            .maxByOrNull { it.width * it.height }
            ?: sizes.minByOrNull { kotlin.math.abs(it.width * it.height - 640 * 480) }
            ?: throw IllegalStateException("A câmera não oferece um formato JPEG compatível.")
        reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2).also { imageReader ->
            imageReader.setOnImageAvailableListener({ source ->
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                image.use {
                    val now = SystemClock.elapsedRealtime()
                    if (!running || now - lastFrameAt < 1000) return@use
                    lastFrameAt = now
                    val buffer = it.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    onFrame(bytes)
                }
            }, handler)
        }
        running = true
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                if (!running) { camera.close(); return }
                device = camera
                val surface: Surface = reader?.surface ?: run {
                    onFailure("Não foi possível inicializar o fluxo da câmera.")
                    close()
                    return
                }
                try {
                    camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(configured: CameraCaptureSession) {
                            if (!running) { configured.close(); return }
                            session = configured
                            try {
                                val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                    addTarget(surface)
                                    set(CaptureRequest.CONTROL_MODE, CameraMetadataCompat.CONTROL_MODE_AUTO)
                                }.build()
                                configured.setRepeatingRequest(request, null, handler)
                                onReady()
                            } catch (error: Exception) {
                                onFailure("Falha ao iniciar a câmera: " + (error.message ?: "erro desconhecido"))
                                close()
                            }
                        }
                        override fun onConfigureFailed(configured: CameraCaptureSession) {
                            onFailure("O Android não conseguiu configurar a câmera para visão ao vivo.")
                            close()
                        }
                    }, handler)
                } catch (error: Exception) {
                    onFailure("Falha ao abrir o fluxo da câmera: " + (error.message ?: "erro desconhecido"))
                    close()
                }
            }
            override fun onDisconnected(camera: CameraDevice) {
                onFailure("A câmera foi desconectada.")
                close()
            }
            override fun onError(camera: CameraDevice, error: Int) {
                onFailure("O Android informou um erro na câmera (" + error + ").")
                close()
            }
        }, handler)
    }

    fun close() {
        if (!running) return
        running = false
        try { session?.stopRepeating(); session?.abortCaptures(); session?.close() } catch (_: Exception) { }
        session = null
        try { device?.close() } catch (_: Exception) { }
        device = null
        try { reader?.setOnImageAvailableListener(null, null); reader?.close() } catch (_: Exception) { }
        reader = null
        thread.quitSafely()
    }

    private object CameraMetadataCompat {
        const val CONTROL_MODE_AUTO = CaptureRequest.CONTROL_MODE_AUTO
    }
}
