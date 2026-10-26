package android.graphics

/** GPU picture effects written in the phone's shader language (used for video previews). */
class RuntimeShader(val source: String) : Shader() {
    fun setFloatUniform(name: String, vararg v: Float) {}
    fun setIntUniform(name: String, vararg v: Int) {}
    fun setInputShader(name: String, s: Shader) {}
    fun setColorUniform(name: String, c: Int) {}
}
