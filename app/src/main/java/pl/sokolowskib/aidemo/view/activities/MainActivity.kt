package pl.sokolowskib.aidemo.view.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.actionbar_camera.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_detection_results.*
import kotlinx.android.synthetic.main.content_detection_results.view.*
import kotlinx.android.synthetic.main.content_photo.*
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import pl.sokolowskib.aidemo.R
import pl.sokolowskib.aidemo.data.DetectionResult
import pl.sokolowskib.aidemo.data.DetectionOutputDecoder
import pl.sokolowskib.aidemo.data.DetectionOutputDecoder.DETECTION_INPUT_NET_SIZE
import pl.sokolowskib.aidemo.data.IdentificationOutputDecoder
import pl.sokolowskib.aidemo.data.IdentificationOutputDecoder.IDENTIFICATION_INPUT_NET_SIZE
import pl.sokolowskib.aidemo.data.RealTimeDetectionAnalyzer
import pl.sokolowskib.aidemo.ml.Identification
import pl.sokolowskib.aidemo.ml.Tfmodel
import pl.sokolowskib.aidemo.utils.*
import pl.sokolowskib.aidemo.viewmodel.PhotoViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private var mDisplayId: Int = -1
    private var mPreview: Preview? = null
    private var mImageCapture: ImageCapture? = null
    private var mCamera: Camera? = null
    private var mOrientationEventListener: OrientationEventListener? = null
    private var mRotation = 0f
    private var mRealTimeDetectionAnalyzer: RealTimeDetectionAnalyzer? = null
    private val mYuvToRgbConverter by lazy { YuvToRgbConverter(this@MainActivity) }
    private var mAspectRatio = 0

    private val detectionImageProcessor by lazy {
        ImageProcessor.Builder()
            .add(
                ResizeOp(
                    DETECTION_INPUT_NET_SIZE,
                    DETECTION_INPUT_NET_SIZE,
                    ResizeOp.ResizeMethod.BILINEAR
                )
            )
            .add(Rot90Op(mRotation.toInt() / 90))
            .add(NormalizeOp(0f, 255f))
            .build()
    }

    private lateinit var mPhotoViewModel: PhotoViewModel
    private lateinit var mCameraExecutor: ExecutorService
    private var mLoadingDialog: AlertDialog? = null
    private lateinit var identificationTfModel: Identification
    private lateinit var detectionTfModel: Tfmodel

    private val mDisplayManager by lazy {
        getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    private val mDisplayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == this@MainActivity.mDisplayId) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
                    mImageCapture?.targetRotation = display!!.rotation
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mPhotoViewModel = ViewModelProvider(this@MainActivity).get(PhotoViewModel::class.java)
        mCameraExecutor = Executors.newSingleThreadExecutor()
        mDisplayManager.registerDisplayListener(mDisplayListener, null)
        mLoadingDialog = AlertDialog.Builder(this@MainActivity)
            .setView(R.layout.dialog_loading)
            .setCancelable(false)
            .create()
        identificationTfModel = Identification.newInstance(this@MainActivity)
        detectionTfModel = Tfmodel.newInstance(this@MainActivity)

        if (mPhotoViewModel.latestPhoto.value == null || mPhotoViewModel.detectionResults.value == null)
            photoThumbnail.visibility = View.GONE

        viewFinder.post {
            mDisplayId = viewFinder.display.displayId
        }

        detectObjectBtn.setOnClickListener {
            Log.d("TIME START", System.currentTimeMillis().toString())
            mRealTimeDetectionAnalyzer?.setIdentifierRunning(true)
            mLoadingDialog?.show()
            mImageCapture?.takePicture(
                mCameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
                    @SuppressLint("UnsafeExperimentalUsageError")
                    override fun onCaptureSuccess(image: ImageProxy) {
                        Log.d("TIME IMAGE CAPTURED", System.currentTimeMillis().toString())
                        val rotation = image.imageInfo.rotationDegrees - mRotation
                        var bitmap =
                            Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                        if (!mYuvToRgbConverter.yuvToRgb(image.image!!, bitmap))
                            bitmap = image.image?.toBitmap(rotation)

//                        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.photo2)
                        if (bitmap == null) {
                            showShortToast(R.string.image_capture_error)
                            return
                        }
                        mPhotoViewModel.latestPhoto.postValue(bitmap)
                        Log.d("TIME BITMAP PROCESSING", System.currentTimeMillis().toString())
                        mPhotoViewModel.latestResizedPhoto.postValue(
                            bitmap.processBeforeDetection(mPhotoViewModel, true)
                        )
                        Log.d("TIME BITMAP PROCESSED", System.currentTimeMillis().toString())
                        image.close()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (mLoadingDialog?.isShowing == true) mLoadingDialog?.hide()
                        showShortToast(R.string.image_capture_error)
                    }
                })
        }
        photoThumbnail.setOnClickListener { showDetectionResultsLayout() }
        flashBtn.setOnClickListener {
            mImageCapture?.flashMode =
                if (it.isSelected) ImageCapture.FLASH_MODE_OFF else ImageCapture.FLASH_MODE_ON
            it.isSelected = !it.isSelected
        }
        closeBtn.setOnClickListener { onBackPressed() }
        hideResultsBtn.setOnClickListener { onBackPressed() }

        mPhotoViewModel.latestPhoto.observe(this@MainActivity, {
            if (it != null) {
                photoThumbnail.visibility = View.VISIBLE
                Glide.with(this@MainActivity)
                    .load(it)
                    .circleCrop()
                    .into(photoThumbnail)
                resultPhoto.setImageBitmap(it)
            } else
                photoThumbnail.visibility = View.GONE
        })
        mPhotoViewModel.latestResizedPhoto.observe(this@MainActivity, { resizedBitmap ->
            detectObjects(resizedBitmap, true)
        })
        mPhotoViewModel.detectionResults.observe(this@MainActivity, {
            Log.d("TIME OBJECTS DETECTED", System.currentTimeMillis().toString())
            identifyObjects(it)
            Log.d("TIME OBJECTS IDENTIFIED", System.currentTimeMillis().toString())
            Log.d("TIME END", System.currentTimeMillis().toString())
            mLoadingDialog?.hide()
        })

        identificationDrawView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val resultClicked = mPhotoViewModel.detectionResults.value?.firstOrNull {
                    it.rectF.contains(event.x, event.y)
                }
                resultClicked?.let {
                    v.performClick()
                    AlertDialog.Builder(this@MainActivity)
                        .setMessage("${it.identification?.companyName} - ${it.identification?.productName}\nean: ${it.identification?.ean}")
                        .show()
                    true
                }
                false
            } else false
        }
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) viewFinder.post { bindCameraUseCases() }
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        mOrientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if ((orientation < 35 || orientation > 325) && mRotation != 0f) {
                    mRotation = 0f
                    rotateUI(mRotation)
                } else if (orientation in 146..214 && mRotation != 180f) {
                    mRotation = 180f
                    rotateUI(mRotation)
                } else if (orientation in 56..124 && mRotation != 270f) {
                    mRotation = 270f
                    rotateUI(mRotation)
                } else if (orientation in 236..304 && mRotation != 90f) {
                    mRotation = 90f
                    rotateUI(mRotation)
                }
            }
        }
        mOrientationEventListener?.enable()
        mRealTimeDetectionAnalyzer?.setIdentifierRunning(false)
    }

    override fun onPause() {
        super.onPause()
        if (mLoadingDialog?.isShowing == true) {
            mLoadingDialog?.dismiss()
            mLoadingDialog = null
        }
        mOrientationEventListener?.disable()
    }

    override fun onDestroy() {
        super.onDestroy()
        mCameraExecutor.shutdown()
        mDisplayManager.unregisterDisplayListener(mDisplayListener)
        // Releases model resources if no longer used.
        detectionTfModel.close()
        identificationTfModel.close()
    }

    override fun onBackPressed() {
        when {
            identificationResultsLayout.visibility == View.VISIBLE -> {
                identificationResultsLayout.visibility = View.GONE
                cameraLayout.visibility = View.VISIBLE
                cameraActionBarLayout.visibility = View.VISIBLE
                mRealTimeDetectionAnalyzer?.setIdentifierRunning(false)
            }
            mPhotoViewModel.latestPhoto.value == null -> super.onBackPressed()
            else -> mPhotoViewModel.latestPhoto.postValue(null)
        }
    }

    private fun rotateUI(rotation: Float) {
        photoThumbnail.rotate(rotation)
    }

    private fun detectObjects(bitmap: Bitmap?, isDetectionCaptured: Boolean) {
        scaleIV.setImageBitmap(if (isDetectionCaptured) mPhotoViewModel.latestPhoto.value else bitmap)

        val bitmapToProcess =
            if (isDetectionCaptured) bitmap
            else bitmap!!.processBeforeDetection(mPhotoViewModel, false)

        val tfImageBuffer = TensorImage(DataType.FLOAT32)
        val tfImage =
            detectionImageProcessor.process(tfImageBuffer.apply { load(bitmapToProcess) })
        val outputs = detectionTfModel.process(tfImage.tensorBuffer)
        Log.d("TIME OUTPUTS GOT", System.currentTimeMillis().toString())
        val detectionResults = DetectionOutputDecoder.getDetectionResults(outputs)

        scaleResultsRectangles(detectionResults, isDetectionCaptured)
        if (isDetectionCaptured) mPhotoViewModel.detectionResults.value = detectionResults
        scaleRectanglesToDisplay(detectionResults, isDetectionCaptured)
        if (isDetectionCaptured) showDetectionResultsLayout()
    }

    private fun identifyObjects(detectionResults: ArrayList<DetectionResult>) {
        detectionResults.forEach {
            val rect = it.rectF
            var objWidth = abs(rect.right - rect.left).toInt()
            var objHeight = abs(rect.bottom - rect.top).toInt()
            val latestPhotoBitmap = mPhotoViewModel.latestPhoto.value!!
            if (latestPhotoBitmap.width < rect.left.toInt() + objWidth)
                objWidth = latestPhotoBitmap.width - rect.left.toInt()
            if (latestPhotoBitmap.height < rect.top.toInt() + objHeight)
                objHeight = latestPhotoBitmap.height - rect.top.toInt()
            val bitmap = Bitmap.createBitmap(
                latestPhotoBitmap, rect.left.toInt(), rect.top.toInt(), objWidth, objHeight
            ).processBeforeIdentification()
            val imageProcessor = ImageProcessor.Builder()
                .add(
                    ResizeOp(
                        IDENTIFICATION_INPUT_NET_SIZE,
                        IDENTIFICATION_INPUT_NET_SIZE,
                        ResizeOp.ResizeMethod.BILINEAR
                    )
                )
                .add(Rot90Op(mRotation.toInt() / 90))
                .add(NormalizeOp(0f, 128f))
                .add(MinusXOp(1))
                .build()
            val tfImage =
                imageProcessor.process(TensorImage(DataType.FLOAT32).apply { load(bitmap) })
            val outputs = identificationTfModel.process(tfImage.tensorBuffer)
            IdentificationOutputDecoder.setIdentificationResults(
                outputs, it, assets.open("embeddings.txt")
            )
        }
    }

    @SuppressLint("SetTextI18n")
    private fun scaleResultsRectangles(
        detectionResults: ArrayList<DetectionResult>, isDetectionCaptured: Boolean
    ) {
        val photoScale =
            if (isDetectionCaptured) mPhotoViewModel.photoScale.value ?: 1f
            else mPhotoViewModel.realTimeScale.value ?: 1f
        val scale = DETECTION_INPUT_NET_SIZE / photoScale
        scaleRectangles(detectionResults, scale, Float.MAX_VALUE, Float.MAX_VALUE)
    }

    @SuppressLint("RestrictedApi", "NewApi")
    private fun scaleRectanglesToDisplay(
        detectionResults: ArrayList<DetectionResult>, isDetectionCaptured: Boolean
    ) {
        val displayMetrics = DisplayMetrics()
        display?.getRealMetrics(displayMetrics)
        val displayScale = displayMetrics.widthPixels.toFloat() / scaleIV.drawable.intrinsicWidth
        scaleRectangles(
            detectionResults,
            displayScale,
            displayMetrics.widthPixels.toFloat() - 1,
            displayMetrics.heightPixels.toFloat() - 1
        )
        if (isDetectionCaptured) identificationDrawView.setTargets(detectionResults)
        else detectionDrawView.setTargets(detectionResults)
    }

    private fun scaleRectangles(
        results: ArrayList<DetectionResult>, scale: Float, maxValueX: Float, maxValueY: Float
    ) {
        results.forEach { result ->
            result.rectF.left =
                if (result.rectF.left > 0) min(result.rectF.left * scale, maxValueX) else 0f
            result.rectF.right =
                if (result.rectF.right > 0) min(result.rectF.right * scale, maxValueX) else 0f
            result.rectF.top =
                if (result.rectF.top > 0) min(result.rectF.top * scale, maxValueY) else 0f
            result.rectF.bottom =
                if (result.rectF.bottom > 0) min(result.rectF.bottom * scale, maxValueY) else 0f
        }
    }

    private fun showDetectionResultsLayout() {
        identificationResultsLayout.visibility = View.VISIBLE
        cameraLayout.visibility = View.GONE
        cameraActionBarLayout.visibility = View.GONE
    }

    private fun bindCameraUseCases() {
        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        mAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        val rotation = viewFinder.display.rotation

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this@MainActivity)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            mPreview = Preview.Builder()
                .setTargetAspectRatio(mAspectRatio)
                .setTargetRotation(rotation)
                .build()

            mImageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetAspectRatio(mAspectRatio)
                .setTargetRotation(rotation)
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(mAspectRatio)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            mRealTimeDetectionAnalyzer =
                RealTimeDetectionAnalyzer(::detectObjects, mYuvToRgbConverter)
            imageAnalysis.setAnalyzer(mCameraExecutor, mRealTimeDetectionAnalyzer!!)

            // Must unbind the use-cases before rebinding them
            cameraProvider.unbindAll()

            try {
                mCamera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, mPreview, mImageCapture, imageAnalysis
                )
                mPreview?.setSurfaceProvider(viewFinder.createSurfaceProvider())
            } catch (exc: Exception) {
                Log.e("MainActivity", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this@MainActivity))
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        return if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) AspectRatio.RATIO_4_3
        else AspectRatio.RATIO_16_9
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                viewFinder.post { bindCameraUseCases() }
            } else {
                AlertDialog.Builder(this)
                    .setTitle(R.string.permission_error_title)
                    .setMessage(R.string.permission_error_msg)
                    .setCancelable(false)
                    .setPositiveButton(R.string.allow) { dialog, _ ->
                        dialog.dismiss()
                        ActivityCompat.requestPermissions(
                            this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
                        )
                    }
                    .setNegativeButton(R.string.cancel) { dialog, _ ->
                        dialog.dismiss()
                        finish()
                    }
                    .create()
                    .show()
            }
        }
    }

    private fun allPermissionsGranted(): Boolean {
        for (permission in REQUIRED_PERMISSIONS)
            if (ContextCompat
                    .checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
            ) return false
        return true
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 42
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }
}