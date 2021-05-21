package fr.thomasciles.camerax

import android.Manifest
import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
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

            imageCapture = ImageCapture.Builder().build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {

            }
        }, ContextCompat.getMainExecutor(this))
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