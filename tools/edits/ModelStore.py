# Windows: the phone's .tflite models are used as ONNX files (same name, .onnx), downloaded by WinData.
EDITS = [
    ('''    fun local(context: Context, remote: String): File = when {
''', '''    fun local(context: Context, remote: String): File = when {
        remote.endsWith(".tflite") -> ModelStore.file(context, remote.removeSuffix(".tflite") + ".onnx")
'''),
    ('''    fun file(context: Context, name: String) = ModelStore.file(context, name)
    fun has(context: Context, name: String): Boolean = complete(context, name)''', '''    fun file(context: Context, name: String) = local(context, name)
    fun has(context: Context, name: String): Boolean = complete(context, name)'''),
    ('''        val f = local(context, remote)
        val want = FILES[remote] ?: return f.exists() && f.length() > 0''', '''        val f = local(context, remote)
        if (remote.endsWith(".tflite")) return f.exists() && f.length() > 1000 && !File(f.path + ".part").exists()
        val want = FILES[remote] ?: return f.exists() && f.length() > 0'''),
]
