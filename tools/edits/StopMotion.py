# Windows: the stop-motion video is made by windows/CaptureParts.kt (no Media3 on a PC).
EDITS = [
    ('import androidx.media3.common.MediaItem\n', ''),
    ('import androidx.media3.common.MimeTypes\n', ''),
    ('import androidx.media3.common.util.UnstableApi\n', ''),
    ('import androidx.media3.transformer.Composition\n', ''),
    ('import androidx.media3.transformer.EditedMediaItem\n', ''),
    ('import androidx.media3.transformer.EditedMediaItemSequence\n', ''),
    ('import androidx.media3.transformer.ExportException\n', ''),
    ('import androidx.media3.transformer.ExportResult\n', ''),
    ('import androidx.media3.transformer.Transformer\n', ''),
    ('@androidx.annotation.OptIn(UnstableApi::class)\n', ''),
    ('@UnstableApi\n', ''),
]
