package com.example.mosquitokiller

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var viewFinder: PreviewView
    private lateinit var processedImageView: ImageView
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var flashButton: ImageButton
    private lateinit var focusRing: View

    private var camera: Camera? = null
    private var isFlashOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)
        processedImageView = findViewById(R.id.processedImageView)
        flashButton = findViewById(R.id.flashButton)
        focusRing = findViewById(R.id.focusRing)
        imageProcessor = ImageProcessor(applicationContext)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        flashButton.setOnClickListener { toggleFlash() }
        setupTouchControls()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { image ->
                        val rotationDegrees = image.imageInfo.rotationDegrees
                        val processedBitmap = imageProcessor.processImage(image, rotationDegrees)
                        runOnUiThread {
                            processedImageView.setImageBitmap(processedBitmap)
                        }
                        image.close()
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                // Handle exceptions
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupTouchControls() {
        val scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val zoomState = camera?.cameraInfo?.zoomState?.value ?: return true
                val currentZoomRatio = zoomState.zoomRatio
                val newZoomRatio = currentZoomRatio * detector.scaleFactor
                camera?.cameraControl?.setZoomRatio(newZoomRatio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio))
                return true
            }
        })

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                showFocusRing(e.x, e.y)
                val factory = viewFinder.meteringPointFactory
                val point = factory.createPoint(e.x, e.y)
                val action = FocusMeteringAction.Builder(point).build()
                camera?.cameraControl?.startFocusAndMetering(action)
                return true
            }
        })

        viewFinder.setOnTouchListener { _, event ->
            val didConsumeScale = scaleGestureDetector.onTouchEvent(event)
            val didConsumeTap = gestureDetector.onTouchEvent(event)
            return@setOnTouchListener didConsumeScale || didConsumeTap
        }
    }

    private fun showFocusRing(x: Float, y: Float) {
        focusRing.x = x - focusRing.width / 2
        focusRing.y = y - focusRing.height / 2
        focusRing.visibility = View.VISIBLE
        focusRing.alpha = 1f

        focusRing.animate()
            .scaleX(1.3f)
            .scaleY(1.3f)
            .setDuration(250)
            .withEndAction {
                focusRing.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(250)
                    .withEndAction {
                        focusRing.animate()
                            .alpha(0f)
                            .setDuration(500)
                            .setListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    focusRing.visibility = View.GONE
                                }
                            })
                            .start()
                    }
                    .start()
            }
            .start()
    }

    private fun toggleFlash() {
        camera?.let {
            if (it.cameraInfo.hasFlashUnit()) {
                isFlashOn = !isFlashOn
                it.cameraControl.enableTorch(isFlashOn)
                if (isFlashOn) {
                    flashButton.setImageResource(R.drawable.ic_flash_on)
                } else {
                    flashButton.setImageResource(R.drawable.ic_flash_off)
                }
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}