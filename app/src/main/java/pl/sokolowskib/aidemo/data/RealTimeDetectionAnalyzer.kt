package pl.sokolowskib.aidemo.data

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import pl.sokolowskib.aidemo.utils.YuvToRgbConverter
import pl.sokolowskib.aidemo.utils.rotate

class RealTimeDetectionAnalyzer(
    val detectFun: (Bitmap, Boolean) -> Unit, private val converter: YuvToRgbConverter
) : ImageAnalysis.Analyzer {

    private var lastAnalyzedTimestamp = 0L
    private var isObjectIdentifierRunning = false

    @SuppressLint("UnsafeExperimentalUsageError")
    override fun analyze(image: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        if (!isObjectIdentifierRunning && currentTimestamp - lastAnalyzedTimestamp >= 50) {
            var bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            converter.yuvToRgb(image.image!!, bitmap)
            bitmap = bitmap.rotate(image.imageInfo.rotationDegrees)

            GlobalScope.launch(Dispatchers.Main) { detectFun(bitmap, false) }

            lastAnalyzedTimestamp = currentTimestamp
        }
        image.close()
    }

    fun setIdentifierRunning(newValue: Boolean) {
        isObjectIdentifierRunning = newValue
    }
}