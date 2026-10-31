# Windows: the phone's native libraries are DLLs in the app's own folder.
EDITS = [
    ('System.loadLibrary(', 'com.dronedirector.veyrocutpro.windows.NativeLibs.load('),
]
