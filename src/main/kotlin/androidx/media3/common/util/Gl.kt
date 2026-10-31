package androidx.media3.common.util

import org.lwjgl.opengles.GLES20 as G

/** Media3's GL helpers on the app's graphics engine (call on the GL thread). Own code. */
object GlUtil {
    class GlException(message: String) : Exception(message)
    const val HOMOGENEOUS_COORDINATE_VECTOR_SIZE = 4
    @JvmStatic fun getNormalizedCoordinateBounds() = floatArrayOf(-1f, -1f, 0f, 1f, 1f, -1f, 0f, 1f, -1f, 1f, 0f, 1f, 1f, 1f, 0f, 1f)
    @JvmStatic fun getTextureCoordinateBounds() = floatArrayOf(0f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 1f)
    @JvmStatic fun create4x4IdentityMatrix() = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }
    @JvmStatic fun checkGlError() { val e = G.glGetError(); if (e != 0) throw GlException("GL error 0x%04X".format(e)) }
    @JvmStatic fun createTexture(w: Int, h: Int, useHighPrecisionColorComponents: Boolean): Int {
        val t = G.glGenTextures()
        G.glBindTexture(G.GL_TEXTURE_2D, t)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MIN_FILTER, G.GL_LINEAR); G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MAG_FILTER, G.GL_LINEAR)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_S, G.GL_CLAMP_TO_EDGE); G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_T, G.GL_CLAMP_TO_EDGE)
        G.glTexImage2D(G.GL_TEXTURE_2D, 0, G.GL_RGBA, w, h, 0, G.GL_RGBA, G.GL_UNSIGNED_BYTE, null as java.nio.ByteBuffer?)
        checkGlError()
        return t
    }
    @JvmStatic fun createFboForTexture(tex: Int): Int {
        val f = G.glGenFramebuffers()
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, f)
        G.glFramebufferTexture2D(G.GL_FRAMEBUFFER, G.GL_COLOR_ATTACHMENT0, G.GL_TEXTURE_2D, tex, 0)
        checkGlError()
        return f
    }
    @JvmStatic fun focusFramebufferUsingCurrentContext(fbo: Int, w: Int, h: Int) { G.glBindFramebuffer(G.GL_FRAMEBUFFER, fbo); G.glViewport(0, 0, w, h) }
    @JvmStatic fun clearFocusedBuffers() { G.glClearColor(0f, 0f, 0f, 0f); G.glClear(G.GL_COLOR_BUFFER_BIT) }
    @JvmStatic fun deleteTexture(t: Int) { if (t != 0) G.glDeleteTextures(t) }
    @JvmStatic fun deleteFbo(f: Int) { if (f != 0) G.glDeleteFramebuffers(f) }
    @JvmStatic fun bindTexture(target: Int, t: Int, filter: Int = G.GL_LINEAR) {
        G.glBindTexture(target, t)
        G.glTexParameteri(target, G.GL_TEXTURE_MIN_FILTER, filter); G.glTexParameteri(target, G.GL_TEXTURE_MAG_FILTER, filter)
        G.glTexParameteri(target, G.GL_TEXTURE_WRAP_S, G.GL_CLAMP_TO_EDGE); G.glTexParameteri(target, G.GL_TEXTURE_WRAP_T, G.GL_CLAMP_TO_EDGE)
    }
}

/** A vertex + fragment shader pair with named attributes and uniforms (media3's GlProgram). */
class GlProgram(vertexShaderGlsl: String, fragmentShaderGlsl: String) {
    val programId: Int
    private class Attr(val loc: Int) { var data: java.nio.FloatBuffer? = null; var size = 0 }
    private class Uni(val loc: Int, val type: Int) { val f = FloatArray(16); var n = 0; var tex = 0; var unit = 0; var ints = IntArray(4) }
    private val attrs = HashMap<String, Attr>()
    private val unis = HashMap<String, Uni>()

    init {
        fun compile(type: Int, src: String): Int {
            val s = G.glCreateShader(type)
            G.glShaderSource(s, src); G.glCompileShader(s)
            if (G.glGetShaderi(s, G.GL_COMPILE_STATUS) == 0) { val log = G.glGetShaderInfoLog(s); G.glDeleteShader(s); throw GlUtil.GlException("Shader didn't compile: $log") }
            return s
        }
        val vs = compile(G.GL_VERTEX_SHADER, vertexShaderGlsl); val fs = compile(G.GL_FRAGMENT_SHADER, fragmentShaderGlsl)
        programId = G.glCreateProgram()
        G.glAttachShader(programId, vs); G.glAttachShader(programId, fs); G.glLinkProgram(programId)
        G.glDeleteShader(vs); G.glDeleteShader(fs)
        if (G.glGetProgrami(programId, G.GL_LINK_STATUS) == 0) throw GlUtil.GlException("Shaders didn't link: " + G.glGetProgramInfoLog(programId))
        org.lwjgl.system.MemoryStack.stackPush().use { st ->
            val size = st.mallocInt(1); val type = st.mallocInt(1)
            for (i in 0 until G.glGetProgrami(programId, G.GL_ACTIVE_ATTRIBUTES)) {
                val n = G.glGetActiveAttrib(programId, i, size, type)
                attrs[n] = Attr(G.glGetAttribLocation(programId, n))
            }
            for (i in 0 until G.glGetProgrami(programId, G.GL_ACTIVE_UNIFORMS)) {
                var n = G.glGetActiveUniform(programId, i, size, type)
                if (n.endsWith("[0]")) n = n.dropLast(3)
                unis[n] = Uni(G.glGetUniformLocation(programId, n), type[0])
            }
        }
    }

    fun use() = G.glUseProgram(programId)
    fun delete() = G.glDeleteProgram(programId)
    fun getAttributeArrayLocationAndEnable(name: String): Int { val a = attrs[name] ?: return -1; G.glEnableVertexAttribArray(a.loc); return a.loc }
    fun getUniformLocation(name: String): Int = G.glGetUniformLocation(programId, name)

    fun setBufferAttribute(name: String, values: FloatArray, size: Int) {
        val a = attrs[name] ?: throw NullPointerException("No attribute $name")
        a.data = java.nio.ByteBuffer.allocateDirect(values.size * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().put(values).also { it.flip() }
        a.size = size
    }
    private fun uni(name: String) = unis[name] ?: throw NullPointerException("No uniform $name")
    fun setSamplerTexIdUniform(name: String, texId: Int, texUnitIndex: Int) { val u = uni(name); u.tex = texId; u.unit = texUnitIndex }
    fun setExternalTexturesRequireNearestSampling(b: Boolean) {}
    fun setIntUniform(name: String, v: Int) { val u = uni(name); u.ints[0] = v; u.n = 1 }
    fun setIntsUniform(name: String, v: IntArray) { val u = uni(name); u.ints = v.copyOf(); u.n = v.size }
    fun setFloatUniform(name: String, v: Float) { val u = uni(name); u.f[0] = v; u.n = 1 }
    fun setFloatsUniform(name: String, v: FloatArray) { val u = uni(name); System.arraycopy(v, 0, u.f, 0, minOf(16, v.size)); u.n = v.size; if (v.size > 16) big[name] = v.copyOf() }
    private val big = HashMap<String, FloatArray>()

    fun bindAttributesAndUniforms() {
        for (a in attrs.values) { val d = a.data ?: continue; G.glEnableVertexAttribArray(a.loc); G.glVertexAttribPointer(a.loc, a.size, G.GL_FLOAT, false, 0, d) }
        for ((name, u) in unis) {
            when (u.type) {
                G.GL_SAMPLER_2D, 0x8B66 /* external */ -> { G.glActiveTexture(G.GL_TEXTURE0 + u.unit); GlUtil.bindTexture(G.GL_TEXTURE_2D, u.tex); G.glUniform1i(u.loc, u.unit) }
                G.GL_FLOAT -> if (u.n > 1) G.glUniform1fv(u.loc, big[name] ?: u.f.copyOf(u.n)) else G.glUniform1f(u.loc, u.f[0])
                G.GL_FLOAT_VEC2 -> G.glUniform2fv(u.loc, big[name] ?: u.f.copyOf(maxOf(2, u.n)))
                G.GL_FLOAT_VEC3 -> G.glUniform3fv(u.loc, big[name] ?: u.f.copyOf(maxOf(3, u.n)))
                G.GL_FLOAT_VEC4 -> G.glUniform4fv(u.loc, big[name] ?: u.f.copyOf(maxOf(4, u.n)))
                G.GL_FLOAT_MAT3 -> G.glUniformMatrix3fv(u.loc, false, u.f.copyOf(9))
                G.GL_FLOAT_MAT4 -> G.glUniformMatrix4fv(u.loc, false, u.f.copyOf(16))
                G.GL_INT, G.GL_BOOL -> G.glUniform1i(u.loc, u.ints[0])
                G.GL_INT_VEC2 -> G.glUniform2iv(u.loc, u.ints.copyOf(2))
            }
        }
    }
}
