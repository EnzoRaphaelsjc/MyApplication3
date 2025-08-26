
package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.common.PointF3D
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sqrt

class PrincipalActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var poseDetector: com.google.mlkit.vision.pose.PoseDetector
    private lateinit var resultTextView: TextView
    private lateinit var cameraPreview: PreviewView
    private lateinit var flipCameraButton: ImageButton

    private var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_principal)

        resultTextView = findViewById(R.id.result_textview)
        cameraPreview = findViewById(R.id.camera_preview)
        flipCameraButton = findViewById(R.id.flip_camera_button)

        cameraExecutor = Executors.newSingleThreadExecutor()
        setupPoseDetector()

        // Lógica do botão para virar a câmera
        flipCameraButton.setOnClickListener {
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
            startCamera()
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun setupPoseDetector() {
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
        poseDetector = PoseDetection.getClient(options)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(cameraPreview.surfaceProvider)
            }

            // A câmera usada agora é controlada pela variável cameraSelector
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(imageProxy)
                    }
                }
            try {

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e(TAG, "Falha ao vincular a câmera", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }



    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    @OptIn(ExperimentalGetImage::class)
    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            poseDetector.process(image)
                .addOnSuccessListener { pose ->
                    analyzePose(pose)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Detecção de pose falhou", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    private fun analyzePose(pose: Pose) {
        var translatedText = "Nenhum sinal conhecido detectado"

        if (isSayingHello(pose)) {
            translatedText = "Olá"
        } else if (isPointingToSelf(pose)) {
            translatedText = "Eu"
        }

        runOnUiThread {
            resultTextView.text = translatedText
        }
    }

    private fun isSayingHello(pose: Pose): Boolean {
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        if (nose == null) return false
        if (leftWrist != null && leftWrist.position.y < nose.position.y) return true
        if (rightWrist != null && rightWrist.position.y < nose.position.y) return true
        return false
    }

    private fun isPointingToSelf(pose: Pose): Boolean {
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftIndex = pose.getPoseLandmark(PoseLandmark.LEFT_INDEX)
        val rightIndex = pose.getPoseLandmark(PoseLandmark.RIGHT_INDEX)
        if (leftShoulder == null || rightShoulder == null) return false
        val chestCenter = PointF3D.from(
            (leftShoulder.position.x + rightShoulder.position.x) / 2,
            (leftShoulder.position.y + rightShoulder.position.y) / 2,
            0f
        )
        if (leftIndex != null && getDistance(leftIndex.position, chestCenter) < 100) return true
        if (rightIndex != null && getDistance(rightIndex.position, chestCenter) < 100) return true
        return false
    }

    private fun getDistance(p1: PointF, p2: PointF3D): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy).toFloat()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissões não concedidas.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        poseDetector.close()
    }

    companion object {
        private const val TAG = "PrincipalActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}