package com.ansimue.gestface

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.*
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import java.util.concurrent.Executors

class FaceService : LifecycleService() {

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var detector: FaceDetector

    private var enabled = true
    private var lastAction = 0L

    override fun onCreate() {
        super.onCreate()

        startForeground(1, createNotification())
        detector = createFaceDetector()
        startCamera()
    }

    override fun onBind(
        intent: Intent
    ): IBinder? = super.onBind(intent)

    private fun createNotification(): Notification {
        val channelId = "face_service"

        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                channelId,
                "GestFace Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("GestFace actif")
            .setContentText("Analyse des gestes en cours…")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    @OptIn(
        ExperimentalGetImage::class
    )
    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)

        providerFuture.addListener({
            val provider = providerFuture.get()

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(executor) { proxy ->
                val media = proxy.image ?: return@setAnalyzer proxy.close()

                val img = InputImage.fromMediaImage(
                    media,
                    proxy.imageInfo.rotationDegrees
                )

                detector.process(img)
                    .addOnSuccessListener { faces ->
                        if (faces.isNotEmpty()) handle(faces[0])
                    }
                    .addOnCompleteListener { proxy.close() }
            }

            provider.unbindAll()
            provider.bindToLifecycle(
                this, // UN VRAI LIFECYCLE OWNER
                CameraSelector.DEFAULT_FRONT_CAMERA,
                analysis
            )

        }, ContextCompat.getMainExecutor(this))
    }

    private fun createFaceDetector(): FaceDetector {
        return FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()
        )
    }

    private fun handle(face: Face) {
        val now = System.currentTimeMillis()
        if (now - lastAction < 700) return

        val smile = face.smilingProbability ?: 0f
        val left = face.leftEyeOpenProbability ?: 1f
        val right = face.rightEyeOpenProbability ?: 1f
        val pitch = face.headEulerAngleX
        val yaw = face.headEulerAngleY
        val roll = face.headEulerAngleZ

        val svc = MyAccessibilityService.instance ?: return

        // Toggle ON/OFF
        if (roll > 15) {
            enabled = !enabled
            vibrate()
            lastAction = now
            return
        }

        if (!enabled) return

        if (smile > 0.8f) {
            vibrate()
            svc.scrollDown()
            lastAction = now
            return
        }

        if (left < 0.25f && right > 0.5f) {
            vibrate()
            svc.scrollUp()
            lastAction = now
            return
        }

        if (left < 0.25f && right < 0.25f) {
            vibrate()
            svc.goBack()
            lastAction = now
            return
        }

        if (pitch < -10) {
            vibrate()
            svc.goHome()
            lastAction = now
            return
        }

        if (yaw > 20) {
            vibrate()
            svc.switchApps()
            lastAction = now
            return
        }
    }

    private fun vibrate() {
        val vib = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) {
            vib.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vib.vibrate(150)
        }
    }
}
