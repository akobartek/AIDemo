package pl.sokolowskib.aidemo.data

import android.graphics.RectF

data class DetectionResult(
    val rectF: RectF,
    val confidence: Float,
    val detectedClass: Int,
    var identification: IdentificationObject? = null
)
