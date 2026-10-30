# Windows: a phone Typeface becomes a desktop font family through the app's own helper.
EDITS = [
    ('private val condensed = FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL))',
     'private val condensed = com.dronedirector.veyrocutpro.editor.composeFamilyOf(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL))'),
]
