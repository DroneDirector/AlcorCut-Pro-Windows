# Fonts for the picker: the bundled font file as a desktop font family.
EDITS = [
    ('fontFamily = tf?.let { androidx.compose.ui.text.font.FontFamily(it) }', 'fontFamily = tf?.composeFamily()'),
]
