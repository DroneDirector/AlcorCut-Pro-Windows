package com.google.ai.edge.litert

/**
 * The phone's AI runtime. On Windows these models are not loaded yet (the AI tools that use them
 * say so and the rest of the editor works); ONNX models run through ONNX Runtime.
 */
enum class Accelerator { CPU, GPU, NPU }

class TensorBuffer : AutoCloseable {
    fun writeFloat(data: FloatArray) {}
    fun readFloat(): FloatArray = FloatArray(0)
    override fun close() {}
}

class CompiledModel private constructor() : AutoCloseable {
    class Options(vararg val accelerators: Accelerator)
    fun createInputBuffers(): List<TensorBuffer> = listOf(TensorBuffer())
    fun createOutputBuffers(): List<TensorBuffer> = listOf(TensorBuffer())
    fun run(inputs: List<TensorBuffer>, outputs: List<TensorBuffer>) {}
    override fun close() {}

    companion object {
        fun create(path: String, options: Options, env: Any?): CompiledModel = throw UnsupportedOperationException("This AI model isn't available on Windows yet")
    }
}
