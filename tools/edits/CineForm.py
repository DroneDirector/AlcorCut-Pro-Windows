# Windows: the output file is opened by the CineForm library itself; the exact frame rate (23.976 / 29.97 / 59.94).
EDITS = [
    ('handle = nOpen(pfd.fd,', 'handle = nOpen(com.dronedirector.veyrocutpro.windows.CfFiles.fd(pfd),'),
    ('        try { pfd.close() } catch (_: Throwable) {}\n        return if (keep && closed) {',
     '        com.dronedirector.veyrocutpro.windows.CfFiles.close(pfd)\n        try { pfd.close() } catch (_: Throwable) {}\n        return if (keep && closed) {'),
    ('val f = try { vt.format.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble() } catch (_: Throwable) { 0.0 }',
     'val f = try { vt.format.getFloat("frame-rate-exact", vt.format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()).toDouble() } catch (_: Throwable) { 0.0 }'),
    ('                        val p = img.planes\n', '                        val p = com.dronedirector.veyrocutpro.windows.Bt709.planes(img)\n'),
]
