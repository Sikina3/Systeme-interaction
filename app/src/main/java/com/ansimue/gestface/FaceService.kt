package com.ansimue.gestface

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker.HandLandmarkerOptions
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.Executors
import android.graphics.*
import android.media.Image
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

class FaceService : LifecycleService() {

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var handLandmarker: HandLandmarker

    private var lastAction = 0L

    override fun onCreate() {
        super.onCreate()

        startForeground(1, createNotification())
        handLandmarker = createHandLandmarker()
        startCamera()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    private fun createNotification(): Notification {

        val channelId = "hand_service"

        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                channelId,
                "Hand control",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Hand control actif")
            .setContentText("Contrôle par la main")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    private fun createHandLandmarker(): HandLandmarker {

        val options = HandLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                    .build()
            )
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(1)
            .build()

        return HandLandmarker.createFromOptions(this, options)
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {

        val providerFuture = ProcessCameraProvider.getInstance(this)

        providerFuture.addListener({

            val provider = providerFuture.get()

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(executor) { proxy ->

                val image = proxy.image
                if (image == null) {
                    proxy.close()
                    return@setAnalyzer
                }

                val mpImage =
                    com.google.mediapipe.framework.image.MediaImageBuilder(image).build()

                val result = handLandmarker.detectForVideo(
                    mpImage,
                    System.currentTimeMillis()
                )

                handleHand(result)

                proxy.close()
            }

            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                analysis
            )

        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleHand(result: HandLandmarkerResult) {

        val svc = MyAccessibilityService.instance ?: return

        if (result.landmarks().isEmpty()) return

        val now = System.currentTimeMillis()
        if (now - lastAction < 800) return

        val hand = result.landmarks()[0]

        val index = hand[8]
        val thumb = hand[4]

        val d = distance(index, thumb)

        // pinch
        if (d < 0.05f) {
            vibrate()
            svc.performClick(540f, 1200f)
            lastAction = now
        }
    }

    private fun distance(a: NormalizedLandmark, b: NormalizedLandmark): Float {
        val dx = a.x() - b.x()
        val dy = a.y() - b.y()
        return sqrt(dx * dx + dy * dy)
    }

    private fun vibrate() {
        val vib = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) {
            vib.vibrate(
                VibrationEffect.createOneShot(
                    80,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(80)
        }
    }
}
