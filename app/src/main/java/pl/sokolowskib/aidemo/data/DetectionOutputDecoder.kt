package pl.sokolowskib.aidemo.data

import android.graphics.RectF
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import pl.sokolowskib.aidemo.ml.Tfmodel
import java.util.*
import kotlin.math.*

object DetectionOutputDecoder {

    const val DETECTION_INPUT_NET_SIZE = 416

    //    private const val NUM_CLASSES = 1
    private const val NUM_BOXES_PER_BLOCK = 3
    private const val DETECTION_THRESHOLD = 0.5   // 0.5 or 0.8
    private const val OVERLAP_THRESHOLD = 0.3

    private val ANCHORS =
        intArrayOf(60, 51, 73, 26, 79, 89, 35, 56, 40, 129, 46, 23, 19, 46, 24, 94, 34, 40)

    fun getDetectionResults(outputs: Tfmodel.Outputs): ArrayList<DetectionResult> {
        val resultsQueue: PriorityQueue<DetectionResult> = PriorityQueue(1) { lhs, rhs ->
            (rhs as DetectionResult).confidence.compareTo((lhs as DetectionResult).confidence)
        }
        resultsQueue.addAll(decodeOutput(outputs.outputFeature0AsTensorBuffer, 0))
        resultsQueue.addAll(decodeOutput(outputs.outputFeature1AsTensorBuffer, 1))
        resultsQueue.addAll(decodeOutput(outputs.outputFeature2AsTensorBuffer, 2))
        return nms(resultsQueue)
    }

    private fun decodeOutput(output: TensorBuffer, number: Int): ArrayList<DetectionResult> {
        val gridHeight = output.shape[1]
        val gridWidth = output.shape[2]
        val blockSize = output.shape[3]
        val outputArray = output.floatArray

        val results = arrayListOf<DetectionResult>()
        for (i in 0 until gridHeight * gridHeight) {
            val y = floor(i.toFloat().div(gridWidth)).toInt()
            val x = i.rem(gridWidth)
            for (b in 0 until NUM_BOXES_PER_BLOCK) {
                val offset =
                    (gridWidth * NUM_BOXES_PER_BLOCK * y + NUM_BOXES_PER_BLOCK * x + b) * (blockSize / NUM_BOXES_PER_BLOCK)

                val confidence = sigmoid(outputArray[offset + 4])
                if (confidence <= DETECTION_THRESHOLD)
                    continue

                // Getting BoundingBox
                val xPos = (x + sigmoid(outputArray[offset + 0])) / gridWidth
                val yPos = (y + sigmoid(outputArray[offset + 1])) / gridHeight
                val anchor1 = ANCHORS[2 * b + 0 + 6 * number]
                val w =
                    (anchor1 * exp(outputArray[offset + 2].toDouble())).toFloat() / DETECTION_INPUT_NET_SIZE
                val anchor2 = ANCHORS[2 * b + 1 + 6 * number]
                val h =
                    (anchor2 * exp(outputArray[offset + 3].toDouble())).toFloat() / DETECTION_INPUT_NET_SIZE

                val rect = RectF(
                    xPos - w / 2, yPos - h / 2, xPos + w / 2, yPos + h / 2
                )

//                    var detectedClass = -1
//                    var maxClass = 0f
//
//                    val classes = FloatArray(NUM_CLASSES)
//                    for (c in 0 until NUM_CLASSES)
//                        classes[c] = outputArray[offset + 5 + c]
//                    softmax(classes)
//
//                    for (c in 0 until NUM_CLASSES) {
//                        if (classes[c] > maxClass) {
//                            detectedClass = c
//                            maxClass = classes[c]
//                        }
//                    }
//                    val confidenceInClass = maxClass * confidence

                results.add(DetectionResult(rect, confidence, outputArray[offset + 5].toInt()))
            }
        }
        return results
    }

    private fun nms(resultsQueue: PriorityQueue<DetectionResult>): ArrayList<DetectionResult> {
        val resultsList = arrayListOf<DetectionResult>()
        if (resultsQueue.size > 0) {
            val bestRecognition = resultsQueue.poll()!!
            resultsList.add(bestRecognition)
            while (true) {
                val recognition = resultsQueue.poll() ?: break
                var overlaps = false
                resultsList.forEach { previousRecognition ->
                    overlaps = overlaps ||
                            iou(previousRecognition.rectF, recognition.rectF) > OVERLAP_THRESHOLD
                }
                if (!overlaps)
                    resultsList.add(recognition)
            }
        }
        return resultsList
    }

    private fun iou(box1: RectF, box2: RectF): Float {
        val x1 = min(box1.left, box2.left)
        val y1 = min(box1.top, box2.top)
        val x2 = max(box1.right, box2.right)
        val y2 = max(box1.bottom, box2.bottom)
        // TODO() -> +1 in the python code fragment
        val intersection = abs((x2 - x1) * (y2 - y1))
        val area1 = (box1.right - box1.left) * (box1.bottom - box1.top)
        val area2 = (box2.right - box2.left) * (box2.bottom - box2.top)
        return intersection / (area1 + area2 - intersection)
    }

    private fun sigmoid(x: Float): Float {
        return 1.0f / (1.0f + exp(-x))
    }

//    private fun getArray(number: Int, assets: AssetManager): Array<Double> {
//        val inputStream = assets.open("predictions$number.txt")
//        val size = inputStream.available()
//        val buffer = ByteArray(size)
//        inputStream.read(buffer)
//        val string = String(buffer)
//        return string.split(", ").map { it.toDouble() }.toTypedArray()
//    }
}