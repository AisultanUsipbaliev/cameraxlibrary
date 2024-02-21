package com.avrora.telecom.cameraxlibrary;

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.PermissionChecker
import androidx.recyclerview.widget.LinearLayoutManager
import com.avrora.telecom.cameraxlibrary.databinding.ActivityMainBinding
import com.avrora.telecom.cameraxlibrary.VideoThumbnailAdapter
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale

typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity(), VideoThumbnailAdapter.OnThumbnailUpdateListener {
    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        )
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && it.value == false)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(
                    baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                startCamera()
            }
        }

    private lateinit var viewBinding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private lateinit var cameraExecutor: ExecutorService

    private var cameraControl: CameraControl? = null
    private var cameraInfo: CameraInfo? = null

    // Select back camera as a default
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private var isTorchOn = false

    private var recordingStartTime = 0L
    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            val elapsedMillis = System.currentTimeMillis() - recordingStartTime
            updateRecordingTime(elapsedMillis)
            updateHandler.postDelayed(this, 1000) // Обновляем каждую секунду
        }
    }

    private val mediaFiles = mutableListOf<MediaFile>()

    private lateinit var videoThumbnailAdapter: VideoThumbnailAdapter

    private lateinit var scaleGestureDetector: ScaleGestureDetector

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoomRatio = cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                val delta = detector.scaleFactor
                cameraControl?.setZoomRatio(currentZoomRatio * delta)
                return true
            }
        })

        // Обрабатываем касания к ViewFinder для реализации зума
        viewBinding.viewFinder.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            return@setOnTouchListener true
        }

        initializeRecyclerView()

        // Set up the listeners
        viewBinding.closeButton.setOnClickListener { onBackPressed() }
        viewBinding.toggleModeButton.setOnClickListener { toggleMode() }
        viewBinding.switchCameraButton.setOnClickListener { switchCamera() }
        viewBinding.toggleFlashButton.setOnClickListener { toggleFlash() }

        viewBinding.captureButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    when (viewBinding.captureButton.currentState) {
                        CameraButtonView.State.DEFAULT -> {
                            viewBinding.captureButton.animatePressDown()
                            takePhoto()
                        }

                        CameraButtonView.State.RECORDING -> {
                            captureVideo()
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (viewBinding.captureButton.currentState == CameraButtonView.State.DEFAULT) {
                        viewBinding.captureButton.animatePressUp()
                    }
                    true
                }

                else -> false
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun initializeRecyclerView() {
        videoThumbnailAdapter = VideoThumbnailAdapter(this, mediaFiles, this)

        viewBinding.thumbnailsRecyclerView.layoutManager = LinearLayoutManager(
            this, LinearLayoutManager.HORIZONTAL, false
        )
        viewBinding.thumbnailsRecyclerView.adapter = videoThumbnailAdapter
        viewBinding.thumbnailsRecyclerView.addItemDecoration(
            HorizontalSpaceItemDecoration(
                resources.getDimensionPixelSize(
                    R.dimen.horizontal_space_width
                )
            )
        )
    }

    private fun updateMediaCounterButtonText() {
        val mediaCount = mediaFiles.count { it.isChecked }
        val mediaString = resources.getQuantityString(R.plurals.media_count, mediaCount, mediaCount)

        viewBinding.mediaCounterButton.text = mediaString
    }

    private fun updateRecordingTime(elapsedMillis: Long) {
        // Форматируем elapsedMillis в читаемый формат, например, "MM:SS"
        val formattedTime = formatMillisecondsToTime(elapsedMillis)

        // Обновляем интерфейс пользователя, например, текстовое поле с таймером
        viewBinding.videoDurationText.text = formattedTime
    }

    override fun onBackPressed() {
        if (recording != null) {
            captureVideo()
        }

        val returnIntent = Intent()
        returnIntent.putParcelableArrayListExtra("mediaFiles", ArrayList(mediaFiles))
        setResult(Activity.RESULT_OK, returnIntent)

        super.onBackPressed()
    }

    private fun toggleFlash() {
        val cameraControl = cameraControl ?: return

        isTorchOn = !isTorchOn

        cameraControl.enableTorch(isTorchOn)

        viewBinding.toggleFlashButton.setImageResource(if (isTorchOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off)
    }

    private fun switchCamera() {
        if (recording != null) {
            captureVideo()
        }

        when (cameraSelector) {
            CameraSelector.DEFAULT_BACK_CAMERA -> {
                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                viewBinding.switchCameraButton.setImageResource(R.drawable.ic_camera_swap_front)
            }

            CameraSelector.DEFAULT_FRONT_CAMERA -> {
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                viewBinding.switchCameraButton.setImageResource(R.drawable.ic_camera_swap)
            }
        }

        startCamera()
    }

    private fun toggleMode() {
        if (viewBinding.captureButton.currentState == CameraButtonView.State.DEFAULT) {
            viewBinding.toggleModeButton.setImageResource(R.drawable.ic_camera)
            viewBinding.captureButton.currentState = CameraButtonView.State.RECORDING
        } else {
            if (recording != null) {
                captureVideo()
            }
            viewBinding.toggleModeButton.setImageResource(R.drawable.ic_video)
            viewBinding.captureButton.currentState = CameraButtonView.State.DEFAULT
        }
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${output.savedUri}"
//                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)

                    val savedUri = output.savedUri ?: return

                    val absolutePath = getRealPathFromUri(baseContext, savedUri) ?: "UnknownPath"

                    val rotationAngle = getRotationAngle(baseContext, savedUri)
                    val rotatedThumbnailBitmap = createRotatedImageThumbnail(baseContext, savedUri, rotationAngle, 192, 192)

                    mediaFiles.add(
                        MediaFile(
                            absolutePath,
                            name,
                            true,
                            1,
                            "",
                            rotatedThumbnailBitmap
                        )
                    )

                    Log.d(TAG, mediaFiles.toString())

                    val newPosition = mediaFiles.size - 1
                    viewBinding.thumbnailsRecyclerView.adapter?.notifyItemInserted(newPosition)
                    viewBinding.thumbnailsRecyclerView.scrollToPosition(newPosition)

                    updateMediaCounterButtonText()
                }
            }
        )
    }

    private fun getRotationAngle(context: Context, imageUri: Uri): Int {
        context.contentResolver.openInputStream(imageUri).use { inputStream ->
            val exifInterface = ExifInterface(inputStream!!)
            val orientation = exifInterface.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            return when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }
    }

    private fun createRotatedImageThumbnail(context: Context, imageUri: Uri, rotationAngle: Int, width: Int, height: Int): Bitmap? {
        var bitmap: Bitmap? = null

        // Загрузка Bitmap из Uri с автоматическим закрытием потока ввода
        context.contentResolver.openInputStream(imageUri).use { inputStream ->
            bitmap = BitmapFactory.decodeStream(inputStream)
        } // inputStream автоматически закрывается здесь

        if (bitmap == null) {
            return null // Если изображение не удалось загрузить, возвращаем null
        }

        // Поворачиваем Bitmap, если это необходимо
        val rotatedBitmap = if (rotationAngle != 0) {
            val matrix = Matrix().apply { postRotate(rotationAngle.toFloat()) }
            Bitmap.createBitmap(bitmap!!, 0, 0, bitmap!!.width, bitmap!!.height, matrix, true).also {
                // Если создается новый Bitmap, исходный Bitmap больше не нужен
                if (it != bitmap) bitmap?.recycle()
            }
        } else {
            bitmap
        }

        // Создаем миниатюру из повёрнутого Bitmap
        return rotatedBitmap?.let { ThumbnailUtils.extractThumbnail(it, width, height) }
    }

//    private fun saveThumbnailToMediaStore(context: Context, thumbnail: Bitmap, displayName: String, path: String): Uri? {
//        val contentValues = ContentValues().apply {
//            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
//            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
//            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
//                put(MediaStore.Images.Media.RELATIVE_PATH, path)
//            }
//        }
//
//        val uri = context.contentResolver.insert(
//            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
//            contentValues
//        )
//        uri?.let {
//            context.contentResolver.openOutputStream(it).use { outputStream ->
//                // Сжимаем и записываем миниатюру в MediaStore
//                if (outputStream != null) {
//                    thumbnail.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
//                }
//            }
//        }
//
//        return uri
//    }

    private fun getRealPathFromUri(context: Context, contentUri: Uri): String? {
        var cursor: Cursor? = null
        try {
            val proj = arrayOf(MediaStore.Images.Media.DATA)
            cursor = context.contentResolver.query(contentUri, proj, null, null, null)
            val columnIndex = cursor?.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            cursor?.moveToFirst()
            if (cursor != null) {
                return columnIndex?.let { cursor.getString(it) }
            }
            return null
        } finally {
            cursor?.close()
        }
    }

    // Implements VideoCapture use case, including start and stop capturing.
    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        viewBinding.captureButton.isEnabled = false

        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            viewBinding.captureButton.animateStopVideo()

            updateHandler.removeCallbacks(updateRunnable)
            viewBinding.videoDurationText.visibility = View.GONE

            curRecording.stop()
            recording = null
            return
        }

        // create and start a new recording session
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) ==
                    PermissionChecker.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        recordingStartTime = System.currentTimeMillis()
                        updateHandler.post(updateRunnable)
                        viewBinding.videoDurationText.visibility = View.VISIBLE

                        viewBinding.captureButton.apply {
                            animateStartVideo()
                            isEnabled = true
                        }
                    }

                    is VideoRecordEvent.Finalize -> {
                        val text = viewBinding.videoDurationText.text.toString()

                        updateHandler.removeCallbacks(updateRunnable)
                        viewBinding.videoDurationText.visibility = View.GONE

                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
//                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
//                                .show()
                            Log.d(TAG, msg)

                            val videoUri = recordEvent.outputResults.outputUri
                            val absolutePath = getRealPathFromUri(baseContext, videoUri) ?: "UnknownPath"

                            val thumbnailBitmap = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                getVideoThumbnail(absolutePath)
                            } else {
                                getVideoThumbnailFromUri(this@MainActivity, videoUri)
                            }

                            mediaFiles.add(
                                MediaFile(
                                    absolutePath,
                                    name,
                                    true,
                                    2,
                                    "",
                                    thumbnailBitmap,
                                    text
                                )
                            )

                            Log.d(TAG, mediaFiles.toString())

                            val newPosition = mediaFiles.size - 1
                            viewBinding.thumbnailsRecyclerView.adapter?.notifyItemInserted(newPosition)
                            viewBinding.thumbnailsRecyclerView.scrollToPosition(newPosition)

                            updateMediaCounterButtonText()
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(
                                TAG, "Video capture ends with error: " +
                                        "${recordEvent.error}"
                            )
                        }
                        viewBinding.captureButton.apply {
                            animateStopVideo()
                            isEnabled = true
                        }
                    }
                }
            }
    }

    private fun formatMillisecondsToTime(milliseconds: Long): String {
        val elapsedSeconds = milliseconds / 1000
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun getVideoThumbnail(filePath: String): Bitmap? {
        return ThumbnailUtils.createVideoThumbnail(filePath, MediaStore.Images.Thumbnails.MICRO_KIND)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getVideoThumbnailFromUri(context: Context, videoUri: Uri): Bitmap? {
        val thumbnail = context.contentResolver.loadThumbnail(videoUri, Size(192, 192), null)
        return thumbnail
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.HIGHEST,
                        FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                    )
                )
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            imageCapture = ImageCapture.Builder().build()

            /*
            val imageAnalyzer = ImageAnalysis.Builder().build()
                .also {
                    setAnalyzer(
                        cameraExecutor,
                        LuminosityAnalyzer { luma ->
                            Log.d(TAG, "Average luminosity: $luma")
                        }
                    )
                }
            */

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                val camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, videoCapture
                )

                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo

                cameraControl?.enableTorch(isTorchOn)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    override fun onUpdateThumbnails() {
        updateMediaCounterButtonText()
    }
}