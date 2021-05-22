package fr.thomasciles.camerax

import android.Manifest
import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.animation.ScaleAnimation
import android.view.animation.TranslateAnimation
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.transition.Fade
import androidx.transition.TransitionManager
import com.bumptech.glide.Glide
import fr.thomasciles.camerax.databinding.ActivityMainBinding
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraProvider: ProcessCameraProvider
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private var inBurstMode = false
    private var flashMode = ImageCapture.FLASH_MODE_OFF

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            setupView()
            Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Camera permission needed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasFrontCamera() = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
    private fun hasBackCamera() = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestPermission.launch(Manifest.permission.CAMERA)
        
        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupView() {
        binding.apply {

            linearLayoutFlashOptions.isVisible = false

            cameraCaptureButton.setOnClickListener {
                takePhoto()
            }

            switchCamera.setOnClickListener {
                if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA && hasFrontCamera()) {
                    cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                } else if (hasBackCamera()) {
                    cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                }

                startCamera()
            }

            cameraCaptureButton.setOnTouchListener { v, event ->
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        binding.cameraCaptureButton.setBackgroundResource(R.drawable.ic_shutter_pressed)

                        if (!inBurstMode) {
                            inBurstMode = true
                            setInterval(500) {
                                takePhoto()
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        inBurstMode = false
                        binding.cameraCaptureButton.setBackgroundResource(R.drawable.ic_shutter)
                        v.performClick()
                    }
                }

                true
            }
        }

        showLastImageInGallery()

        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera()
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            imageCapture = ImageCapture
                .Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setFlashMode(flashMode)
                .build()

            try {
                cameraProvider.unbindAll()
                val camera: Camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                setupFlash(camera)
                setupGestureControls(camera)
            } catch (e: Exception) {

            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestureControls(camera: Camera) {
        val onTapGestureListener = object: GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent?): Boolean {
                return true
            }
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val factory: MeteringPointFactory = DisplayOrientedMeteringPointFactory(
                    binding.previewView.display,
                    camera.cameraInfo,
                    binding.previewView.width.toFloat(),
                    binding.previewView.height.toFloat()
                )

                val action: FocusMeteringAction = FocusMeteringAction.Builder(
                    factory.createPoint(e.x, e.y)
                ).build()

                camera.cameraControl.startFocusAndMetering(action)

                return true
            }
        }

        val tapGestureDetector = GestureDetector(this, onTapGestureListener)

        binding.previewView.setOnTouchListener { _, e: MotionEvent ->
            val tapEventProcessed = tapGestureDetector.onTouchEvent(e)
            tapEventProcessed
        }
    }

    private fun setupFlash(camera: Camera) {
        if (!camera.cameraInfo.hasFlashUnit()) {
            binding.apply {
                linearLayoutFlashOptions.isVisible = false
                imageViewCurrentFlash.setOnClickListener {}
                imageViewCurrentFlash.setImageResource(R.drawable.ic_baseline_no_flash_24)
            }
        } else {
            binding.apply {
                var icon = when (flashMode) {
                    ImageCapture.FLASH_MODE_AUTO -> R.drawable.ic_baseline_flash_auto_24
                    ImageCapture.FLASH_MODE_ON -> R.drawable.ic_baseline_flash_on_24
                    else -> R.drawable.ic_baseline_flash_off_24
                }

                imageViewCurrentFlash.setImageResource(icon)
                imageViewCurrentFlash.setOnClickListener {
                    val animate = Fade()
                    animate.duration = 500
                    animate.addTarget(linearLayoutFlashOptions)

                    TransitionManager.beginDelayedTransition(linearLayoutFlashOptions, animate)
                    linearLayoutFlashOptions.isVisible = !linearLayoutFlashOptions.isVisible
                    llTools.isVisible = !linearLayoutFlashOptions.isVisible
                }

                imageViewFlashOn.setOnClickListener {
                    flashMode = ImageCapture.FLASH_MODE_ON
                    camera.cameraControl.enableTorch(true)
                    imageViewCurrentFlash.setImageResource(R.drawable.ic_baseline_flash_on_24)
                    linearLayoutFlashOptions.isVisible = false
                    llTools.isVisible = true
                }

                imageViewFlashOff.setOnClickListener {
                    flashMode = ImageCapture.FLASH_MODE_OFF
                    camera.cameraControl.enableTorch(false)
                    imageViewCurrentFlash.setImageResource(R.drawable.ic_baseline_flash_off_24)
                    linearLayoutFlashOptions.isVisible = false
                    llTools.isVisible = true
                }

                imageViewFlashAuto.setOnClickListener {
                    flashMode = ImageCapture.FLASH_MODE_AUTO
                    imageViewCurrentFlash.setImageResource(R.drawable.ic_baseline_flash_auto_24)
                    linearLayoutFlashOptions.isVisible = false
                    llTools.isVisible = true
                    startCamera()
                }
            }
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile = getPhotoFile()

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object: ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(baseContext, "Photo capture error: ${exception.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)

                    if (!inBurstMode) {
                        showLastImageInGallery()
                    }
                }
            })
    }


    private fun showLastImageInGallery() {
        val outputDir = getOutputDirectory()
        val files = outputDir.listFiles()?.filter { it.canRead() && it.isFile && it.name.endsWith(".jpg") } ?: return

        files.maxByOrNull { it.lastModified() }?.let {
            val uri = Uri.fromFile(it)


            binding.imageViewGallery.post {
                binding.imageViewGallery.setPadding(resources.getDimension(R.dimen.gallery_rounded_button_padding).toInt())

                Glide
                    .with(this)
                    .load(uri)
                    .circleCrop()
                    .into(binding.imageViewGallery)
            }


        }
    }


    private fun setInterval(timeMillis: Long, handler: () -> Unit) = GlobalScope.launch {
        while (inBurstMode) {
            delay(timeMillis)

            if (inBurstMode) {
                handler()
            }
        }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }

        return if (mediaDir != null && mediaDir.exists()) {
            mediaDir
        } else {
            filesDir
        }
    }

    private fun getFilename(): String = SimpleDateFormat(FILENAME_FORMAT, Locale.FRANCE)
        .format(System.currentTimeMillis()) + ".jpg"

    private fun getPhotoFile(): File = File(getOutputDirectory(), getFilename())

    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}