package pl.sokolowskib.aidemo.data

data class IdentificationObject(
    private val array: Array<String>,
    val embeddings: Array<Float>
) {
    val number: Int = array[0].trim().toInt()
    val path: String = array[1]
    val companyName: String = array[2]
    val width: Int = array[3].trim().toInt()
    val height: Int = array[4].trim().toInt()
    val productName: String = setProductName()
    val productType: String = array[array.size - 4]
    val ean: String = array[array.size - 3]
    val companyLabel: String = array[array.size - 2]

    private fun setProductName(): String {
        var name = ""
        for (i in 5 until array.size - 4) {
            name += array[i]
            if (i < array.size - 5) name += ", "
        }
        return name
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IdentificationObject
        if (!array.contentEquals(other.array)) return false
        if (!embeddings.contentEquals(other.embeddings)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = array.contentHashCode()
        result = 31 * result + embeddings.contentHashCode()
        return result
    }
}