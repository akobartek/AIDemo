package pl.sokolowskib.aidemo.data

import pl.sokolowskib.aidemo.ml.Identification
import pl.sokolowskib.aidemo.utils.dot
import pl.sokolowskib.aidemo.utils.normalize
import java.io.InputStream

object IdentificationOutputDecoder {

    const val IDENTIFICATION_INPUT_NET_SIZE = 256

    private var embeddingsObjects = arrayListOf<IdentificationObject>()

    fun setIdentificationResults(
        outputs: Identification.Outputs,
        detectionResult: DetectionResult,
        embeddingsIS: InputStream
    ) {
        if (embeddingsObjects.isEmpty()) embeddingsObjects = getIdentificationObjects(embeddingsIS)

        var bestIdentificationObject: IdentificationObject? = null
        var bestIdentificationDotProductValue = 0.0

        val outputVector = outputs.outputFeature0AsTensorBuffer.floatArray.normalize()
        embeddingsObjects.forEach {
            val dotProduct = outputVector.dot(it.embeddings)
            if (dotProduct > bestIdentificationDotProductValue) {
                bestIdentificationDotProductValue = dotProduct
                bestIdentificationObject = it
            }
        }
        detectionResult.identification = bestIdentificationObject
    }

    private fun getIdentificationObjects(embeddingsIS: InputStream): ArrayList<IdentificationObject> {
        val buffer = ByteArray(embeddingsIS.available())
        embeddingsIS.read(buffer)
        val stringArray = String(buffer)
            .replace("\"", "")
            .replace("\n", " ")
            .replace("  ", " ")
            .split("[[", "]]")

        val embeddings = ArrayList<IdentificationObject>()
        for (i in 0 until stringArray.size - 1 step 2)
            embeddings.add(
                IdentificationObject(
                    stringArray[i].split(",").toTypedArray(),
                    stringArray[i + 1].split(" ").filter { it != "" }
                        .map { it.trim().toFloat() }.toTypedArray()
                )
            )
        return embeddings
    }
}