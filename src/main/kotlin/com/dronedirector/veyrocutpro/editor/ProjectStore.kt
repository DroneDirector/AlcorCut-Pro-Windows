package com.dronedirector.veyrocutpro.editor

import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer

/** Saved projects (Windows). */
object ProjectStore {
    internal val gson: Gson = GsonBuilder()
        .registerTypeHierarchyAdapter(Uri::class.java, JsonSerializer<Uri> { src, _, _ -> JsonPrimitive(src.toString()) })
        .registerTypeHierarchyAdapter(Uri::class.java, JsonDeserializer { json, _, _ -> Uri.parse(json.asString) })
        .create()
}
