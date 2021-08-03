package pl.sokolowskib.aidemo.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.Image
import android.view.View
import android.widget.Toast
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import pl.sokolowskib.aidemo.data.DetectionOutputDecoder.DETECTION_INPUT_NET_SIZE
import pl.sokolowskib.aidemo.data.IdentificationOutputDecoder.IDENTIFICATION_INPUT_NET_SIZE
import pl.sokolowskib.aidemo.viewmodel.PhotoViewModel
import kotlin.math.sqrt

fun Context.showShortToast(messageId: Int) =
    Toast.makeText(this, messageId, Toast.LENGTH_SHORT).show()

fun View.rotate(rotation: Float) {
    this.animate()
        .rotation(rotation)
        .duration = 200
}

fun Image.toBitmap(rotation: Float): Bitmap {
    val buffer = planes[0].buffer
    buffer.rewind()
    val bytes = ByteArray(buffer.capacity())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size).rotate(rotation)
}

infix fun FloatArray.dot(other: Array<Float>) =
    foldIndexed(0.0) { i, acc, cur -> acc + cur * other[i] }

fun FloatArray.normalize(): FloatArray {
    val l2 = sqrt(this.map { it * it }.sum())
    val s = 1f / l2
    return this.map { it * s }.toFloatArray()
}

fun Bitmap.rotate(degrees: Number): Bitmap {
    return Bitmap.createBitmap(
        this, 0, 0, width, height, Matrix().apply { postRotate(degrees.toFloat()) }, true
    )
}

fun Bitmap.processBeforeDetection(viewModel: PhotoViewModel, isDetectionCaptured: Boolean): Bitmap {
    var newHeight = height
    var newWidth = width
    val oldValue: Int
    val scale: Float
    val heightChanged =
        if ((DETECTION_INPUT_NET_SIZE.toFloat() / newWidth) < (DETECTION_INPUT_NET_SIZE.toFloat() / newHeight)) {
            scale = DETECTION_INPUT_NET_SIZE.toFloat() / newWidth
            oldValue = newHeight
            newHeight = newWidth
            true
        } else {
            scale = DETECTION_INPUT_NET_SIZE.toFloat() / newHeight
            oldValue = newWidth
            newWidth = newHeight
            false
        }

    // CREATE NEW BITMAP AND FILL WITH BLACK COLOR TO FIT NEW WIDTH AND HEIGHT AND PLACE BITMAP IN THE TOP LEFT CORNER
    val oldPixels = IntArray(width * height)
    getPixels(oldPixels, 0, width, 0, 0, width, height)
    val newBitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
    val newPixels = arrayListOf<Int>()
    repeat(newHeight) { height ->
        repeat(newWidth) { width ->
            newPixels.add(
                when {
                    heightChanged && height < oldValue -> oldPixels[width + (height * newWidth)]
                    !heightChanged && width < oldValue -> oldPixels[width + (height * oldValue)]
                    else -> Color.BLACK
                }
            )
        }
    }
    newBitmap.setPixels(newPixels.toIntArray(), 0, newWidth, 0, 0, newWidth, newHeight)

    if (isDetectionCaptured) viewModel.photoScale.postValue(scale)
    else viewModel.realTimeScale.postValue(scale)
    return newBitmap
}

fun Bitmap.processBeforeIdentification(): Bitmap {
    var newHeight = height
    var newWidth = width
    val oldValue: Int
    val heightChanged =
        if ((IDENTIFICATION_INPUT_NET_SIZE.toFloat() / newWidth) < (IDENTIFICATION_INPUT_NET_SIZE.toFloat() / newHeight)) {
            oldValue = newHeight
            newHeight = newWidth
            true
        } else {
            oldValue = newWidth
            newWidth = newHeight
            false
        }

    // CREATE NEW BITMAP AND FILL WITH BLACK COLOR TO FIT NEW WIDTH AND HEIGHT AND PLACE BITMAP IN THE CENTER
    val oldPixels = IntArray(width * height)
    getPixels(oldPixels, 0, width, 0, 0, width, height)
    val newPixels = arrayListOf<Int>()
    val blackPixelsValue = (newHeight - oldValue) / 2
    repeat(newHeight) { height ->
        repeat(newWidth) { width ->
            newPixels.add(
                when (if (heightChanged) height else width) {
                    in (0 until blackPixelsValue) -> Color.BLACK
                    in (blackPixelsValue until blackPixelsValue + oldValue) -> {
                        val currentWidth = if (heightChanged) width else width - blackPixelsValue
                        val currentHeight = if (heightChanged) height - blackPixelsValue else height
                        oldPixels[currentWidth + (currentHeight * if (heightChanged) newWidth else oldValue)].getChangedColor()
                    }
                    else -> Color.BLACK
                }
            )
        }
    }
    val newBitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
    newBitmap.setPixels(newPixels.toIntArray(), 0, newWidth, 0, 0, newWidth, newHeight)
    return newBitmap
}

private fun Int.getChangedColor(): Int = Color.rgb(blue, green, red)