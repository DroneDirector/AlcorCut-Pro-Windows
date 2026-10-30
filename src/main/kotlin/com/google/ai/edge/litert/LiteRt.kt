package com.google.ai.edge.litert

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer

/**
 * The phone's AI runtime on Windows: the same models, converted to ONNX (see the "AI models for
 * Windows" workflow), run with ONNX Runtime. Tensors keep the phone's layout. Own code.
 */
enum class Accelerator { CPU, GPU, NPU }

class TensorBuffer internal constructor(internal val shape: LongArray) : AutoCloseable {
    internal var data: FloatArray = FloatArray(0)
    fun writeFloat(d: FloatArray) { data = d }
    fun readFloat(): FloatArray = data
    override fun close() {}
}

class CompiledModel private constructor(private val session: OrtSession) : AutoCloseable {
    class Options(vararg val accelerators: Accelerator)

    private val inNames = session.inputNames.toList()
    private val outNames = session.outputNames.toList()
    private fun shapeOf(info: ai.onnxruntime.NodeInfo): LongArray =
        ((info.info as? ai.onnxruntime.TensorInfo)?.shape ?: longArrayOf()).map { if (it > 0) it else 1L }.toLongArray()

    fun createInputBuffers(): List<TensorBuffer> = inNames.map { TensorBuffer(shapeOf(session.inputInfo[it]!!)) }
    fun createOutputBuffers(): List<TensorBuffer> = outNames.map { TensorBuffer(shapeOf(session.outputInfo[it]!!)) }

    fun run(inputs: List<TensorBuffer>, outputs: List<TensorBuffer>) {
        val env = OrtEnvironment.getEnvironment()
        val feeds = LinkedHashMap<String, OnnxTensor>()
        try {
            inNames.forEachIndexed { i, n -> feeds[n] = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputs[i].data), inputs[i].shape) }
            session.run(feeds).use { r ->
                outNames.forEachIndexed { i, n ->
                    val t = r.get(n).orElse(null) as? OnnxTensor ?: return@forEachIndexed
                    val fb = t.floatBuffer
                    val arr = FloatArray(fb.remaining()); fb.get(arr)
                    if (i < outputs.size) outputs[i].data = arr
                }
            }
        } finally { feeds.values.forEach { it.close() } }
    }

    override fun close() { try { session.close() } catch (_: Throwable) {} }

    companion object {
        /** [path] names the phone's .tflite file; the ONNX version next to it is used. */
        fun create(path: String, options: Options, env: Any?): CompiledModel {
            val f = File(path).let { if (it.name.endsWith(".tflite")) File(it.parentFile, it.name.removeSuffix(".tflite") + ".onnx") else it }
            if (!f.exists()) throw IllegalStateException("The AI model ${f.name} hasn't downloaded yet")
            val e = OrtEnvironment.getEnvironment()
            val o = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 16))
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            return CompiledModel(e.createSession(f.path, o))
        }
    }
}
