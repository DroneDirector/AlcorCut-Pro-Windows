# Windows wording (PC instead of phone).
EDITS = [
    ('val codec = try { MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS) } catch (t: Throwable) { pcm.delete(); return "This phone has no Opus encoder" }',
     'val codec = try { MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS) } catch (t: Throwable) { pcm.delete(); return "This PC has no Opus encoder" }'),
]
