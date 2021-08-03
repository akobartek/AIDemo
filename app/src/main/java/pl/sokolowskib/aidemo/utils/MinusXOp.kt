package pl.sokolowskib.aidemo.utils

import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.TensorOperator
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.support.tensorbuffer.TensorBufferFloat

class MinusXOp(private var x: Int) : TensorOperator {

    override fun apply(input: TensorBuffer): TensorBuffer {
        val shape = input.shape
        val values = input.floatArray

        for (i in values.indices) {
            values[i] = values[i] - x
        }

        val output: TensorBuffer = if (input.isDynamic) {
            TensorBufferFloat.createDynamic(DataType.FLOAT32)
        } else {
            TensorBufferFloat.createFixedSize(shape, DataType.FLOAT32)
        }

        output.loadArray(values, shape)
        return output
    }
}