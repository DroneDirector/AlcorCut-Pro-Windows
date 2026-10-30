# Windows: no Media3 on a PC (video grading comes with the video editor).
EDITS = [
    ('import androidx.annotation.OptIn\n', ''),
    ('import androidx.media3.common.util.UnstableApi\n', ''),
    ('@OptIn(UnstableApi::class)\n', ''),
]
