package android.widget

import android.view.ViewGroup

/** Views stacked on each other, each placed by its gravity. */
open class FrameLayout(context: android.content.Context? = null) : ViewGroup(context) {
    open class LayoutParams(width: Int, height: Int, @JvmField var gravity: Int = -1) : ViewGroup.LayoutParams(width, height)
}
